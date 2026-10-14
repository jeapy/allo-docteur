package dev.jpzconception.allodocteur.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.notification.application.EmailSender;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Réinitialisation du mot de passe déclenchée par l'administration : lien envoyé au titulaire uniquement. */
class AdminPasswordResetTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @MockitoBean EmailSender email;

    ApiClient api;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    @Test
    void adminTriggersLinkAndOwnerCompletesTheReset() throws Exception {
        Session patient = api.registerAndLogin("PATIENT", "Oublieux");

        var res = api.post("/api/v1/admin/users/" + patient.userId() + "/password-reset", Map.of(), admin.token());
        assertThat(res.getResponse().getStatus()).isEqualTo(202);
        assertThat(res.getResponse().getContentAsString()).isEmpty(); // l'admin ne voit jamais le lien

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).send(eq(patient.email()), org.mockito.ArgumentMatchers.anyString(), body.capture());
        Matcher m = Pattern.compile("reset-password\\?token=(\\S+)")
                .matcher(String.join("\n", body.getAllValues()));
        assertThat(m.find()).isTrue();

        String newPassword = "NouveauMotDePasse1!";
        assertThat(api.post("/api/v1/auth/password/reset", Map.of("token", m.group(1), "newPassword", newPassword), null)
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(api.login(patient.email(), newPassword).token()).isNotBlank();
        // le lien ne sert qu'une fois
        assertThat(api.post("/api/v1/auth/password/reset", Map.of("token", m.group(1), "newPassword", newPassword), null)
                .getResponse().getStatus()).isEqualTo(400);

        var audit = api.body(api.get("/api/v1/admin/audit-logs", admin.token(),
                "action", "PASSWORD_RESET_TRIGGERED_BY_ADMIN", "targetId", patient.userId()));
        assertThat(audit.get("totalElements").asLong()).isEqualTo(1);
        assertThat(audit.get("content").get(0).get("actorEmail").asText()).isEqualTo("admin@test.local");
    }

    @Test
    void unknownUserAndNonAdminAreRefused() throws Exception {
        assertThat(api.post("/api/v1/admin/users/" + UUID.randomUUID() + "/password-reset", Map.of(), admin.token())
                .getResponse().getStatus()).isEqualTo(404);
        Session patient = api.registerAndLogin("PATIENT", "Curieux");
        Session other = api.registerAndLogin("PATIENT", "Cible");
        assertThat(api.post("/api/v1/admin/users/" + other.userId() + "/password-reset", Map.of(), patient.token())
                .getResponse().getStatus()).isEqualTo(403);
    }
}
