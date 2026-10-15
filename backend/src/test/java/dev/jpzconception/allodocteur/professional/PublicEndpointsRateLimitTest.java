package dev.jpzconception.allodocteur.professional;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.rate-limit.public-per-minute=3")
class PublicEndpointsRateLimitTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    @Test
    void publicEndpointsAreThrottledPerIp() throws Exception {
        ApiClient api = new ApiClient(mvc, json);
        int ok = 0;
        int limited = 0;
        for (int i = 0; i < 6; i++) {
            int status = api.get("/api/v1/public/license-check", null, "typeId", "1", "number", "CI-" + i)
                    .getResponse().getStatus();
            if (status == 429) limited++; else ok++;
        }
        assertThat(ok).isEqualTo(3);
        assertThat(limited).isEqualTo(3);
    }
}
