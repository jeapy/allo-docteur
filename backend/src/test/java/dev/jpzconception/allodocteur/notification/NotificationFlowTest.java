package dev.jpzconception.allodocteur.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.ClockTestConfig;
import dev.jpzconception.allodocteur.Fixtures;
import dev.jpzconception.allodocteur.Fixtures.Booking;
import dev.jpzconception.allodocteur.MutableClock;
import dev.jpzconception.allodocteur.notification.application.AppointmentReminderJob;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/** Notifications in-app déclenchées par les événements métier, lecture, rappels de rendez-vous. */
@Import(ClockTestConfig.class)
class NotificationFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired AppointmentReminderJob reminders;

    ApiClient api;
    Fixtures fx;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private JsonNode notes(Session who) throws Exception {
        return api.body(api.get("/api/v1/notifications", who.token())).get("content");
    }

    private int unread(Session who) throws Exception {
        return api.body(api.get("/api/v1/notifications/unread-count", who.token())).get("count").asInt();
    }

    @Test
    void appointmentEventsNotifyTheRightPersonAndNotificationsCanBeRead() throws Exception {
        Session pro = fx.approvedProfessional("Notif" + UUID.randomUUID().toString().substring(0, 6));
        Session patient = fx.patient("Notifie");

        // l'approbation a déjà prévenu le professionnel
        assertThat(notes(pro).findValuesAsText("type")).contains("PROFESSIONAL");

        Booking b = fx.book(patient, pro, "TELECONSULTATION_VIDEO", 0);
        JsonNode proNotes = notes(pro);
        assertThat(proNotes.findValuesAsText("type")).contains("APPOINTMENT");
        assertThat(notes(patient).size()).isZero(); // rien pour le patient avant la confirmation

        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);
        JsonNode patientNotes = notes(patient);
        assertThat(patientNotes.size()).isEqualTo(1);
        assertThat(patientNotes.get(0).get("title").asText()).isEqualTo("Rendez-vous confirmé");
        assertThat(patientNotes.get(0).get("refId").asText()).isEqualTo(b.id());
        assertThat(unread(patient)).isEqualTo(1);

        // un tiers ne voit rien et ne peut pas marquer la notification d'autrui
        Session other = fx.patient("Tiers");
        assertThat(notes(other).size()).isZero();
        String noteId = patientNotes.get(0).get("id").asText();
        assertThat(status(api.post("/api/v1/notifications/" + noteId + "/read", Map.of(), other.token()))).isEqualTo(404);

        assertThat(status(api.post("/api/v1/notifications/" + noteId + "/read", Map.of(), patient.token()))).isEqualTo(200);
        assertThat(unread(patient)).isZero();
        assertThat(api.body(api.get("/api/v1/notifications", patient.token(), "unread", "true")).get("content").size()).isZero();

        // annuler prévient l'autre partie
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/cancel", Map.of("reason", "Imprévu"), pro.token())))
                .isEqualTo(200);
        assertThat(unread(patient)).isEqualTo(1);
        assertThat(status(api.post("/api/v1/notifications/read-all", Map.of(), patient.token()))).isEqualTo(200);
        assertThat(unread(patient)).isZero();

        assertThat(status(api.get("/api/v1/notifications", api.login("admin@test.local", "AdminPassw0rd!").token()))).isEqualTo(403);
    }

    @Test
    void remindersAreSentOnceForUpcomingConfirmedAppointments() throws Exception {
        Session pro = fx.approvedProfessional("Rappel" + UUID.randomUUID().toString().substring(0, 6));
        Session patient = fx.patient("Rappele");
        Booking b = fx.book(patient, pro, "TELECONSULTATION_VIDEO", 0);

        // non confirmé : aucun rappel
        clock.travelTo(b.startAt().minusSeconds(30 * 60));
        assertThat(reminders.run()).isZero();

        clock.reset();
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);

        // trop tôt : le rendez-vous est dans 3 jours
        assertThat(reminders.run()).isZero();

        clock.travelTo(b.startAt().minusSeconds(30 * 60));
        assertThat(reminders.run()).isEqualTo(1);
        assertThat(reminders.run()).isZero(); // idempotent

        Session p = api.login(patient.email(), ApiClient.PASSWORD);
        Session q = api.login(pro.email(), ApiClient.PASSWORD);
        assertThat(notes(p).findValuesAsText("type")).contains("APPOINTMENT_REMINDER");
        assertThat(notes(q).findValuesAsText("type")).contains("APPOINTMENT_REMINDER");
    }
}
