package dev.jpzconception.allodocteur.review;

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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/** Avis : seulement après une consultation terminée, un par rendez-vous, modération et recalcul de la note. */
@Import(ClockTestConfig.class)
class ReviewFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;

    ApiClient api;
    Fixtures fx;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private record Done(Session pro, Session patient, String appointmentId) {}

    private Booking booking(Session patient, Session pro) throws Exception {
        Booking b = fx.book(patient, pro, "TELECONSULTATION_VIDEO", 0);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);
        return b;
    }

    /** Mène un rendez-vous jusqu'à COMPLETED et rend des sessions valides à l'heure déplacée. */
    private Done completed(Session pro, Session patient) throws Exception {
        Booking b = booking(patient, pro);
        clock.travelTo(b.startAt().minusSeconds(60));
        Session p = api.login(pro.email(), ApiClient.PASSWORD);
        String base = "/api/v1/appointments/" + b.id();
        assertThat(status(api.post(base + "/start", Map.of(), p.token()))).isEqualTo(200);
        assertThat(status(api.post(base + "/complete", Map.of(), p.token()))).isEqualTo(200);
        return new Done(p, api.login(patient.email(), ApiClient.PASSWORD), b.id());
    }

    private JsonNode professional(Session viewer, Session pro) throws Exception {
        return api.body(api.get("/api/v1/professionals/" + pro.userId(), viewer.token()));
    }

    @Test
    void reviewRequiresACompletedConsultationAndIsUniquePerAppointment() throws Exception {
        Session pro = fx.approvedProfessional("Note" + UUID.randomUUID().toString().substring(0, 6));
        Session patient = fx.patient("Avis");
        Booking pending = booking(patient, pro);

        MvcResult tooEarly = api.post("/api/v1/appointments/" + pending.id() + "/review",
                Map.of("rating", 5, "comment", "Top"), patient.token());
        assertThat(status(tooEarly)).isEqualTo(409);
        assertThat(api.body(tooEarly).get("code").asText()).isEqualTo("REVIEW_NOT_ALLOWED");

        // autre patient : le premier a déjà un rendez-vous au même créneau (chevauchement refusé)
        Done d = completed(fx.approvedProfessional("Fini" + UUID.randomUUID().toString().substring(0, 6)), fx.patient("Avis2"));
        String url = "/api/v1/appointments/" + d.appointmentId() + "/review";

        assertThat(status(api.post(url, Map.of("rating", 0), d.patient().token()))).isEqualTo(400);
        assertThat(status(api.post(url, Map.of("rating", 6), d.patient().token()))).isEqualTo(400);
        assertThat(status(api.post(url, Map.of("rating", 4), d.pro().token()))).isEqualTo(403); // seul le patient note
        Session stranger = fx.patient("Etranger");
        assertThat(status(api.post(url, Map.of("rating", 4), stranger.token()))).isEqualTo(404);

        MvcResult ok = api.post(url, Map.of("rating", 4, "comment", "Très à l'écoute"), d.patient().token());
        assertThat(status(ok)).isEqualTo(201);
        JsonNode review = api.body(ok);
        assertThat(review.get("rating").asInt()).isEqualTo(4);
        assertThat(review.get("authorName").asText()).matches("\\S+ \\p{Lu}\\.");

        MvcResult twice = api.post(url, Map.of("rating", 5), d.patient().token());
        assertThat(status(twice)).isEqualTo(409);
        assertThat(api.body(twice).get("code").asText()).isEqualTo("ALREADY_REVIEWED");
    }

    @Test
    void ratingIsRecomputedAndAdminCanHideAndRestore() throws Exception {
        Session pro = fx.approvedProfessional("Moy" + UUID.randomUUID().toString().substring(0, 6));
        Session p1 = fx.patient("Un");
        Session p2 = fx.patient("Deux");

        Done d1 = completed(pro, p1);
        String r1 = api.body(api.post("/api/v1/appointments/" + d1.appointmentId() + "/review",
                Map.of("rating", 5), d1.patient().token())).get("id").asText();
        Done d2 = completedAfter(pro, p2);
        assertThat(status(api.post("/api/v1/appointments/" + d2.appointmentId() + "/review",
                Map.of("rating", 2, "comment", "Décevant"), d2.patient().token()))).isEqualTo(201);

        clock.reset();
        Session viewer = api.login(p1.email(), ApiClient.PASSWORD);
        JsonNode view = professional(viewer, pro);
        assertThat(view.get("ratingCount").asInt()).isEqualTo(2);
        assertThat(view.get("ratingAvg").asDouble()).isEqualTo(3.5);

        JsonNode list = api.body(api.get("/api/v1/professionals/" + pro.userId() + "/reviews", viewer.token()));
        assertThat(list.get("totalElements").asInt()).isEqualTo(2);

        Session sup = api.login("admin@test.local", "AdminPassw0rd!");
        assertThat(status(api.post("/api/v1/admin/reviews/" + r1 + "/hide", Map.of(), sup.token()))).isEqualTo(400); // motif requis
        assertThat(status(api.post("/api/v1/admin/reviews/" + r1 + "/hide", Map.of("reason", "Propos inappropriés"),
                viewer.token()))).isEqualTo(403);
        MvcResult hidden = api.post("/api/v1/admin/reviews/" + r1 + "/hide", Map.of("reason", "Propos inappropriés"), sup.token());
        assertThat(status(hidden)).isEqualTo(200);
        assertThat(api.body(hidden).get("status").asText()).isEqualTo("HIDDEN");
        assertThat(status(api.post("/api/v1/admin/reviews/" + r1 + "/hide", Map.of("reason", "x"), sup.token()))).isEqualTo(409);

        JsonNode after = professional(viewer, pro);
        assertThat(after.get("ratingCount").asInt()).isEqualTo(1);
        assertThat(after.get("ratingAvg").asDouble()).isEqualTo(2.0);
        assertThat(api.body(api.get("/api/v1/professionals/" + pro.userId() + "/reviews", viewer.token()))
                .get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.body(api.get("/api/v1/admin/reviews", sup.token(), "status", "HIDDEN")).get("totalElements").asInt())
                .isGreaterThanOrEqualTo(1);

        assertThat(status(api.post("/api/v1/admin/reviews/" + r1 + "/show", Map.of(), sup.token()))).isEqualTo(200);
        JsonNode restored = professional(viewer, pro);
        assertThat(restored.get("ratingCount").asInt()).isEqualTo(2);
        assertThat(restored.get("ratingAvg").asDouble()).isEqualTo(3.5);
    }

    /** Second rendez-vous du même professionnel : créneau suivant, l'horloge reste après le premier. */
    private Done completedAfter(Session pro, Session patient) throws Exception {
        clock.reset();
        Session freshPro = api.login(pro.email(), ApiClient.PASSWORD);
        Session freshPatient = api.login(patient.email(), ApiClient.PASSWORD);
        Booking b = fx.book(freshPatient, freshPro, "TELECONSULTATION_VIDEO", 3);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), freshPro.token()))).isEqualTo(200);
        clock.travelTo(b.startAt().minusSeconds(60));
        Session p = api.login(pro.email(), ApiClient.PASSWORD);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/start", Map.of(), p.token()))).isEqualTo(200);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/complete", Map.of(), p.token()))).isEqualTo(200);
        return new Done(p, api.login(patient.email(), ApiClient.PASSWORD), b.id());
    }
}
