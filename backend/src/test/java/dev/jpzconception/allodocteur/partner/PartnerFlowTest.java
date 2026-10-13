package dev.jpzconception.allodocteur.partner;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.Fixtures;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Annuaire des partenaires et polices d'assurance des patients. */
class PartnerFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;
    Fixtures fx;
    Session admin;
    Session patient;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        admin = api.login("admin@test.local", "AdminPassw0rd!");
        patient = fx.patient("Assure");
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private JsonNode partner(String type) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Partenaire " + UUID.randomUUID().toString().substring(0, 6));
        m.put("type", type);
        m.put("cityId", fx.cityId());
        m.put("contactEmail", "contact@partenaire.test");
        m.put("contractStartDate", LocalDate.now().toString());
        MvcResult r = api.post("/api/v1/admin/partners", m, admin.token());
        assertThat(status(r)).isEqualTo(201);
        return api.body(r);
    }

    private Map<String, Object> policy(String partnerId, Object rate, LocalDate validUntil) {
        Map<String, Object> m = new HashMap<>();
        m.put("partnerId", partnerId);
        m.put("policyNumber", "POL-123456");
        m.put("coverageRate", rate);
        m.put("validUntil", validUntil.toString());
        return m;
    }

    @Test
    void adminManagesPartnersAndPatientsOnlySeeActiveOnes() throws Exception {
        JsonNode p = partner("CLINIC");
        String id = p.get("id").asText();
        assertThat(p.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(p.get("cityName").asText()).isNotBlank();

        assertThat(status(api.post("/api/v1/admin/partners", Map.of("name", "X", "type", "CLINIC"), patient.token()))).isEqualTo(403);
        assertThat(status(api.post("/api/v1/admin/partners", Map.of("name", "", "type", "CLINIC"), admin.token()))).isEqualTo(400);

        JsonNode listed = api.body(api.get("/api/v1/partners", patient.token(), "type", "CLINIC"));
        assertThat(listed.get("content").findValuesAsText("id")).contains(id);

        Map<String, Object> upd = new HashMap<>();
        upd.put("name", "Clinique Renommée");
        upd.put("type", "CLINIC");
        assertThat(api.body(api.put("/api/v1/admin/partners/" + id, upd, admin.token())).get("name").asText())
                .isEqualTo("Clinique Renommée");

        assertThat(status(api.post("/api/v1/admin/partners/" + id + "/deactivate", Map.of(), admin.token()))).isEqualTo(200);
        assertThat(api.body(api.get("/api/v1/partners", patient.token(), "type", "CLINIC")).get("content")
                .findValuesAsText("id")).doesNotContain(id);
        assertThat(api.body(api.get("/api/v1/admin/partners", admin.token(), "status", "INACTIVE")).get("content")
                .findValuesAsText("id")).contains(id);
        assertThat(status(api.post("/api/v1/admin/partners/" + id + "/activate", Map.of(), admin.token()))).isEqualTo(200);
    }

    @Test
    void patientRegistersAndManagesInsurancePolicies() throws Exception {
        String insurer = partner("INSURANCE").get("id").asText();
        String clinic = partner("CLINIC").get("id").asText();
        LocalDate next = LocalDate.now().plusYears(1);

        // seul un assureur ou une mutuelle actifs
        assertThat(status(api.post("/api/v1/insurance-policies", policy(clinic, 80, next), patient.token()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/insurance-policies", policy(insurer, 120, next), patient.token()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/insurance-policies", policy(insurer, 80, LocalDate.now().minusDays(1)),
                patient.token()))).isEqualTo(400);

        MvcResult created = api.post("/api/v1/insurance-policies", policy(insurer, 80, next), patient.token());
        assertThat(status(created)).isEqualTo(201);
        JsonNode pol = api.body(created);
        assertThat(pol.get("policyNumber").asText()).isEqualTo("POL-123456");
        assertThat(pol.get("expired").asBoolean()).isFalse();
        String id = pol.get("id").asText();

        // confidentialité : une autre personne ne voit ni ne modifie la police
        Session other = fx.patient("Voisin");
        assertThat(status(api.get("/api/v1/insurance-policies/" + id, other.token()))).isEqualTo(404);
        assertThat(status(api.put("/api/v1/insurance-policies/" + id, policy(insurer, 50, next), other.token()))).isEqualTo(404);
        assertThat(status(api.delete("/api/v1/insurance-policies/" + id, other.token()))).isEqualTo(404);
        assertThat(api.body(api.get("/api/v1/insurance-policies", other.token())).size()).isZero();

        // personne à charge inconnue
        Map<String, Object> badDependent = policy(insurer, 80, next);
        badDependent.put("dependentId", UUID.randomUUID().toString());
        assertThat(status(api.post("/api/v1/insurance-policies", badDependent, patient.token()))).isEqualTo(404);

        JsonNode updated = api.body(api.put("/api/v1/insurance-policies/" + id, policy(insurer, 65.5, next), patient.token()));
        assertThat(updated.get("coverageRate").asDouble()).isEqualTo(65.5);
        assertThat(api.body(api.get("/api/v1/insurance-policies", patient.token())).size()).isEqualTo(1);

        assertThat(status(api.delete("/api/v1/insurance-policies/" + id, patient.token()))).isEqualTo(204);
        assertThat(status(api.get("/api/v1/insurance-policies/" + id, patient.token()))).isEqualTo(404);
    }
}
