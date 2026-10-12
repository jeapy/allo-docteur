package dev.jpzconception.allodocteur.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.auth.application.TotpService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Sup3rSecretPass!";

    @Autowired ObjectMapper json;
    @Autowired TotpService totp;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    private MvcResult call(String url, Object body, Cookie cookie, String... headers) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (cookie != null) req.cookie(cookie);
        for (int i = 0; i + 1 < headers.length; i += 2) req.header(headers[i], headers[i + 1]);
        return mvc.perform(req).andReturn();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private void register(String email, String role) throws Exception {
        var res = call("/api/v1/auth/register", java.util.Map.of(
                "email", email, "password", PASSWORD, "firstName", "Awa", "lastName", "Koné", "role", role), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(201);
    }

    private MvcResult login(String email, String password) throws Exception {
        return call("/api/v1/auth/login", java.util.Map.of("email", email, "password", password), null);
    }

    @Test
    void registerLoginAndMe() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");

        MvcResult login = login(email, PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        JsonNode b = body(login);
        assertThat(b.get("status").asText()).isEqualTo("AUTHENTICATED");
        assertThat(b.has("refreshToken")).isFalse(); // web : cookie seulement
        Cookie cookie = login.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + b.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        var res = call("/api/v1/auth/register", java.util.Map.of(
                "email", email.toUpperCase(), "password", PASSWORD, "firstName", "A", "lastName", "B", "role", "PATIENT"), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(res).get("code").asText()).isEqualTo("EMAIL_ALREADY_USED");
    }

    @Test
    void adminRoleCannotBeRequestedAtRegistration() throws Exception {
        var res = call("/api/v1/auth/register", java.util.Map.of(
                "email", uniqueEmail(), "password", PASSWORD, "firstName", "A", "lastName", "B", "role", "ADMIN"), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void wrongPasswordIsUnauthorizedWithProblemDetail() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        MvcResult res = login(email, "mauvais-mot-de-passe");
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(res).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void accountLocksAfterFiveFailures() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        for (int i = 0; i < 5; i++) login(email, "mauvais-mot-de-passe");
        MvcResult res = login(email, PASSWORD);
        assertThat(res.getResponse().getStatus()).isEqualTo(423);
    }

    @Test
    void refreshRotatesAndReuseRevokesFamily() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        Cookie first = login(email, PASSWORD).getResponse().getCookie("refresh_token");

        MvcResult refreshed = call("/api/v1/auth/refresh", java.util.Map.of(), first);
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        Cookie second = refreshed.getResponse().getCookie("refresh_token");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // rejouer l'ancien jeton => 401 et toute la famille est révoquée
        MvcResult reuse = call("/api/v1/auth/refresh", java.util.Map.of(), first);
        assertThat(reuse.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(reuse).get("code").asText()).isEqualTo("REFRESH_TOKEN_REUSED");
        MvcResult afterReuse = call("/api/v1/auth/refresh", java.util.Map.of(), second);
        assertThat(afterReuse.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void mobileClientGetsRefreshTokenInBody() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        MvcResult res = call("/api/v1/auth/login", java.util.Map.of("email", email, "password", PASSWORD), null,
                "X-Client-Type", "mobile");
        JsonNode b = body(res);
        assertThat(b.get("refreshToken").asText()).isNotBlank();
        assertThat(res.getResponse().getCookie("refresh_token")).isNull();

        MvcResult refreshed = call("/api/v1/auth/refresh", java.util.Map.of("refreshToken", b.get("refreshToken").asText()),
                null, "X-Client-Type", "mobile");
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(refreshed).get("refreshToken").asText()).isNotEqualTo(b.get("refreshToken").asText());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        Cookie cookie = login(email, PASSWORD).getResponse().getCookie("refresh_token");
        assertThat(call("/api/v1/auth/logout", java.util.Map.of(), cookie).getResponse().getStatus()).isEqualTo(204);
        assertThat(call("/api/v1/auth/refresh", java.util.Map.of(), cookie).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void twoFactorFlowWithRecoveryCode() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        String access = body(login(email, PASSWORD)).get("accessToken").asText();

        MvcResult setup = mvc.perform(post("/api/v1/auth/2fa/setup").header("Authorization", "Bearer " + access)).andReturn();
        String secret = body(setup).get("secret").asText();

        // un mauvais code n'active rien
        MvcResult bad = mvc.perform(post("/api/v1/auth/2fa/enable").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}")).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);

        MvcResult enable = mvc.perform(post("/api/v1/auth/2fa/enable").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + totp.code(secret, Instant.now()) + "\"}")).andReturn();
        assertThat(enable.getResponse().getStatus()).isEqualTo(200);
        String recovery = body(enable).get("recoveryCodes").get(0).asText();

        // login => étape 2FA
        JsonNode step1 = body(login(email, PASSWORD));
        assertThat(step1.get("status").asText()).isEqualTo("MFA_REQUIRED");
        assertThat(step1.has("accessToken")).isFalse();
        String mfaToken = step1.get("mfaToken").asText();

        // le jeton MFA ne donne pas accès à l'API
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + mfaToken)).andExpect(status().isUnauthorized());

        MvcResult wrong = call("/api/v1/auth/2fa/verify", java.util.Map.of("mfaToken", mfaToken, "code", "000000"), null);
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);

        MvcResult ok = call("/api/v1/auth/2fa/verify",
                java.util.Map.of("mfaToken", mfaToken, "code", totp.code(secret, Instant.now())), null);
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(ok).get("status").asText()).isEqualTo("AUTHENTICATED");

        // code de secours : valable une seule fois
        String mfa2 = body(login(email, PASSWORD)).get("mfaToken").asText();
        assertThat(call("/api/v1/auth/2fa/verify", java.util.Map.of("mfaToken", mfa2, "code", recovery), null)
                .getResponse().getStatus()).isEqualTo(200);
        String mfa3 = body(login(email, PASSWORD)).get("mfaToken").asText();
        assertThat(call("/api/v1/auth/2fa/verify", java.util.Map.of("mfaToken", mfa3, "code", recovery), null)
                .getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void passwordResetFlow() throws Exception {
        String email = uniqueEmail();
        register(email, "PATIENT");
        // l'adresse inconnue reçoit la même réponse
        assertThat(call("/api/v1/auth/password/forgot", java.util.Map.of("email", "nobody@test.local"), null)
                .getResponse().getStatus()).isEqualTo(202);
        assertThat(call("/api/v1/auth/password/forgot", java.util.Map.of("email", email), null)
                .getResponse().getStatus()).isEqualTo(202);
        // un jeton invalide est refusé
        assertThat(call("/api/v1/auth/password/reset",
                java.util.Map.of("token", "invalide", "newPassword", "NouveauMotDePasse1!"), null)
                .getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminUserManagementAndAuthorization() throws Exception {
        String patientEmail = uniqueEmail();
        register(patientEmail, "PATIENT");
        String patientToken = body(login(patientEmail, PASSWORD)).get("accessToken").asText();

        // un patient ne peut pas lister les utilisateurs
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String adminToken = body(login("admin@test.local", "AdminPassw0rd!")).get("accessToken").asText();
        MvcResult list = mvc.perform(get("/api/v1/admin/users").param("q", patientEmail)
                        .param("sort", "createdAt,desc").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        String userId = body(list).get("content").get(0).get("id").asText();

        mvc.perform(get("/api/v1/admin/users").param("sort", "passwordHash,asc")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/admin/users/" + userId + "/status").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // un compte suspendu ne peut plus se connecter
        assertThat(login(patientEmail, PASSWORD).getResponse().getStatus()).isEqualTo(403);
    }
}
