package dev.jpzconception.allodocteur.medicalrecord;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.Fixtures;
import dev.jpzconception.allodocteur.Fixtures.Booking;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

class MedicalRecordAccessTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    ApiClient api;
    Fixtures fx;
    Session pro;
    Session patient;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        pro = fx.approvedProfessional("Dossier" + UUID.randomUUID().toString().substring(0, 6));
        patient = fx.patient("Malade");
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String code(MvcResult r) throws Exception {
        return api.body(r).get("code").asText();
    }

    private void accept(Session who) throws Exception {
        String version = api.body(api.get("/api/v1/consents/current", who.token())).get("version").asText();
        assertThat(status(api.post("/api/v1/consents", Map.of("version", version), who.token()))).isEqualTo(200);
    }

    /** Rendez-vous réservé puis confirmé : établit le lien de soin. */
    private Booking care(Session who, Session byPro, String dependentId, int slot) throws Exception {
        Booking b = fx.book(who, byPro, "CHAT", slot, dependentId);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), byPro.token()))).isEqualTo(200);
        return b;
    }

    private String ownRecordId() throws Exception {
        return api.body(api.get("/api/v1/medical-records/me", patient.token())).get("id").asText();
    }

    // ------------------------------------------------------------------ consentement

    @Test
    void consentLifecycleIsVersionedAndRevocable() throws Exception {
        JsonNode initial = api.body(api.get("/api/v1/consents/current", patient.token()));
        assertThat(initial.get("accepted").asBoolean()).isFalse();
        assertThat(initial.get("content").asText()).isNotBlank();

        MvcResult stale = api.post("/api/v1/consents", Map.of("version", "0.1"), patient.token());
        assertThat(status(stale)).isEqualTo(409);
        assertThat(code(stale)).isEqualTo("CONSENT_VERSION_OUTDATED");

        accept(patient);
        accept(patient); // idempotent
        assertThat(api.body(api.get("/api/v1/consents/current", patient.token())).get("accepted").asBoolean()).isTrue();
        assertThat(api.body(api.get("/api/v1/consents/history", patient.token()))).hasSize(1);

        assertThat(status(api.delete("/api/v1/consents/current", patient.token()))).isEqualTo(204);
        assertThat(api.body(api.get("/api/v1/consents/current", patient.token())).get("accepted").asBoolean()).isFalse();

        accept(patient);
        JsonNode history = api.body(api.get("/api/v1/consents/history", patient.token()));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).path("revokedAt").isMissingNode() || history.get(0).path("revokedAt").isNull()).isTrue();
        assertThat(history.get(1).path("revokedAt").isMissingNode() || history.get(1).path("revokedAt").isNull()).isFalse();
    }

    @Test
    void consentIsReservedToPatients() throws Exception {
        assertThat(status(api.get("/api/v1/consents/current", pro.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/consents/current", fx.adminToken()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/consents/current", null))).isEqualTo(401);
    }

    // ------------------------------------------------------------------ accès

    @Test
    void ownerAlwaysReachesHerOwnRecordAndSummaryIsEncryptedAtRest() throws Exception {
        String id = ownRecordId();
        Map<String, Object> summary = Map.of("bloodGroup", "O_POS", "allergies", "Pénicilline",
                "chronicConditions", "Asthme", "currentMedications", "Ventoline", "familyHistory", "Diabète");
        MvcResult updated = api.put("/api/v1/medical-records/" + id + "/summary", summary, patient.token());
        assertThat(status(updated)).isEqualTo(200);
        assertThat(api.body(updated).get("allergies").asText()).isEqualTo("Pénicilline");
        assertThat(api.body(api.get("/api/v1/medical-records/" + id, patient.token())).get("bloodGroup").asText())
                .isEqualTo("O_POS");

        String raw = jdbc.queryForObject("select allergies from medical_records where id = ?::uuid", String.class, id);
        assertThat(raw).startsWith("v1:").doesNotContain("Pénicilline");
    }

    @Test
    void professionalNeedsCareRelationshipAndActiveConsent() throws Exception {
        Session stranger = fx.approvedProfessional("Etranger" + UUID.randomUUID().toString().substring(0, 6));
        String recordId = ownRecordId();
        Booking b = care(patient, pro, null, 0);
        String url = "/api/v1/appointments/" + b.id() + "/medical-record";

        // lien de soin mais pas de consentement
        MvcResult noConsent = api.get(url, pro.token());
        assertThat(status(noConsent)).isEqualTo(403);
        assertThat(code(noConsent)).isEqualTo("CONSENT_REQUIRED");

        // un professionnel sans lien de soin ne voit même pas que le dossier existe
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, stranger.token()))).isEqualTo(404);
        assertThat(status(api.get(url, stranger.token()))).isEqualTo(404);

        accept(patient);
        MvcResult ok = api.get(url, pro.token());
        assertThat(status(ok)).isEqualTo(200);
        assertThat(api.body(ok).get("id").asText()).isEqualTo(recordId);
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, pro.token()))).isEqualTo(200);
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, stranger.token()))).isEqualTo(404);

        // le retrait du consentement coupe l'accès immédiatement
        assertThat(status(api.delete("/api/v1/consents/current", patient.token()))).isEqualTo(204);
        MvcResult revoked = api.get("/api/v1/medical-records/" + recordId, pro.token());
        assertThat(status(revoked)).isEqualTo(403);
        assertThat(code(revoked)).isEqualTo("CONSENT_REQUIRED");

        // le professionnel ne peut pas modifier la fiche de synthèse, même avec consentement
        accept(patient);
        MvcResult summary = api.put("/api/v1/medical-records/" + recordId + "/summary",
                Map.of("allergies", "x"), pro.token());
        assertThat(status(summary)).isEqualTo(403);
        assertThat(code(summary)).isEqualTo("OWNER_ONLY");
    }

    @Test
    void otherPatientsAndAdminsNeverSeeTheRecord() throws Exception {
        String recordId = ownRecordId();
        Session other = fx.patient("Voisin");
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, other.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/medical-records/" + recordId + "/entries", other.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/medical-records/" + recordId + "/access-log", other.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, fx.adminToken()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, null))).isEqualTo(401);
    }

    // ------------------------------------------------------------------ carnet de santé

    @Test
    void entriesFollowRoleRulesAndAreEncrypted() throws Exception {
        accept(patient);
        Booking b = care(patient, pro, null, 0);
        String id = ownRecordId();
        String url = "/api/v1/medical-records/" + id + "/entries";

        MvcResult patientDiagnosis = api.post(url, Map.of("kind", "DIAGNOSIS", "title", "Je me soigne seul"), patient.token());
        assertThat(status(patientDiagnosis)).isEqualTo(403);
        assertThat(code(patientDiagnosis)).isEqualTo("ENTRY_KIND_NOT_ALLOWED");
        MvcResult proNote = api.post(url, Map.of("kind", "PERSONAL_NOTE", "title", "x"), pro.token());
        assertThat(status(proNote)).isEqualTo(403);
        assertThat(code(proNote)).isEqualTo("ENTRY_KIND_NOT_ALLOWED");

        MvcResult vaccine = api.post(url, Map.of("kind", "VACCINATION", "title", "Fièvre jaune",
                "content", "Lot 42", "occurredOn", "2025-05-01"), patient.token());
        assertThat(status(vaccine)).isEqualTo(201);
        String vaccineId = api.body(vaccine).get("id").asText();

        MvcResult diagnosis = api.post(url, Map.of("kind", "DIAGNOSIS", "title", "Paludisme simple",
                "content", "Traitement ACT", "appointmentId", b.id()), pro.token());
        assertThat(status(diagnosis)).isEqualTo(201);
        String diagnosisId = api.body(diagnosis).get("id").asText();
        assertThat(api.body(diagnosis).get("authorRole").asText()).isEqualTo("PROFESSIONAL");

        String rawTitle = jdbc.queryForObject("select title from medical_record_entries where id = ?::uuid",
                String.class, diagnosisId);
        assertThat(rawTitle).startsWith("v1:").doesNotContain("Paludisme");

        JsonNode page = api.body(api.get(url, patient.token()));
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        JsonNode onlyVaccines = api.body(api.get(url, patient.token(), "kind", "VACCINATION"));
        assertThat(onlyVaccines.get("content")).hasSize(1);

        // les notes cliniques ne se suppriment pas ; le patient retire ses propres entrées
        assertThat(status(api.delete(url + "/" + diagnosisId, patient.token()))).isEqualTo(403);
        assertThat(status(api.delete(url + "/" + vaccineId, patient.token()))).isEqualTo(204);
        assertThat(status(api.delete(url + "/" + vaccineId, patient.token()))).isEqualTo(404);
        assertThat(api.body(api.get(url, patient.token())).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void entryCannotReferenceAnotherPersonsAppointment() throws Exception {
        accept(patient);
        Booking b = care(patient, pro, null, 0);
        Session otherPatient = fx.patient("Autre");
        accept(otherPatient);
        Booking otherBooking = care(otherPatient, pro, null, 1);
        String url = "/api/v1/medical-records/" + ownRecordId() + "/entries";
        api.get("/api/v1/appointments/" + b.id() + "/medical-record", pro.token()); // provisionne le dossier

        MvcResult mismatch = api.post(url, Map.of("kind", "DIAGNOSIS", "title", "Erreur de dossier",
                "appointmentId", otherBooking.id()), pro.token());
        assertThat(status(mismatch)).isEqualTo(400);
        assertThat(code(mismatch)).isEqualTo("APPOINTMENT_MISMATCH");
    }

    @Test
    void dependentRecordBelongsToTheGuardian() throws Exception {
        String dependentId = api.body(api.post("/api/v1/patients/me/dependents", Map.of(
                "firstName", "Léa", "lastName", "Malade", "birthDate", "2018-03-01", "relationship", "CHILD"),
                patient.token())).get("id").asText();
        MvcResult mine = api.get("/api/v1/medical-records/dependents/" + dependentId, patient.token());
        assertThat(status(mine)).isEqualTo(200);
        JsonNode record = api.body(mine);
        assertThat(record.get("dependentId").asText()).isEqualTo(dependentId);
        assertThat(record.get("beneficiaryName").asText()).contains("Léa");
        assertThat(record.get("id").asText()).isNotEqualTo(ownRecordId());

        Session other = fx.patient("Etranger");
        assertThat(status(api.get("/api/v1/medical-records/dependents/" + dependentId, other.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/medical-records/" + record.get("id").asText(), other.token()))).isEqualTo(404);

        // le professionnel du rendez-vous de l'enfant voit le dossier de l'enfant, pas celui du parent
        accept(patient);
        Booking b = care(patient, pro, dependentId, 0);
        MvcResult viaAppointment = api.get("/api/v1/appointments/" + b.id() + "/medical-record", pro.token());
        assertThat(status(viaAppointment)).isEqualTo(200);
        assertThat(api.body(viaAppointment).get("id").asText()).isEqualTo(record.get("id").asText());
        assertThat(status(api.get("/api/v1/medical-records/" + ownRecordId(), pro.token()))).isEqualTo(404);
    }

    // ------------------------------------------------------------------ journal d'accès

    @Test
    void accessLogRecordsGrantedAndDeniedAttempts() throws Exception {
        Session stranger = fx.approvedProfessional("Curieux" + UUID.randomUUID().toString().substring(0, 6));
        String id = ownRecordId();
        Booking b = care(patient, pro, null, 0);

        api.get("/api/v1/appointments/" + b.id() + "/medical-record", pro.token()); // refusé : pas de consentement
        accept(patient);
        api.get("/api/v1/medical-records/" + id, pro.token());                       // autorisé
        api.get("/api/v1/medical-records/" + id, stranger.token());                  // refusé : pas de lien de soin

        JsonNode log = api.body(api.get("/api/v1/medical-records/" + id + "/access-log", patient.token())).get("content");
        List<String> reasons = log.findValuesAsText("reason");
        assertThat(reasons).contains("NO_CONSENT", "NO_CARE_RELATIONSHIP");
        assertThat(log.findValuesAsText("outcome")).contains("GRANTED", "DENIED");
        assertThat(log.findValuesAsText("actorId")).contains(pro.userId(), stranger.userId());
        // le propriétaire ne voit pas l'adresse IP
        log.forEach(l -> assertThat(l.path("ipAddress").isMissingNode() || l.path("ipAddress").isNull()).isTrue());

        // un professionnel ne lit pas le journal
        assertThat(status(api.get("/api/v1/medical-records/" + id + "/access-log", pro.token()))).isEqualTo(403);
    }

    @Test
    void accessLogIsAppendOnlyAndAdminViewIsSuperOnly() throws Exception {
        String id = ownRecordId();
        api.put("/api/v1/medical-records/" + id + "/summary", Map.of("allergies", "Aucune"), patient.token());

        JsonNode adminView = api.body(api.get("/api/v1/admin/medical-access-logs", fx.adminToken(), "recordId", id));
        assertThat(adminView.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(adminView.toString()).doesNotContain("Aucune"); // métadonnées seulement

        assertThat(status(api.get("/api/v1/admin/medical-access-logs", patient.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/admin/medical-access-logs", pro.token()))).isEqualTo(403);

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("delete from medical_access_logs where record_id = ?::uuid", id));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update medical_access_logs set reason = 'x' where record_id = ?::uuid", id));
    }
}
