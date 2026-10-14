package dev.jpzconception.allodocteur.admin;

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

/** Journal d'audit général : réservé au niveau SUPER, filtrable, avec l'e-mail de l'acteur. */
class AdminAuditApiTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    @Test
    void superAdminSeesActionsWithActorEmailAndCanFilter() throws Exception {
        Session patient = api.registerAndLogin("PATIENT", "Audit");
        var patch = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/admin/users/" + patient.userId() + "/status")
                .contentType("application/json").content("{\"status\":\"SUSPENDED\"}");
        assertThat(api.perform(patch, admin.token()).getResponse().getStatus()).isEqualTo(200);

        JsonNode page = api.body(api.get("/api/v1/admin/audit-logs", admin.token(),
                "action", "USER_STATUS_CHANGED", "targetId", patient.userId()));
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode first = page.get("content").get(0);
        assertThat(first.get("actorEmail").asText()).isEqualTo("admin@test.local");
        assertThat(first.get("targetType").asText()).isEqualTo("USER");

        JsonNode none = api.body(api.get("/api/v1/admin/audit-logs", admin.token(), "action", "NOPE_NEVER"));
        assertThat(none.get("totalElements").asLong()).isZero();

        JsonNode actions = api.body(api.get("/api/v1/admin/audit-logs/actions", admin.token()));
        assertThat(actions.toString()).contains("USER_STATUS_CHANGED");
    }

    @Test
    void nonSuperAndNonAdminAreRefused() throws Exception {
        Session patient = api.registerAndLogin("PATIENT", "Nosy");
        assertThat(api.get("/api/v1/admin/audit-logs", patient.token()).getResponse().getStatus()).isEqualTo(403);
        assertThat(api.get("/api/v1/admin/audit-logs", null).getResponse().getStatus()).isEqualTo(401);
    }
}
