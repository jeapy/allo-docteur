package dev.jpzconception.allodocteur.prescription;

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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

@Import(ClockTestConfig.class)
class PrescriptionFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    ApiClient api;
    Fixtures fx;
    Session pro;
    Session patient;
    Session stranger;
    Session otherPatient;
    Booking booking;

    static final Map<String, Object> ORDER = Map.of(
            "notes", "Boire beaucoup d'eau",
            "items", List.of(
                    Map.of("medicationName", "Paracétamol 500 mg", "dosage", "1 comprimé", "frequency", "3 fois par jour",
                            "durationDays", 5, "quantity", 15, "instructions", "Après les repas"),
                    Map.of("medicationName", "Amoxicilline 1 g", "durationDays", 7)));

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        pro = fx.approvedProfessional("Presc" + UUID.randomUUID().toString().substring(0, 6));
        patient = fx.patient("Ordonnance");
        stranger = fx.approvedProfessional("Curieux" + UUID.randomUUID().toString().substring(0, 6));
        otherPatient = fx.patient("Voisin");
        booking = fx.book(patient, pro, "CHAT", 0);
        assertThat(status(api.post("/api/v1/appointments/" + booking.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String code(MvcResult r) throws Exception {
        return api.body(r).get("code").asText();
    }

    private void accept() throws Exception {
        String version = api.body(api.get("/api/v1/consents/current", patient.token())).get("version").asText();
        assertThat(status(api.post("/api/v1/consents", Map.of("version", version), patient.token()))).isEqualTo(200);
    }

    /** Déplace l'horloge et reconnecte les deux parties (le jeton dépend de l'horloge). */
    private void travel(java.time.Instant target) throws Exception {
        clock.travelTo(target);
        pro = api.login(pro.email(), ApiClient.PASSWORD);
        patient = api.login(patient.email(), ApiClient.PASSWORD);
        stranger = api.login(stranger.email(), ApiClient.PASSWORD);
        otherPatient = api.login(otherPatient.email(), ApiClient.PASSWORD);
    }

    private String adminToken() throws Exception {
        return api.login("admin@test.local", "AdminPassw0rd!").token();
    }

    private void startConsultation() throws Exception {
        travel(booking.startAt().minusSeconds(60));
        assertThat(status(api.post("/api/v1/appointments/" + booking.id() + "/start", Map.of(), pro.token()))).isEqualTo(200);
    }

    private String issuePath() {
        return "/api/v1/appointments/" + booking.id() + "/prescriptions";
    }

    @Test
    void cannotPrescribeBeforeTheConsultationStarts() throws Exception {
        accept();
        MvcResult r = api.post(issuePath(), ORDER, pro.token());
        assertThat(status(r)).isEqualTo(409);
        assertThat(code(r)).isEqualTo("PRESCRIPTION_NOT_ALLOWED");
    }

    @Test
    void prescribingRequiresPatientConsent() throws Exception {
        startConsultation();
        MvcResult r = api.post(issuePath(), ORDER, pro.token());
        assertThat(status(r)).isEqualTo(403);
        assertThat(code(r)).isEqualTo("CONSENT_REQUIRED");
    }

    @Test
    void validationAndRolesAreEnforced() throws Exception {
        accept();
        startConsultation();
        assertThat(status(api.post(issuePath(), Map.of("items", List.of()), pro.token()))).isEqualTo(400);
        assertThat(status(api.post(issuePath(), Map.of("items", List.of(Map.of("medicationName", " "))), pro.token())))
                .isEqualTo(400);
        assertThat(status(api.post(issuePath(), ORDER, patient.token()))).isEqualTo(403);
        assertThat(status(api.post(issuePath(), ORDER, adminToken()))).isEqualTo(403);
        assertThat(status(api.post(issuePath(), ORDER, stranger.token()))).isEqualTo(404);
    }

    @Test
    void fullPrescriptionLifecycle() throws Exception {
        accept();
        startConsultation();

        MvcResult issued = api.post(issuePath(), ORDER, pro.token());
        assertThat(status(issued)).isEqualTo(201);
        JsonNode p = api.body(issued);
        String id = p.get("id").asText();
        assertThat(p.get("number").asText()).matches("ORD-\\d{4}-\\d{6}");
        assertThat(p.get("status").asText()).isEqualTo("ISSUED");
        assertThat(p.get("items")).hasSize(2);
        assertThat(p.get("items").get(0).get("lineNumber").asInt()).isEqualTo(1);
        assertThat(p.get("integrityValid").asBoolean()).isTrue();
        assertThat(p.get("beneficiaryName").asText()).contains("Ordonnance");

        // médicaments, posologie et notes sont chiffrés au repos
        String rawName = jdbc.queryForObject("select medication_name from prescription_items where prescription_id = ?::uuid and line_number = 1",
                String.class, id);
        assertThat(rawName).startsWith("v1:").doesNotContain("Paracétamol");
        String rawNotes = jdbc.queryForObject("select notes from prescriptions where id = ?::uuid", String.class, id);
        assertThat(rawNotes).startsWith("v1:").doesNotContain("eau");

        // listes : sans donnée de santé
        MvcResult mine = api.get("/api/v1/prescriptions", patient.token());
        assertThat(api.body(mine).get("totalElements").asInt()).isEqualTo(1);
        assertThat(mine.getResponse().getContentAsString()).doesNotContain("Paracétamol").doesNotContain("Amoxicilline");
        assertThat(api.body(api.get("/api/v1/prescriptions", pro.token())).get("totalElements").asInt()).isEqualTo(1);

        // lecture du détail
        JsonNode detail = api.body(api.get("/api/v1/prescriptions/" + id, patient.token()));
        assertThat(detail.get("items").get(0).get("medicationName").asText()).isEqualTo("Paracétamol 500 mg");
        assertThat(status(api.get("/api/v1/prescriptions/" + id, pro.token()))).isEqualTo(200);
        String recordId = detail.get("recordId").asText();
        JsonNode inRecord = api.body(api.get("/api/v1/medical-records/" + recordId + "/prescriptions", patient.token()));
        assertThat(inRecord.get("content")).hasSize(1);

        // les autres n'y accèdent pas
        assertThat(status(api.get("/api/v1/prescriptions/" + id, otherPatient.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/prescriptions/" + id, stranger.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/prescriptions/" + id, adminToken()))).isEqualTo(403);
        assertThat(api.body(api.get("/api/v1/prescriptions", otherPatient.token())).get("totalElements").asInt()).isZero();

        // l'émission et la lecture sont journalisées
        List<String> actions = api.body(api.get("/api/v1/medical-records/" + recordId + "/access-log", patient.token()))
                .get("content").findValuesAsText("action");
        assertThat(actions).contains("CREATE_PRESCRIPTION", "VIEW_PRESCRIPTION");

        // altération en base : détectée par l'empreinte
        jdbc.update("update prescription_items set duration_days = 99 where prescription_id = ?::uuid and line_number = 1", id);
        assertThat(api.body(api.get("/api/v1/prescriptions/" + id, patient.token())).get("integrityValid").asBoolean()).isFalse();
        jdbc.update("update prescription_items set duration_days = 5 where prescription_id = ?::uuid and line_number = 1", id);

        // annulation : par l'émetteur seulement, une seule fois
        assertThat(status(api.post("/api/v1/prescriptions/" + id + "/cancel", Map.of("reason", "Erreur"), patient.token())))
                .isEqualTo(403);
        assertThat(status(api.post("/api/v1/prescriptions/" + id + "/cancel", Map.of("reason", "Erreur"), stranger.token())))
                .isEqualTo(404);
        assertThat(status(api.post("/api/v1/prescriptions/" + id + "/cancel", Map.of("reason", " "), pro.token()))).isEqualTo(400);
        MvcResult cancelled = api.post("/api/v1/prescriptions/" + id + "/cancel", Map.of("reason", "Erreur de dosage"), pro.token());
        assertThat(status(cancelled)).isEqualTo(200);
        assertThat(api.body(cancelled).get("status").asText()).isEqualTo("CANCELLED");
        MvcResult again = api.post("/api/v1/prescriptions/" + id + "/cancel", Map.of("reason", "Encore"), pro.token());
        assertThat(status(again)).isEqualTo(409);
        assertThat(code(again)).isEqualTo("PRESCRIPTION_ALREADY_CANCELLED");
    }

    @Test
    void revokedConsentBlocksTheProfessionalButNotThePatient() throws Exception {
        accept();
        startConsultation();
        String id = api.body(api.post(issuePath(), ORDER, pro.token())).get("id").asText();

        assertThat(status(api.delete("/api/v1/consents/current", patient.token()))).isEqualTo(204);
        MvcResult r = api.get("/api/v1/prescriptions/" + id, pro.token());
        assertThat(status(r)).isEqualTo(403);
        assertThat(code(r)).isEqualTo("CONSENT_REQUIRED");
        assertThat(status(api.get("/api/v1/prescriptions/" + id, patient.token()))).isEqualTo(200);
    }

    @Test
    void issueWindowAfterCompletionAndValidityExpiry() throws Exception {
        accept();
        startConsultation();
        assertThat(status(api.post("/api/v1/appointments/" + booking.id() + "/complete", Map.of(), pro.token()))).isEqualTo(200);

        // dans le délai de 3 jours
        travel(booking.startAt().plus(Duration.ofDays(2)));
        MvcResult inWindow = api.post(issuePath(), ORDER, pro.token());
        assertThat(status(inWindow)).isEqualTo(201);
        String id = api.body(inWindow).get("id").asText();
        assertThat(api.body(inWindow).get("expired").asBoolean()).isFalse();

        // hors délai
        travel(booking.startAt().plus(Duration.ofDays(5)));
        MvcResult late = api.post(issuePath(), ORDER, pro.token());
        assertThat(status(late)).isEqualTo(409);
        assertThat(code(late)).isEqualTo("PRESCRIPTION_NOT_ALLOWED");

        // l'ordonnance expire après 90 jours mais reste consultable
        travel(booking.startAt().plus(Duration.ofDays(100)));
        JsonNode expired = api.body(api.get("/api/v1/prescriptions/" + id, patient.token()));
        assertThat(expired.get("expired").asBoolean()).isTrue();
        assertThat(expired.get("status").asText()).isEqualTo("ISSUED");
    }
}
