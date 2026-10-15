package dev.jpzconception.allodocteur.professional;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Avec app.professionals.require-confirmed-license=true, l'approbation exige une confirmation manuelle. */
@TestPropertySource(properties = "app.professionals.require-confirmed-license=true")
class PublicApplicationGatingTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    @Test
    void approvalRequiresConfirmedLicense() throws Exception {
        ApiClient api = new ApiClient(mvc, json);
        var pub = new PublicApplicationSupport(api, json);
        String admin = api.login("admin@test.local", "AdminPassw0rd!").token();
        String id = api.body(pub.submit(pub.application(PublicApplicationSupport.email(), "CI-" + UUID.randomUUID()),
                List.of("LICENSE"), pub.pdf("a.pdf"))).get("reference").asText();

        var blocked = api.post("/api/v1/admin/professionals/" + id + "/approve", Map.of(), admin);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.body(blocked).get("code").asText()).isEqualTo("LICENSE_NOT_CONFIRMED");

        api.post("/api/v1/admin/professionals/" + id + "/license-check", Map.of("result", "CONFIRMED"), admin);
        assertThat(api.post("/api/v1/admin/professionals/" + id + "/approve", Map.of(), admin)
                .getResponse().getStatus()).isEqualTo(200);
    }
}
