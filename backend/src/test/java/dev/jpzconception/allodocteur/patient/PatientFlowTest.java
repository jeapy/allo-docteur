package dev.jpzconception.allodocteur.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class PatientFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, json);
    }

    @Test
    void emptyProfileThenUpdate() throws Exception {
        Session patient = api.registerAndLogin("PATIENT", "Kouassi");

        JsonNode empty = api.body(api.get("/api/v1/patients/me", patient.token()));
        assertThat(empty.get("lastName").asText()).isEqualTo("Kouassi");
        assertThat(empty.has("birthDate")).isFalse();

        MvcResult put = api.put("/api/v1/patients/me", Map.of(
                "birthDate", "1990-05-12", "gender", "FEMALE", "address", "Rue des Jardins",
                "emergencyContactName", "Awa", "emergencyContactPhone", "+2250700000000"), patient.token());
        assertThat(put.getResponse().getStatus()).isEqualTo(200);
        JsonNode saved = api.body(api.get("/api/v1/patients/me", patient.token()));
        assertThat(saved.get("gender").asText()).isEqualTo("FEMALE");
        assertThat(saved.get("birthDate").asText()).isEqualTo("1990-05-12");
    }

    @Test
    void rejectsFutureBirthDateAndUnknownCity() throws Exception {
        Session patient = api.registerAndLogin("PATIENT", "Test");
        assertThat(api.put("/api/v1/patients/me", Map.of("birthDate", "2999-01-01"), patient.token())
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(api.put("/api/v1/patients/me", Map.of("cityId", 999999), patient.token())
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void dependentsBelongToTheirGuardianOnly() throws Exception {
        Session parent = api.registerAndLogin("PATIENT", "Parent");
        Session other = api.registerAndLogin("PATIENT", "Autre");

        MvcResult created = api.post("/api/v1/patients/me/dependents", Map.of(
                "firstName", "Léa", "lastName", "Parent", "birthDate", "2018-03-01", "relationship", "CHILD"),
                parent.token());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String id = api.body(created).get("id").asText();

        assertThat(api.body(api.get("/api/v1/patients/me/dependents", parent.token()))).hasSize(1);
        assertThat(api.body(api.get("/api/v1/patients/me/dependents", other.token()))).isEmpty();
        assertThat(api.put("/api/v1/patients/me/dependents/" + id, Map.of(
                "firstName", "X", "lastName", "Y", "birthDate", "2018-03-01", "relationship", "CHILD"),
                other.token()).getResponse().getStatus()).isEqualTo(404);
        assertThat(api.delete("/api/v1/patients/me/dependents/" + id, other.token())
                .getResponse().getStatus()).isEqualTo(404);

        assertThat(api.delete("/api/v1/patients/me/dependents/" + id, parent.token())
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(api.body(api.get("/api/v1/patients/me/dependents", parent.token()))).isEmpty();
    }

    @Test
    void professionalsCannotUsePatientEndpoints() throws Exception {
        Session pro = api.registerAndLogin("PROFESSIONAL", "Pro");
        assertThat(api.get("/api/v1/patients/me", pro.token()).getResponse().getStatus()).isEqualTo(403);
    }
}
