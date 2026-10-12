package dev.jpzconception.allodocteur.professional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class ProfessionalFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;
    String adminToken;
    int generalPractitionerId;
    int specialtyId;
    int abidjanId;
    int cocodyId;
    int yopougonId;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        adminToken = api.login("admin@test.local", "AdminPassw0rd!").token();
        Session patient = api.registerAndLogin("PATIENT", "Ref");
        for (JsonNode t : api.body(api.get("/api/v1/reference/professional-types", patient.token()))) {
            if (t.get("code").asText().equals("GENERAL_PRACTITIONER")) generalPractitionerId = t.get("id").asInt();
        }
        specialtyId = api.body(api.get("/api/v1/reference/specialties", patient.token())).get(0).get("id").asInt();
        for (JsonNode c : api.body(api.get("/api/v1/reference/cities", patient.token()))) {
            if (c.get("name").asText().equals("Abidjan")) {
                abidjanId = c.get("id").asInt();
                for (JsonNode d : c.get("districts")) {
                    if (d.get("name").asText().equals("Cocody")) cocodyId = d.get("id").asInt();
                    if (d.get("name").asText().equals("Yopougon")) yopougonId = d.get("id").asInt();
                }
            }
        }
        assertThat(generalPractitionerId).isPositive();
        assertThat(cocodyId).isPositive();
    }

    private Map<String, Object> profile(String license) {
        return Map.ofEntries(
                Map.entry("professionalTypeId", generalPractitionerId),
                Map.entry("qualification", "Docteur en médecine"),
                Map.entry("licenseNumber", license),
                Map.entry("yearsExperience", 8),
                Map.entry("bio", "Médecin généraliste"),
                Map.entry("cityId", abidjanId),
                Map.entry("districtId", cocodyId),
                Map.entry("consultationFee", 5000),
                Map.entry("homeVisitFee", 8000),
                Map.entry("offersTeleconsultation", true),
                Map.entry("offersHomeVisit", true),
                Map.entry("specialtyIds", List.of(specialtyId)),
                Map.entry("coverageZones", List.of(Map.of("cityId", abidjanId, "districtId", yopougonId))));
    }

    private void uploadPdf(String token) throws Exception {
        var file = new MockMultipartFile("file", "ordre.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        MvcResult r = api.perform(multipart("/api/v1/professionals/me/documents").file(file).param("type", "LICENSE"), token);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
    }

    private int searchTotal(String token, String... params) throws Exception {
        MvcResult r = api.get("/api/v1/professionals", token, params);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return api.body(r).get("totalElements").asInt();
    }

    @Test
    void fullValidationAndSearchWorkflow() throws Exception {
        String lastName = "Zz" + UUID.randomUUID().toString().substring(0, 8);
        Session pro = api.registerAndLogin("PROFESSIONAL", lastName);
        Session patient = api.registerAndLogin("PATIENT", "Patient");
        String license = "CI-" + UUID.randomUUID();

        // création du profil : en attente, absent du répertoire
        MvcResult saved = api.put("/api/v1/professionals/me", profile(license), pro.token());
        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.body(saved).get("verificationStatus").asText()).isEqualTo("PENDING");
        assertThat(searchTotal(patient.token(), "q", lastName)).isZero();
        assertThat(api.get("/api/v1/professionals/" + pro.userId(), patient.token()).getResponse().getStatus()).isEqualTo(404);

        // disponibilités : le chevauchement est refusé
        var monday = Map.of("dayOfWeek", 1, "startTime", "08:00", "endTime", "12:00", "teleconsultation", true, "homeVisit", false);
        var overlap = Map.of("dayOfWeek", 1, "startTime", "11:00", "endTime", "14:00", "teleconsultation", true, "homeVisit", false);
        assertThat(api.put("/api/v1/professionals/me/availability/rules", Map.of("rules", List.of(monday, overlap)), pro.token())
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(api.put("/api/v1/professionals/me/availability/rules", Map.of("rules", List.of(monday)), pro.token())
                .getResponse().getStatus()).isEqualTo(200);

        // validation impossible sans justificatif
        MvcResult noDoc = api.post("/api/v1/admin/professionals/" + pro.userId() + "/approve", Map.of(), adminToken);
        assertThat(noDoc.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.body(noDoc).get("code").asText()).isEqualTo("DOCUMENTS_REQUIRED");

        // un fichier non PDF/image est refusé, un PDF est accepté
        var html = new MockMultipartFile("file", "x.pdf", "application/pdf", "<html></html>".getBytes());
        assertThat(api.perform(multipart("/api/v1/professionals/me/documents").file(html).param("type", "LICENSE"), pro.token())
                .getResponse().getStatus()).isEqualTo(400);
        uploadPdf(pro.token());

        // un patient ne peut ni utiliser l'espace professionnel ni valider
        assertThat(api.get("/api/v1/professionals/me", patient.token()).getResponse().getStatus()).isEqualTo(403);
        assertThat(api.post("/api/v1/admin/professionals/" + pro.userId() + "/approve", Map.of(), patient.token())
                .getResponse().getStatus()).isEqualTo(403);

        // le justificatif n'est téléchargeable que par son propriétaire et les admins
        String docId = api.body(api.get("/api/v1/professionals/me/documents", pro.token())).get(0).get("id").asText();
        String contentUrl = "/api/v1/professionals/documents/" + docId + "/content";
        assertThat(api.get(contentUrl, patient.token()).getResponse().getStatus()).isEqualTo(404);
        MvcResult own = api.get(contentUrl, pro.token());
        assertThat(own.getResponse().getStatus()).isEqualTo(200);
        assertThat(own.getResponse().getContentAsString()).startsWith("%PDF");
        assertThat(api.get(contentUrl, adminToken).getResponse().getStatus()).isEqualTo(200);

        // l'admin voit la demande en attente puis l'approuve
        MvcResult pending = api.get("/api/v1/admin/professionals", adminToken, "status", "PENDING", "q", lastName);
        assertThat(api.body(pending).get("totalElements").asInt()).isEqualTo(1);
        MvcResult approved = api.post("/api/v1/admin/professionals/" + pro.userId() + "/approve", Map.of(), adminToken);
        assertThat(approved.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.body(approved).get("verificationStatus").asText()).isEqualTo("APPROVED");

        // recherche avancée
        String q = lastName;
        assertThat(searchTotal(patient.token(), "q", q)).isEqualTo(1);
        assertThat(searchTotal(patient.token(), "q", q, "cityId", "" + abidjanId, "districtId", "" + cocodyId)).isEqualTo(1);
        // Yopougon : couvert via la zone d'intervention
        assertThat(searchTotal(patient.token(), "q", q, "districtId", "" + yopougonId)).isEqualTo(1);
        assertThat(searchTotal(patient.token(), "q", q, "specialtyId", "" + specialtyId)).isEqualTo(1);
        assertThat(searchTotal(patient.token(), "q", q, "teleconsultation", "true", "day", "1")).isEqualTo(1);
        assertThat(searchTotal(patient.token(), "q", q, "day", "2")).isZero();
        assertThat(searchTotal(patient.token(), "q", q, "homeVisit", "true", "day", "1")).isZero(); // lundi : téléconsultation seule
        assertThat(searchTotal(patient.token(), "q", q, "maxFee", "1000")).isZero();
        assertThat(searchTotal(patient.token(), "q", q, "minFee", "4000", "maxFee", "6000")).isEqualTo(1);
        assertThat(searchTotal(patient.token(), "q", q, "homeVisit", "true", "maxFee", "7000")).isZero(); // tarif visite = 8000
        assertThat(searchTotal(patient.token(), "q", q, "minRating", "4")).isZero();
        assertThat(api.get("/api/v1/professionals", patient.token(), "sort", "nope").getResponse().getStatus()).isEqualTo(400);

        // absence d'une journée entière : exclu pour cette date seulement
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        MvcResult exc = api.post("/api/v1/professionals/me/availability/exceptions",
                Map.of("date", nextMonday.toString(), "reason", "Congés"), pro.token());
        assertThat(exc.getResponse().getStatus()).isEqualTo(201);
        assertThat(searchTotal(patient.token(), "q", q, "date", nextMonday.toString())).isZero();
        assertThat(searchTotal(patient.token(), "q", q, "date", nextMonday.plusWeeks(1).toString())).isEqualTo(1);

        // la fiche publique masque les champs privés
        JsonNode publicView = api.body(api.get("/api/v1/professionals/" + pro.userId(), patient.token()));
        assertThat(publicView.has("email")).isFalse();
        assertThat(publicView.has("verificationStatus")).isFalse();
        assertThat(publicView.has("documents")).isFalse();
        assertThat(publicView.get("availability").size()).isEqualTo(1);

        // modifier le numéro d'ordre repasse le profil en attente (et le retire du répertoire)
        MvcResult changed = api.put("/api/v1/professionals/me", profile("CI-NEW-" + UUID.randomUUID()), pro.token());
        assertThat(api.body(changed).get("verificationStatus").asText()).isEqualTo("PENDING");
        assertThat(searchTotal(patient.token(), "q", q)).isZero();

        // ré-approbation puis suspension
        api.post("/api/v1/admin/professionals/" + pro.userId() + "/approve", Map.of(), adminToken);
        assertThat(searchTotal(patient.token(), "q", q)).isEqualTo(1);
        assertThat(api.post("/api/v1/admin/professionals/" + pro.userId() + "/suspend", Map.of(), adminToken)
                .getResponse().getStatus()).isEqualTo(400); // motif obligatoire
        MvcResult suspended = api.post("/api/v1/admin/professionals/" + pro.userId() + "/suspend",
                Map.of("reason", "Signalement en cours d'examen"), adminToken);
        assertThat(api.body(suspended).get("verificationStatus").asText()).isEqualTo("SUSPENDED");
        assertThat(searchTotal(patient.token(), "q", q)).isZero();
    }

    @Test
    void rejectionRequiresReasonAndAllowsResubmission() throws Exception {
        Session pro = api.registerAndLogin("PROFESSIONAL", "Rejet" + UUID.randomUUID().toString().substring(0, 6));
        api.put("/api/v1/professionals/me", profile("CI-" + UUID.randomUUID()), pro.token());
        uploadPdf(pro.token());

        assertThat(api.post("/api/v1/admin/professionals/" + pro.userId() + "/reject", Map.of(), adminToken)
                .getResponse().getStatus()).isEqualTo(400);
        MvcResult rejected = api.post("/api/v1/admin/professionals/" + pro.userId() + "/reject",
                Map.of("reason", "Justificatif illisible"), adminToken);
        JsonNode b = api.body(rejected);
        assertThat(b.get("verificationStatus").asText()).isEqualTo("REJECTED");
        assertThat(b.get("rejectionReason").asText()).isEqualTo("Justificatif illisible");

        // un rejet ne peut pas être suivi d'une suspension
        assertThat(api.post("/api/v1/admin/professionals/" + pro.userId() + "/suspend", Map.of("reason", "x"), adminToken)
                .getResponse().getStatus()).isEqualTo(409);

        // le professionnel corrige son profil : retour en attente
        MvcResult resubmitted = api.put("/api/v1/professionals/me", profile(b.get("licenseNumber").asText()), pro.token());
        assertThat(api.body(resubmitted).get("verificationStatus").asText()).isEqualTo("PENDING");
    }

    @Test
    void licenseNumberMustBeUnique() throws Exception {
        String license = "CI-DUP-" + UUID.randomUUID();
        Session a = api.registerAndLogin("PROFESSIONAL", "A");
        Session b = api.registerAndLogin("PROFESSIONAL", "B");
        assertThat(api.put("/api/v1/professionals/me", profile(license), a.token()).getResponse().getStatus()).isEqualTo(200);
        MvcResult dup = api.put("/api/v1/professionals/me", profile(license.toLowerCase()), b.token());
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.body(dup).get("code").asText()).isEqualTo("LICENSE_ALREADY_USED");
    }

    @Test
    void profileValidation() throws Exception {
        Session pro = api.registerAndLogin("PROFESSIONAL", "Val");
        var bad = new java.util.HashMap<>(profile("CI-" + UUID.randomUUID()));
        bad.put("districtId", yopougonId); // OK : appartient à Abidjan
        bad.put("offersHomeVisit", false);
        bad.put("offersTeleconsultation", false);
        assertThat(api.put("/api/v1/professionals/me", bad, pro.token()).getResponse().getStatus()).isEqualTo(400);

        var noFee = new java.util.HashMap<>(profile("CI-" + UUID.randomUUID()));
        noFee.remove("consultationFee");
        assertThat(api.body(api.put("/api/v1/professionals/me", noFee, pro.token())).get("code").asText()).isEqualTo("FEE_REQUIRED");
    }
}
