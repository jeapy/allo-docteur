package dev.jpzconception.allodocteur.sos;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.Fixtures;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

class SosFlowTest extends AbstractIntegrationTest {

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
        pro = fx.approvedProfessional("Urgence" + UUID.randomUUID().toString().substring(0, 6));
        patient = fx.patient("Urgent");
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String code(MvcResult r) throws Exception {
        return api.body(r).get("code").asText();
    }

    private Map<String, Object> alert() {
        Map<String, Object> body = new HashMap<>();
        body.put("category", "MEDICAL");
        body.put("latitude", 5.3601);
        body.put("longitude", -4.0084);
        body.put("description", "Douleur thoracique intense");
        body.put("cityId", fx.cityId());
        return body;
    }

    private JsonNode createAlert(Session who) throws Exception {
        MvcResult r = api.post("/api/v1/sos", alert(), who.token());
        assertThat(status(r)).isEqualTo(201);
        return api.body(r);
    }

    private void accept(Session who) throws Exception {
        String version = api.body(api.get("/api/v1/consents/current", who.token())).get("version").asText();
        assertThat(status(api.post("/api/v1/consents", Map.of("version", version), who.token()))).isEqualTo(200);
    }

    private Map<String, Object> facility(String name, double lat, double lon) {
        Map<String, Object> f = new HashMap<>();
        f.put("name", name);
        f.put("type", "HOSPITAL");
        f.put("cityId", fx.cityId());
        f.put("latitude", lat);
        f.put("longitude", lon);
        f.put("emergencyService", true);
        return f;
    }

    // ------------------------------------------------------------------ déclenchement

    @Test
    void alertIsCreatedWithNearestFacilitiesAndOnlyOneActiveAtATime() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        assertThat(status(api.post("/api/v1/admin/health-facilities", facility("Lointain " + suffix, 5.50, -3.90), fx.adminToken())))
                .isEqualTo(201);
        assertThat(status(api.post("/api/v1/admin/health-facilities", facility("Proche " + suffix, 5.3602, -4.0085), fx.adminToken())))
                .isEqualTo(201);

        JsonNode created = createAlert(patient);
        assertThat(created.get("status").asText()).isEqualTo("OPEN");
        assertThat(created.get("address").asText()).startsWith("Position GPS");
        JsonNode near = created.get("nearestFacilities");
        assertThat(near.size()).isGreaterThanOrEqualTo(2);
        for (int i = 1; i < near.size(); i++) {
            assertThat(near.get(i).get("distanceKm").asDouble()).isGreaterThanOrEqualTo(near.get(i - 1).get("distanceKm").asDouble());
        }

        MvcResult again = api.post("/api/v1/sos", alert(), patient.token());
        assertThat(status(again)).isEqualTo(409);
        assertThat(code(again)).isEqualTo("SOS_ALREADY_ACTIVE");
    }

    @Test
    void invalidPositionIsRejectedAndRolesAreEnforced() throws Exception {
        Map<String, Object> bad = alert();
        bad.put("latitude", 95);
        assertThat(status(api.post("/api/v1/sos", bad, patient.token()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/sos", alert(), pro.token()))).isEqualTo(403);
        assertThat(status(api.post("/api/v1/sos", alert(), fx.adminToken()))).isEqualTo(403);
        assertThat(status(api.post("/api/v1/sos", alert(), null))).isEqualTo(401);
        Map<String, Object> foreignDependent = alert();
        foreignDependent.put("dependentId", UUID.randomUUID().toString());
        assertThat(status(api.post("/api/v1/sos", foreignDependent, patient.token()))).isEqualTo(404);
    }

    // ------------------------------------------------------------------ prise en charge

    @Test
    void professionalsSeeLimitedOpenAlertsAndOnlyEligibleOnesCanAnswer() throws Exception {
        JsonNode created = createAlert(patient);
        String id = created.get("id").asText();

        MvcResult open = api.get("/api/v1/sos/open", pro.token());
        assertThat(status(open)).isEqualTo(200);
        String body = open.getResponse().getContentAsString();
        assertThat(body).contains(id).doesNotContain("Douleur").doesNotContain("latitude").doesNotContain("Urgent");

        Session noHomeVisit = fx.approvedProfessional("Distance" + UUID.randomUUID().toString().substring(0, 6), true, false);
        MvcResult denied = api.get("/api/v1/sos/open", noHomeVisit.token());
        assertThat(status(denied)).isEqualTo(403);
        assertThat(code(denied)).isEqualTo("SOS_NOT_AVAILABLE");
        assertThat(status(api.get("/api/v1/sos/open", patient.token()))).isEqualTo(403);

        // avant d'accepter, le détail reste fermé aux professionnels
        assertThat(status(api.get("/api/v1/sos/" + id, pro.token()))).isEqualTo(404);
    }

    @Test
    void firstProfessionalWinsAndAnUrgentVisitIsCreated() throws Exception {
        String id = createAlert(patient).get("id").asText();
        Session rival = fx.approvedProfessional("Rival" + UUID.randomUUID().toString().substring(0, 6));

        MvcResult accepted = api.post("/api/v1/sos/" + id + "/accept", Map.of(), pro.token());
        assertThat(status(accepted)).isEqualTo(200);
        JsonNode a = api.body(accepted);
        assertThat(a.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(a.get("description").asText()).contains("Douleur");
        assertThat(a.get("professional").get("id").asText()).isEqualTo(pro.userId());
        String appointmentId = a.get("appointmentId").asText();

        MvcResult late = api.post("/api/v1/sos/" + id + "/accept", Map.of(), rival.token());
        assertThat(status(late)).isEqualTo(409);
        assertThat(code(late)).isEqualTo("SOS_ALREADY_TAKEN");

        JsonNode appointment = api.body(api.get("/api/v1/appointments/" + appointmentId, patient.token()));
        assertThat(appointment.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(appointment.get("type").asText()).isEqualTo("HOME_VISIT");
        assertThat(status(api.get("/api/v1/appointments/" + appointmentId, pro.token()))).isEqualTo(200);

        // seuls le patient et le professionnel assigné voient le détail
        assertThat(status(api.get("/api/v1/sos/" + id, patient.token()))).isEqualTo(200);
        assertThat(status(api.get("/api/v1/sos/" + id, pro.token()))).isEqualTo(200);
        assertThat(status(api.get("/api/v1/sos/" + id, rival.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/sos/" + id, fx.patient("Autre").token()))).isEqualTo(404);
        assertThat(api.body(api.get("/api/v1/sos/mine", patient.token())).get("totalElements").asInt()).isEqualTo(1);

        // une alerte prise n'apparaît plus dans les alertes ouvertes
        assertThat(api.get("/api/v1/sos/open", rival.token()).getResponse().getContentAsString()).doesNotContain(id);
    }

    @Test
    void descriptionIsEncryptedAtRest() throws Exception {
        String id = createAlert(patient).get("id").asText();
        String raw = jdbc.queryForObject("select description from sos_alerts where id = ?::uuid", String.class, id);
        assertThat(raw).startsWith("v1:").doesNotContain("Douleur");
    }

    // ------------------------------------------------------------------ bris de glace

    @Test
    void emergencyAccessReplacesConsentOnlyForTheAssignedProfessionalAndOnlyWhileActive() throws Exception {
        String id = createAlert(patient).get("id").asText();
        String appointmentId = api.body(api.post("/api/v1/sos/" + id + "/accept", Map.of(), pro.token()))
                .get("appointmentId").asText();
        String recordUrl = "/api/v1/appointments/" + appointmentId + "/medical-record";
        Session rival = fx.approvedProfessional("Curieux" + UUID.randomUUID().toString().substring(0, 6));

        // sans consentement ni accès d'urgence : refusé
        MvcResult before = api.get(recordUrl, pro.token());
        assertThat(status(before)).isEqualTo(403);
        assertThat(code(before)).isEqualTo("CONSENT_REQUIRED");

        // motif obligatoire, réservé au professionnel assigné
        assertThat(status(api.post("/api/v1/sos/" + id + "/emergency-access", Map.of("reason", " "), pro.token()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/sos/" + id + "/emergency-access", Map.of("reason", "Suspicion d'infarctus"), rival.token())))
                .isEqualTo(404);
        assertThat(status(api.post("/api/v1/sos/" + id + "/emergency-access", Map.of("reason", "Suspicion d'infarctus"), patient.token())))
                .isEqualTo(403);

        MvcResult granted = api.post("/api/v1/sos/" + id + "/emergency-access", Map.of("reason", "Suspicion d'infarctus"), pro.token());
        assertThat(status(granted)).isEqualTo(200);
        assertThat(api.body(granted).get("expiresAt").asText()).isNotBlank();
        // idempotent
        assertThat(status(api.post("/api/v1/sos/" + id + "/emergency-access", Map.of("reason", "Encore"), pro.token()))).isEqualTo(200);

        MvcResult during = api.get(recordUrl, pro.token());
        assertThat(status(during)).isEqualTo(200);
        String recordId = api.body(during).get("id").asText();

        // le patient le voit dans son journal d'accès
        JsonNode log = api.body(api.get("/api/v1/medical-records/" + recordId + "/access-log", patient.token())).get("content");
        assertThat(log.findValuesAsText("action")).contains("EMERGENCY_ACCESS");
        assertThat(log.findValuesAsText("reason")).contains("EMERGENCY_ACCESS_GRANTED", "EMERGENCY_ACCESS");

        // un autre professionnel n'en profite pas
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, rival.token()))).isEqualTo(404);

        // clôture : l'accès d'urgence cesse
        assertThat(status(api.post("/api/v1/sos/" + id + "/resolve", Map.of("note", "Transfert au CHU"), pro.token()))).isEqualTo(200);
        MvcResult after = api.get("/api/v1/medical-records/" + recordId, pro.token());
        assertThat(status(after)).isEqualTo(403);
        assertThat(code(after)).isEqualTo("CONSENT_REQUIRED");

        // avec le consentement ordinaire, le lien de soin suffit toujours
        accept(patient);
        assertThat(status(api.get("/api/v1/medical-records/" + recordId, pro.token()))).isEqualTo(200);
    }

    // ------------------------------------------------------------------ clôture et annulation

    @Test
    void resolveIsReservedToTheAssignedProfessionalAndFreesThePatientForANewAlert() throws Exception {
        String id = createAlert(patient).get("id").asText();
        Session rival = fx.approvedProfessional("Autre" + UUID.randomUUID().toString().substring(0, 6));
        assertThat(status(api.post("/api/v1/sos/" + id + "/resolve", Map.of(), pro.token()))).isEqualTo(404);
        api.post("/api/v1/sos/" + id + "/accept", Map.of(), pro.token());
        assertThat(status(api.post("/api/v1/sos/" + id + "/resolve", Map.of(), rival.token()))).isEqualTo(404);
        MvcResult resolved = api.post("/api/v1/sos/" + id + "/resolve", Map.of("note", "RAS"), pro.token());
        assertThat(status(resolved)).isEqualTo(200);
        assertThat(api.body(resolved).get("status").asText()).isEqualTo("RESOLVED");
        MvcResult twice = api.post("/api/v1/sos/" + id + "/resolve", Map.of(), pro.token());
        assertThat(status(twice)).isEqualTo(409);
        assertThat(code(twice)).isEqualTo("SOS_INVALID_STATE");

        assertThat(status(api.post("/api/v1/sos", alert(), patient.token()))).isEqualTo(201);
    }

    @Test
    void patientCancelsAnOpenAlertOrAnAcceptedOneWithItsVisit() throws Exception {
        String open = createAlert(patient).get("id").asText();
        MvcResult cancelled = api.post("/api/v1/sos/" + open + "/cancel", Map.of("reason", "Fausse alerte"), patient.token());
        assertThat(status(cancelled)).isEqualTo(200);
        assertThat(api.body(cancelled).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(code(api.post("/api/v1/sos/" + open + "/cancel", Map.of(), patient.token()))).isEqualTo("SOS_INVALID_STATE");
        assertThat(status(api.post("/api/v1/sos/" + open + "/accept", Map.of(), pro.token()))).isEqualTo(409);

        String taken = createAlert(patient).get("id").asText();
        String appointmentId = api.body(api.post("/api/v1/sos/" + taken + "/accept", Map.of(), pro.token()))
                .get("appointmentId").asText();
        assertThat(status(api.post("/api/v1/sos/" + taken + "/cancel", Map.of(), patient.token()))).isEqualTo(200);
        assertThat(api.body(api.get("/api/v1/appointments/" + appointmentId, patient.token())).get("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(status(api.post("/api/v1/sos/" + taken + "/cancel", Map.of(), fx.patient("Etranger").token()))).isEqualTo(404);
    }

    // ------------------------------------------------------------------ administration

    @Test
    void adminFollowsEmergenciesWithoutSeeingHealthDataAndCanCancelWithAReason() throws Exception {
        String id = createAlert(patient).get("id").asText();

        MvcResult list = api.get("/api/v1/admin/sos", fx.adminToken(), "status", "OPEN");
        assertThat(status(list)).isEqualTo(200);
        String body = list.getResponse().getContentAsString();
        assertThat(body).contains(id).doesNotContain("Douleur").doesNotContain("latitude").doesNotContain("longitude");
        assertThat(api.body(api.get("/api/v1/admin/sos/summary", fx.adminToken())).get("open").asInt()).isGreaterThanOrEqualTo(1);

        assertThat(status(api.get("/api/v1/admin/sos", patient.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/admin/sos", pro.token()))).isEqualTo(403);

        assertThat(status(api.post("/api/v1/admin/sos/" + id + "/cancel", Map.of("reason", " "), fx.adminToken()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/admin/sos/" + id + "/cancel", Map.of("reason", "Doublon signalé"), fx.adminToken())))
                .isEqualTo(204);
        assertThat(api.body(api.get("/api/v1/sos/" + id, patient.token())).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void facilitiesAreManagedByAdminsAndSearchableByPosition() throws Exception {
        String name = "Clinique " + UUID.randomUUID().toString().substring(0, 6);
        MvcResult created = api.post("/api/v1/admin/health-facilities", facility(name, 5.40, -4.00), fx.adminToken());
        assertThat(status(created)).isEqualTo(201);
        String id = api.body(created).get("id").asText();

        assertThat(status(api.post("/api/v1/admin/health-facilities", facility(name, 5.40, -4.00), patient.token()))).isEqualTo(403);
        Map<String, Object> badCity = facility("X", 5.4, -4.0);
        badCity.put("cityId", 999999);
        assertThat(status(api.post("/api/v1/admin/health-facilities", badCity, fx.adminToken()))).isEqualTo(400);

        assertThat(api.get("/api/v1/health-facilities/nearby", patient.token(), "latitude", "5.40", "longitude", "-4.00",
                "limit", "20").getResponse().getContentAsString()).contains(name);

        assertThat(status(api.post("/api/v1/admin/health-facilities/" + id + "/deactivate", Map.of(), fx.adminToken()))).isEqualTo(200);
        assertThat(api.get("/api/v1/health-facilities/nearby", patient.token(), "latitude", "5.40", "longitude", "-4.00",
                "limit", "20").getResponse().getContentAsString()).doesNotContain(name);
    }
}
