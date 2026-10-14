package dev.jpzconception.allodocteur.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.auth.application.TotpService;
import dev.jpzconception.allodocteur.user.domain.AdminLevel;
import dev.jpzconception.allodocteur.user.domain.Role;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

/** Les administrateurs SUPER et FINANCE doivent activer la 2FA avant d'utiliser l'administration. */
@TestPropertySource(properties = "app.security.require-admin-mfa=true")
class AdminMfaEnforcementTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired TotpService totp;

    private ApiClient api() {
        return new ApiClient(mvc, json);
    }

    private String createAdmin(AdminLevel level) {
        User u = new User();
        u.setEmail("admin-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash(encoder.encode(ApiClient.PASSWORD));
        u.setRole(Role.ADMIN);
        u.setAdminLevel(level);
        u.setFirstName("Admin");
        u.setLastName(level.name());
        users.save(u);
        return u.getEmail();
    }

    @Test
    void financeAdminIsBlockedUntilTwoFactorIsEnabled() throws Exception {
        ApiClient api = api();
        String email = createAdmin(AdminLevel.FINANCE);
        String token = api.login(email, ApiClient.PASSWORD).token();

        var blocked = api.get("/api/v1/admin/payments/summary", token);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        assertThat(api.body(blocked).get("code").asText()).isEqualTo("MFA_ENROLLMENT_REQUIRED");

        // l'enrôlement reste possible : /auth/** n'est pas bloqué
        assertThat(api.get("/api/v1/auth/me", token).getResponse().getStatus()).isEqualTo(200);
        String secret = api.body(api.post("/api/v1/auth/2fa/setup", Map.of(), token)).get("secret").asText();
        var enable = api.post("/api/v1/auth/2fa/enable", Map.of("code", totp.code(secret, Instant.now())), token);
        assertThat(enable.getResponse().getStatus()).isEqualTo(200);

        // le jeton déjà émis ne porte pas encore la 2FA : il faut un nouveau jeton
        assertThat(api.get("/api/v1/admin/payments/summary", token).getResponse().getStatus()).isEqualTo(403);

        var step1 = api.body(api.post("/api/v1/auth/login", Map.of("email", email, "password", ApiClient.PASSWORD), null));
        assertThat(step1.get("status").asText()).isEqualTo("MFA_REQUIRED");
        var verified = api.body(api.post("/api/v1/auth/2fa/verify",
                Map.of("mfaToken", step1.get("mfaToken").asText(), "code", totp.code(secret, Instant.now())), null));
        String fresh = verified.get("accessToken").asText();
        assertThat(api.get("/api/v1/admin/payments/summary", fresh).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void supportAdminAndPatientAreNotConcerned() throws Exception {
        ApiClient api = api();
        String support = api.login(createAdmin(AdminLevel.SUPPORT), ApiClient.PASSWORD).token();
        assertThat(api.get("/api/v1/admin/users", support).getResponse().getStatus()).isEqualTo(200);

        String patient = api.registerAndLogin("PATIENT", "Koné").token();
        assertThat(api.get("/api/v1/auth/me", patient).getResponse().getStatus()).isEqualTo(200);
    }
}
