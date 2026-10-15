package dev.jpzconception.allodocteur.professional;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class PublicProfessionalApplicationTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;
    PublicApplicationSupport pub;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        pub = new PublicApplicationSupport(api, json);
        adminToken = api.login("admin@test.local", "AdminPassw0rd!").token();
    }

    @Test
    void referenceDataIsReadableWithoutAuthentication() throws Exception {
        assertThat(pub.typeId).isPositive();
        assertThat(pub.cityId).isPositive();
        // les référentiels d'administration, eux, restent protégés
        assertThat(api.get("/api/v1/admin/reference/professional-types", null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void submissionCreatesPendingProfileThatAdminCanReview() throws Exception {
        String email = PublicApplicationSupport.email();
        String license = "CI-" + UUID.randomUUID();

        MvcResult r = pub.submit(pub.application(email, license), List.of("LICENSE", "DIPLOMA"),
                pub.pdf("ordre.pdf"), pub.pdf("diplome.pdf"));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        var receipt = api.body(r);
        assertThat(receipt.get("status").asText()).isEqualTo("PENDING");
        assertThat(receipt.get("email").asText()).isEqualTo(email);
        String id = receipt.get("reference").asText();

        // le compte est utilisable (login) mais le profil n'est pas encore au répertoire
        var session = api.login(email, ApiClient.PASSWORD);
        assertThat(api.get("/api/v1/professionals/" + id, session.token()).getResponse().getStatus()).isEqualTo(404);
        assertThat(api.body(api.get("/api/v1/professionals/me/documents", session.token()))).hasSize(2);

        // l'admin le voit avec le statut de vérification automatique (format seul, pas de registre)
        var detail = api.body(api.get("/api/v1/admin/professionals/" + id, adminToken));
        assertThat(detail.get("verificationStatus").asText()).isEqualTo("PENDING");
        assertThat(detail.get("licenseCheck").get("status").asText()).isEqualTo("FORMAT_VALID");
        assertThat(api.post("/api/v1/admin/professionals/" + id + "/approve", Map.of(), adminToken)
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void submissionIsAtomicWhenSomethingIsInvalid() throws Exception {
        String email = PublicApplicationSupport.email();
        String license = "CI-" + UUID.randomUUID();

        // pas de justificatif de type LICENSE
        assertThat(code(pub.submit(pub.application(email, license), List.of("DIPLOMA"), pub.pdf("d.pdf"))))
                .isEqualTo("LICENSE_DOCUMENT_REQUIRED");
        // fichier qui n'est pas un PDF/image malgré l'extension
        var fake = new MockMultipartFile("files", "x.pdf", "application/pdf", "<html></html>".getBytes());
        assertThat(code(pub.submit(pub.application(email, license), List.of("LICENSE"), fake)))
                .isEqualTo("UNSUPPORTED_FILE");
        // aucun fichier / types incohérents
        assertThat(pub.submit(pub.application(email, license), List.of("LICENSE")).getResponse().getStatus())
                .isGreaterThanOrEqualTo(400);
        assertThat(code(pub.submit(pub.application(email, license), List.of("LICENSE", "OTHER"), pub.pdf("a.pdf"))))
                .isEqualTo("DOCUMENT_TYPES_MISMATCH");
        // format du numéro d'ordre invalide
        assertThat(code(pub.submit(pub.application(email, "!!"), List.of("LICENSE"), pub.pdf("a.pdf"))))
                .isEqualTo("INVALID_LICENSE_FORMAT");

        // aucun compte n'a été créé par ces échecs : la même adresse peut candidater ensuite
        assertThat(pub.submit(pub.application(email, license), List.of("LICENSE"), pub.pdf("a.pdf"))
                .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void duplicateLicenseOrEmailIsRejectedAndRollsBack() throws Exception {
        String license = "CI-DUP-" + UUID.randomUUID();
        String first = PublicApplicationSupport.email();
        assertThat(pub.submit(pub.application(first, license), List.of("LICENSE"), pub.pdf("a.pdf"))
                .getResponse().getStatus()).isEqualTo(201);

        String second = PublicApplicationSupport.email();
        // même numéro (casse différente) : refusé, et le compte du second candidat n'est pas créé
        assertThat(code(pub.submit(pub.application(second, license.toLowerCase()), List.of("LICENSE"), pub.pdf("a.pdf"))))
                .isEqualTo("LICENSE_ALREADY_USED");
        assertThat(api.post("/api/v1/auth/login", Map.of("email", second, "password", ApiClient.PASSWORD), null)
                .getResponse().getStatus()).isEqualTo(401);
        // même e-mail
        assertThat(code(pub.submit(pub.application(first, "CI-" + UUID.randomUUID()), List.of("LICENSE"), pub.pdf("a.pdf"))))
                .isEqualTo("EMAIL_ALREADY_USED");
    }

    @Test
    void termsMustBeAcceptedAndPhoneIsRequired() throws Exception {
        var app = new java.util.HashMap<>(pub.application(PublicApplicationSupport.email(), "CI-" + UUID.randomUUID()));
        app.put("acceptTerms", false);
        assertThat(pub.submit(app, List.of("LICENSE"), pub.pdf("a.pdf")).getResponse().getStatus()).isEqualTo(400);

        var noPhone = new java.util.HashMap<>(pub.application(PublicApplicationSupport.email(), "CI-" + UUID.randomUUID()));
        noPhone.put("account", Map.of("email", PublicApplicationSupport.email(), "password", ApiClient.PASSWORD,
                "firstName", "A", "lastName", "B"));
        assertThat(pub.submit(noPhone, List.of("LICENSE"), pub.pdf("a.pdf")).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void licenseCheckReportsFormatAndExistingRegistration() throws Exception {
        String license = "CI-CHK-" + UUID.randomUUID();
        String type = "" + pub.typeId;

        assertThat(status(api.get("/api/v1/public/license-check", null, "typeId", type, "number", license)))
                .isEqualTo("FORMAT_VALID");
        assertThat(status(api.get("/api/v1/public/license-check", null, "typeId", type, "number", "!!")))
                .isEqualTo("INVALID_FORMAT");
        assertThat(api.get("/api/v1/public/license-check", null, "typeId", "99999", "number", license)
                .getResponse().getStatus()).isEqualTo(400);

        pub.submit(pub.application(PublicApplicationSupport.email(), license), List.of("LICENSE"), pub.pdf("a.pdf"));
        assertThat(status(api.get("/api/v1/public/license-check", null, "typeId", type, "number", license)))
                .isEqualTo("ALREADY_REGISTERED");
    }

    @Test
    void adminCanConfirmOrFlagLicenseAndNonAdminsCannot() throws Exception {
        String email = PublicApplicationSupport.email();
        String id = api.body(pub.submit(pub.application(email, "CI-" + UUID.randomUUID()), List.of("LICENSE"),
                pub.pdf("a.pdf"))).get("reference").asText();
        String url = "/api/v1/admin/professionals/" + id + "/license-check";

        var pro = api.login(email, ApiClient.PASSWORD);
        assertThat(api.post(url, Map.of("result", "CONFIRMED"), pro.token()).getResponse().getStatus()).isEqualTo(403);
        assertThat(api.post(url, Map.of("result", "FORMAT_VALID"), adminToken).getResponse().getStatus()).isEqualTo(400);

        MvcResult notFound = api.post(url, Map.of("result", "NOT_FOUND", "note", "Absent du tableau de l'ordre"), adminToken);
        assertThat(api.body(notFound).get("licenseCheck").get("status").asText()).isEqualTo("NOT_FOUND");
        MvcResult blocked = api.post("/api/v1/admin/professionals/" + id + "/approve", Map.of(), adminToken);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        assertThat(code(blocked)).isEqualTo("LICENSE_NOT_FOUND");

        MvcResult confirmed = api.post(url, Map.of("result", "CONFIRMED"), adminToken);
        assertThat(api.body(confirmed).get("licenseCheck").get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(api.post("/api/v1/admin/professionals/" + id + "/approve", Map.of(), adminToken)
                .getResponse().getStatus()).isEqualTo(200);
    }

    private String code(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        return api.body(r).get("code").asText();
    }

    private String status(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return api.body(r).get("status").asText();
    }
}
