package dev.jpzconception.allodocteur.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.Fixtures;
import dev.jpzconception.allodocteur.Fixtures.Booking;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Réclamations : dépôt, confidentialité, traitement par l'administration et notification du déposant. */
class ComplaintFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;
    Fixtures fx;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private JsonNode file(Session who, String subject, String appointmentId) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("subject", subject);
        m.put("description", "Description détaillée du problème rencontré.");
        if (appointmentId != null) m.put("appointmentId", appointmentId);
        MvcResult r = api.post("/api/v1/complaints", m, who.token());
        assertThat(status(r)).isEqualTo(201);
        return api.body(r);
    }

    @Test
    void patientAndProfessionalCanFileAndOnlyReadTheirOwn() throws Exception {
        Session patient = fx.patient("Plaignant");
        Session other = fx.patient("Autre");
        Session pro = fx.approvedProfessional("Pro" + UUID.randomUUID().toString().substring(0, 6));

        JsonNode c = file(patient, "Retard du professionnel", null);
        assertThat(c.at("/summary/number").asText()).matches("CLM-\\d{4}-\\d{6}");
        assertThat(c.at("/summary/status").asText()).isEqualTo("OPEN");
        assertThat(c.at("/summary/authorRole").asText()).isEqualTo("PATIENT");
        String id = c.at("/summary/id").asText();

        JsonNode proComplaint = file(pro, "Paiement non reçu", null);
        assertThat(proComplaint.at("/summary/authorRole").asText()).isEqualTo("PROFESSIONAL");

        assertThat(status(api.get("/api/v1/complaints/" + id, other.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/complaints/" + id, patient.token()))).isEqualTo(200);
        assertThat(status(api.post("/api/v1/complaints/" + id + "/messages", Map.of("body", "x"), other.token()))).isEqualTo(404);
        assertThat(api.body(api.get("/api/v1/complaints/mine", patient.token())).get("totalElements").asInt()).isEqualTo(1);

        assertThat(status(api.post("/api/v1/complaints", Map.of("subject", "", "description", "x"), patient.token()))).isEqualTo(400);
        assertThat(status(api.get("/api/v1/admin/complaints", patient.token()))).isEqualTo(403);
        assertThat(status(api.post("/api/v1/complaints", Map.of("subject", "a", "description", "b"), admin.token()))).isEqualTo(403);
    }

    @Test
    void anAppointmentLinkMustConcernTheAuthor() throws Exception {
        Session pro = fx.approvedProfessional("Lien" + UUID.randomUUID().toString().substring(0, 6));
        Session patient = fx.patient("Lie");
        Session stranger = fx.patient("Intrus");
        Booking b = fx.book(patient, pro, "TELECONSULTATION_VIDEO", 0);

        assertThat(file(patient, "À propos du rendez-vous", b.id()).at("/summary/appointmentId").asText()).isEqualTo(b.id());
        Map<String, Object> bad = Map.of("subject", "Sujet", "description", "Texte", "appointmentId", b.id());
        assertThat(status(api.post("/api/v1/complaints", bad, stranger.token()))).isEqualTo(404);
    }

    @Test
    void adminWorksTheComplaintThroughItsStatuses() throws Exception {
        Session patient = fx.patient("Suivi");
        String id = file(patient, "Facturation", null).at("/summary/id").asText();
        String admin = this.admin.token();

        assertThat(api.body(api.get("/api/v1/admin/complaints/summary", admin)).get("OPEN").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(api.body(api.get("/api/v1/admin/complaints", admin, "status", "OPEN")).get("totalElements").asInt())
                .isGreaterThanOrEqualTo(1);

        JsonNode taken = api.body(api.post("/api/v1/admin/complaints/" + id + "/take", Map.of(), admin));
        assertThat(taken.at("/summary/status").asText()).isEqualTo("IN_REVIEW");
        assertThat(status(api.post("/api/v1/admin/complaints/" + id + "/take", Map.of(), admin))).isEqualTo(409);

        JsonNode replied = api.body(api.post("/api/v1/admin/complaints/" + id + "/messages",
                Map.of("body", "Nous examinons votre dossier."), admin));
        assertThat(replied.get("messages").size()).isEqualTo(1);
        assertThat(replied.at("/messages/0/fromAdmin").asBoolean()).isTrue();

        // le patient voit la réponse et peut écrire à son tour
        JsonNode mine = api.body(api.get("/api/v1/complaints/" + id, patient.token()));
        assertThat(mine.at("/messages/0/body").asText()).isEqualTo("Nous examinons votre dossier.");
        JsonNode followUp = api.body(api.post("/api/v1/complaints/" + id + "/messages",
                Map.of("body", "Merci, voici un complément."), patient.token()));
        assertThat(followUp.get("messages").size()).isEqualTo(2);
        assertThat(followUp.at("/messages/1/fromAdmin").asBoolean()).isFalse();

        // le patient est notifié de la prise en charge et de la réponse
        JsonNode notes = api.body(api.get("/api/v1/notifications", patient.token()));
        assertThat(notes.get("content").findValuesAsText("type")).contains("COMPLAINT_UPDATE");
        assertThat(api.body(api.get("/api/v1/notifications/unread-count", patient.token())).get("count").asInt())
                .isGreaterThanOrEqualTo(2);

        assertThat(status(api.post("/api/v1/admin/complaints/" + id + "/resolve", Map.of(), admin))).isEqualTo(400);
        JsonNode resolved = api.body(api.post("/api/v1/admin/complaints/" + id + "/resolve",
                Map.of("text", "Remboursement effectué."), admin));
        assertThat(resolved.at("/summary/status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.get("resolution").asText()).isEqualTo("Remboursement effectué.");
        assertThat(resolved.at("/summary/resolvedAt").isMissingNode()).isFalse();

        // une réclamation clôturée n'accepte plus rien
        assertThat(status(api.post("/api/v1/complaints/" + id + "/messages", Map.of("body", "x"), patient.token()))).isEqualTo(409);
        assertThat(status(api.post("/api/v1/admin/complaints/" + id + "/reject", Map.of("text", "x"), admin))).isEqualTo(409);
        assertThat(status(api.post("/api/v1/admin/complaints/" + id + "/messages", Map.of("body", "x"), admin))).isEqualTo(409);
    }

    @Test
    void rejectionRecordsTheReason() throws Exception {
        Session patient = fx.patient("Rejet");
        String id = file(patient, "Hors périmètre", null).at("/summary/id").asText();
        JsonNode rejected = api.body(api.post("/api/v1/admin/complaints/" + id + "/reject",
                Map.of("text", "Hors du périmètre de la plateforme."), admin.token()));
        assertThat(rejected.at("/summary/status").asText()).isEqualTo("REJECTED");
        assertThat(api.body(api.get("/api/v1/complaints/" + id, patient.token())).get("resolution").asText())
                .contains("périmètre");
    }
}
