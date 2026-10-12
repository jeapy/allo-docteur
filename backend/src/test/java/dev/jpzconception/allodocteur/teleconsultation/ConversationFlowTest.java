package dev.jpzconception.allodocteur.teleconsultation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.ClockTestConfig;
import dev.jpzconception.allodocteur.Fixtures;
import dev.jpzconception.allodocteur.Fixtures.Booking;
import dev.jpzconception.allodocteur.MutableClock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

@Import(ClockTestConfig.class)
class ConversationFlowTest extends AbstractIntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    ApiClient api;
    Fixtures fx;
    Session pro;
    Session patient;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        pro = fx.approvedProfessional("Conv" + UUID.randomUUID().toString().substring(0, 6));
        patient = fx.patient("Conv");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private void confirm(Booking b) throws Exception {
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);
    }

    private MvcResult upload(Session who, Booking b, byte[] content, String name) throws Exception {
        var file = new MockMultipartFile("file", name, "application/octet-stream", content);
        return api.perform(multipart("/api/v1/appointments/" + b.id() + "/documents").file(file)
                .param("caption", "Photo de la lésion"), who.token());
    }

    @Test
    void messagingBetweenParticipants() throws Exception {
        Booking b = fx.book(patient, pro, "CHAT", 0);
        String url = "/api/v1/appointments/" + b.id() + "/messages";

        // rien n'est possible tant que le professionnel n'a pas confirmé
        MvcResult closed = api.post(url, Map.of("content", "Bonjour"), patient.token());
        assertThat(status(closed)).isEqualTo(409);
        assertThat(api.body(closed).get("code").asText()).isEqualTo("CONVERSATION_CLOSED");

        confirm(b);
        MvcResult first = api.post(url, Map.of("content", "  Bonjour docteur  "), patient.token());
        assertThat(status(first)).isEqualTo(201);
        JsonNode firstBody = api.body(first);
        assertThat(firstBody.get("content").asText()).isEqualTo("Bonjour docteur");
        assertThat(firstBody.get("senderRole").asText()).isEqualTo("PATIENT");
        Thread.sleep(5); // sentAt distincts pour tester le curseur
        assertThat(status(api.post(url, Map.of("content", "Bonjour, je vous écoute"), pro.token()))).isEqualTo(201);

        JsonNode all = api.body(api.get(url, patient.token()));
        assertThat(all).hasSize(2);
        assertThat(all.get(0).get("content").asText()).isEqualTo("Bonjour docteur");
        assertThat(all.get(1).get("senderRole").asText()).isEqualTo("PROFESSIONAL");

        // curseur de polling : seulement ce qui suit le dernier message connu
        JsonNode newer = api.body(api.get(url, pro.token(), "since", all.get(0).get("sentAt").asText()));
        assertThat(newer).hasSize(1);

        // lecture : le professionnel a 1 message non lu, puis 0
        String base = "/api/v1/appointments/" + b.id() + "/messages";
        assertThat(api.body(api.get(base + "/unread-count", pro.token())).get("count").asInt()).isEqualTo(1);
        assertThat(api.body(api.post(base + "/read", Map.of(), pro.token())).get("count").asInt()).isEqualTo(1);
        assertThat(api.body(api.get(base + "/unread-count", pro.token())).get("count").asInt()).isZero();
        assertThat(api.body(api.get(base + "/unread-count", patient.token())).get("count").asInt()).isEqualTo(1);

        // le contenu est chiffré en base
        String stored = jdbc.queryForObject("select content from messages where id = ?::uuid", String.class,
                firstBody.get("id").asText());
        assertThat(stored).startsWith("v1:").doesNotContain("Bonjour");

        // étrangers et administrateurs n'y ont pas accès ; contenu vide refusé
        Session stranger = fx.patient("Intrus");
        assertThat(status(api.get(url, stranger.token()))).isEqualTo(404);
        assertThat(status(api.post(url, Map.of("content", "coucou"), stranger.token()))).isEqualTo(404);
        assertThat(status(api.get(url, fx.adminToken()))).isEqualTo(403);
        assertThat(status(api.post(url, Map.of("content", "   "), patient.token()))).isEqualTo(400);
    }

    @Test
    void videoSessionOnlyWhileTheAppointmentIsInProgress() throws Exception {
        Booking b = fx.book(patient, pro, "TELECONSULTATION_VIDEO", 0);
        String base = "/api/v1/appointments/" + b.id();
        confirm(b);

        MvcResult notYet = api.post(base + "/session", Map.of(), patient.token());
        assertThat(status(notYet)).isEqualTo(409);
        assertThat(api.body(notYet).get("code").asText()).isEqualTo("SESSION_NOT_ACTIVE");

        clock.travelTo(b.startAt().minusSeconds(60));
        Session proNow = api.login(pro.email(), ApiClient.PASSWORD);
        Session patientNow = api.login(patient.email(), ApiClient.PASSWORD);
        assertThat(status(api.post(base + "/start", Map.of(), proNow.token()))).isEqualTo(200);

        MvcResult forPatient = api.post(base + "/session", Map.of(), patientNow.token());
        assertThat(status(forPatient)).isEqualTo(200);
        JsonNode s = api.body(forPatient);
        assertThat(s.get("media").asText()).isEqualTo("VIDEO");
        assertThat(s.get("token").asText()).isNotBlank();
        assertThat(s.get("joinUrl").asText()).contains(s.get("roomId").asText());

        JsonNode forPro = api.body(api.post(base + "/session", Map.of(), proNow.token()));
        assertThat(forPro.get("roomId").asText()).isEqualTo(s.get("roomId").asText());
        assertThat(forPro.get("token").asText()).isNotEqualTo(s.get("token").asText()); // jeton personnel

        Session stranger = fx.patient("Intrus");
        assertThat(status(api.post(base + "/session", Map.of(), stranger.token()))).isEqualTo(404);

        assertThat(status(api.post(base + "/complete", Map.of(), proNow.token()))).isEqualTo(200);
        assertThat(status(api.post(base + "/session", Map.of(), patientNow.token()))).isEqualTo(409);
        Long ended = jdbc.queryForObject(
                "select count(*) from teleconsultation_sessions where appointment_id = ?::uuid and ended_at is not null",
                Long.class, b.id());
        assertThat(ended).isEqualTo(1L);
    }

    @Test
    void chatAppointmentsHaveNoMediaSession() throws Exception {
        Booking b = fx.book(patient, pro, "CHAT", 1);
        String base = "/api/v1/appointments/" + b.id();
        confirm(b);
        clock.travelTo(b.startAt().minusSeconds(60));
        Session proNow = api.login(pro.email(), ApiClient.PASSWORD);
        Session patientNow = api.login(patient.email(), ApiClient.PASSWORD);
        assertThat(status(api.post(base + "/start", Map.of(), proNow.token()))).isEqualTo(200);
        MvcResult r = api.post(base + "/session", Map.of(), patientNow.token());
        assertThat(status(r)).isEqualTo(409);
        assertThat(api.body(r).get("code").asText()).isEqualTo("NO_MEDIA_SESSION");
    }

    @Test
    void sharedDocuments() throws Exception {
        Booking b = fx.book(patient, pro, "TELECONSULTATION_VIDEO", 2);
        String base = "/api/v1/appointments/" + b.id() + "/documents";

        assertThat(status(upload(patient, b, PNG, "lesion.png"))).isEqualTo(409); // pas encore confirmé
        confirm(b);

        MvcResult sent = upload(patient, b, PNG, "../lesion.png");
        assertThat(status(sent)).isEqualTo(201);
        JsonNode doc = api.body(sent);
        assertThat(doc.get("kind").asText()).isEqualTo("IMAGE");
        assertThat(doc.get("contentType").asText()).isEqualTo("image/png");
        assertThat(doc.get("filename").asText()).isEqualTo("lesion.png");
        assertThat(doc.get("caption").asText()).isEqualTo("Photo de la lésion");

        // un fichier qui n'est pas une image ou un PDF est refusé, quel que soit son nom
        assertThat(status(upload(patient, b, "<html></html>".getBytes(), "x.png"))).isEqualTo(400);
        assertThat(status(upload(pro, b, "%PDF-1.4 ordonnance".getBytes(), "avis.pdf"))).isEqualTo(201);

        // les deux participants voient la liste et téléchargent
        assertThat(api.body(api.get(base, pro.token()))).hasSize(2);
        MvcResult content = api.get(base + "/" + doc.get("id").asText() + "/content", pro.token());
        assertThat(status(content)).isEqualTo(200);
        assertThat(content.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(content.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");

        // étrangers : 404 ; admins : 403 ; suppression réservée à l'auteur
        Session stranger = fx.patient("Intrus");
        assertThat(status(api.get(base, stranger.token()))).isEqualTo(404);
        assertThat(status(api.get(base + "/" + doc.get("id").asText() + "/content", stranger.token()))).isEqualTo(404);
        assertThat(status(api.get(base, fx.adminToken()))).isEqualTo(403);
        assertThat(status(api.delete(base + "/" + doc.get("id").asText(), pro.token()))).isEqualTo(403);
        assertThat(status(api.delete(base + "/" + doc.get("id").asText(), patient.token()))).isEqualTo(204);
        assertThat(api.body(api.get(base, patient.token()))).hasSize(1);
    }

    @Test
    void conversationStaysOpenAWeekAfterTheAppointment() throws Exception {
        Booking b = fx.book(patient, pro, "TELECONSULTATION_AUDIO", 3);
        String base = "/api/v1/appointments/" + b.id();
        confirm(b);

        clock.travelTo(b.startAt().minusSeconds(60));
        Session proNow = api.login(pro.email(), ApiClient.PASSWORD);
        assertThat(status(api.post(base + "/start", Map.of(), proNow.token()))).isEqualTo(200);
        assertThat(api.body(api.post(base + "/session", Map.of(), proNow.token())).get("media").asText()).isEqualTo("AUDIO");
        assertThat(status(api.post(base + "/complete", Map.of(), proNow.token()))).isEqualTo(200);

        clock.travelTo(b.startAt().plus(Duration.ofDays(3)));
        Session patientDay3 = api.login(patient.email(), ApiClient.PASSWORD);
        assertThat(status(api.post(base + "/messages", Map.of("content", "J'ai une question sur l'ordonnance"),
                patientDay3.token()))).isEqualTo(201);

        clock.travelTo(b.startAt().plus(Duration.ofDays(9)));
        Session patientDay9 = api.login(patient.email(), ApiClient.PASSWORD);
        MvcResult tooLate = api.post(base + "/messages", Map.of("content", "Encore une question"), patientDay9.token());
        assertThat(status(tooLate)).isEqualTo(409);
        assertThat(api.body(tooLate).get("code").asText()).isEqualTo("CONVERSATION_CLOSED");
        assertThat(api.body(api.get(base + "/messages", patientDay9.token()))).hasSize(1); // lecture toujours possible
    }
}
