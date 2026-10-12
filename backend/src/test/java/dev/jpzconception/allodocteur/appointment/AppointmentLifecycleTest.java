package dev.jpzconception.allodocteur.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.ClockTestConfig;
import dev.jpzconception.allodocteur.Fixtures;
import dev.jpzconception.allodocteur.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/** Cycle de vie complet, avec une horloge déplaçable pour atteindre l'heure du rendez-vous. */
@Import(ClockTestConfig.class)
class AppointmentLifecycleTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;

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

    private record Booked(Session pro, Session patient, String id, Instant startAt) {}

    private Booked book(int slotIndex) throws Exception {
        Session pro = fx.approvedProfessional("Cycle" + UUID.randomUUID().toString().substring(0, 6));
        Session patient = fx.patient("Cycle");
        LocalDate day = LocalDate.now(ZoneOffset.UTC).plusDays(2);
        JsonNode slot = api.body(api.get("/api/v1/professionals/" + pro.userId() + "/slots", patient.token(),
                "type", "TELECONSULTATION_VIDEO", "from", day.toString())).get(slotIndex);
        Map<String, Object> req = new HashMap<>();
        req.put("professionalId", pro.userId());
        req.put("type", "TELECONSULTATION_VIDEO");
        req.put("reason", "CONSULTATION");
        req.put("startAt", slot.get("startAt").asText());
        JsonNode created = api.body(api.post("/api/v1/appointments", req, patient.token()));
        return new Booked(pro, patient, created.get("id").asText(), Instant.parse(slot.get("startAt").asText()));
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    @Test
    void confirmStartCompleteRespectingRolesAndTiming() throws Exception {
        Booked b = book(0);
        String base = "/api/v1/appointments/" + b.id();
        Session stranger = fx.approvedProfessional("Stranger" + UUID.randomUUID().toString().substring(0, 6));

        // seul le professionnel concerné confirme
        assertThat(status(api.post(base + "/confirm", Map.of(), b.patient().token()))).isEqualTo(403);
        assertThat(status(api.post(base + "/confirm", Map.of(), stranger.token()))).isEqualTo(404);
        MvcResult confirmed = api.post(base + "/confirm", Map.of(), b.pro().token());
        assertThat(status(confirmed)).isEqualTo(200);
        assertThat(api.body(confirmed).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(status(api.post(base + "/confirm", Map.of(), b.pro().token()))).isEqualTo(409); // déjà confirmé

        // trop tôt pour démarrer
        MvcResult early = api.post(base + "/start", Map.of(), b.pro().token());
        assertThat(status(early)).isEqualTo(409);
        assertThat(api.body(early).get("code").asText()).isEqualTo("TOO_EARLY");

        // à l'heure : démarrage puis fin
        clock.travelTo(b.startAt().minusSeconds(120));
        Session pro = api.login(b.pro().email(), ApiClient.PASSWORD);
        MvcResult started = api.post(base + "/start", Map.of(), pro.token());
        assertThat(status(started)).isEqualTo(200);
        assertThat(api.body(started).get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(status(api.post(base + "/no-show", Map.of(), pro.token()))).isEqualTo(409);

        MvcResult done = api.post(base + "/complete", Map.of(), pro.token());
        assertThat(status(done)).isEqualTo(200);
        assertThat(api.body(done).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(api.body(done).has("completedAt")).isTrue();
        assertThat(status(api.post(base + "/cancel", Map.of("reason", "x"), pro.token()))).isEqualTo(409);
    }

    @Test
    void noShowOnlyAfterTheAppointmentTime() throws Exception {
        Booked b = book(1);
        String base = "/api/v1/appointments/" + b.id();
        assertThat(status(api.post(base + "/confirm", Map.of(), b.pro().token()))).isEqualTo(200);
        assertThat(status(api.post(base + "/no-show", Map.of(), b.pro().token()))).isEqualTo(409);

        clock.travelTo(b.startAt().plusSeconds(20 * 60));
        Session pro = api.login(b.pro().email(), ApiClient.PASSWORD);
        MvcResult r = api.post(base + "/no-show", Map.of(), pro.token());
        assertThat(status(r)).isEqualTo(200);
        assertThat(api.body(r).get("status").asText()).isEqualTo("NO_SHOW");
    }

    @Test
    void cancellationRules() throws Exception {
        Booked b = book(2);
        String base = "/api/v1/appointments/" + b.id();

        // le professionnel doit motiver son annulation, pas le patient
        assertThat(status(api.post(base + "/confirm", Map.of(), b.pro().token()))).isEqualTo(200);
        MvcResult noReason = api.post(base + "/cancel", Map.of(), b.pro().token());
        assertThat(status(noReason)).isEqualTo(400);
        assertThat(api.body(noReason).get("code").asText()).isEqualTo("REASON_REQUIRED");

        // un patient ne peut plus annuler un rendez-vous confirmé à moins de 2 h du début
        clock.travelTo(b.startAt().minusSeconds(60 * 60));
        Session patient = api.login(b.patient().email(), ApiClient.PASSWORD);
        MvcResult late = api.post(base + "/cancel", Map.of(), patient.token());
        assertThat(status(late)).isEqualTo(409);
        assertThat(api.body(late).get("code").asText()).isEqualTo("CANCELLATION_TOO_LATE");

        // le professionnel (avec motif) ou un admin SUPER (avec motif) le peuvent encore
        Session pro = api.login(b.pro().email(), ApiClient.PASSWORD);
        MvcResult byPro = api.post(base + "/cancel", Map.of("reason", "Urgence"), pro.token());
        assertThat(status(byPro)).isEqualTo(200);
        JsonNode body = api.body(byPro);
        assertThat(body.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(body.get("cancelReason").asText()).isEqualTo("Urgence");
        assertThat(body.get("cancelledBy").asText()).isEqualTo(b.pro().userId());
    }

    @Test
    void adminCanCancelWithAReason() throws Exception {
        Booked b = book(3);
        String base = "/api/v1/appointments/" + b.id();
        Session admin = api.login("admin@test.local", "AdminPassw0rd!");
        assertThat(status(api.post(base + "/cancel", Map.of(), admin.token()))).isEqualTo(400);
        MvcResult r = api.post(base + "/cancel", Map.of("reason", "Doublon signalé"), admin.token());
        assertThat(status(r)).isEqualTo(200);
        JsonNode body = api.body(r);
        assertThat(body.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(body.has("reasonDetails")).isFalse();
    }
}
