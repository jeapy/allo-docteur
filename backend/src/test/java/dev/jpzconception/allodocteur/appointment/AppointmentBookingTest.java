package dev.jpzconception.allodocteur.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.Fixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class AppointmentBookingTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;

    ApiClient api;
    Fixtures fx;
    LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        day = LocalDate.now(ZoneOffset.UTC).plusDays(2); // toujours hors préavis, quelle que soit l'heure d'exécution
    }

    private JsonNode slots(Session viewer, String proId, String type) throws Exception {
        MvcResult r = api.get("/api/v1/professionals/" + proId + "/slots", viewer.token(),
                "type", type, "from", day.toString());
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return api.body(r);
    }

    private Map<String, Object> booking(String proId, String type, String reason, String startAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("professionalId", proId);
        m.put("type", type);
        m.put("reason", reason);
        m.put("startAt", startAt);
        return m;
    }

    @Test
    void slotsReflectAvailabilityAndModes() throws Exception {
        Session pro = fx.approvedProfessional("Slots" + UUID.randomUUID().toString().substring(0, 6));
        Session patient = fx.patient("Slots");

        assertThat(slots(patient, pro.userId(), "TELECONSULTATION_VIDEO")).hasSize(8); // 08:00-12:00 par pas de 30 min
        assertThat(slots(patient, pro.userId(), "HOME_VISIT")).hasSize(4); // pas de 60 min
        assertThat(slots(patient, pro.userId(), "TELECONSULTATION_VIDEO").get(0).get("startAt").asText())
                .startsWith(day + "T08:00");

        Session teleOnly = fx.approvedProfessional("TeleOnly", true, false);
        assertThat(api.get("/api/v1/professionals/" + teleOnly.userId() + "/slots", patient.token(),
                "type", "HOME_VISIT", "from", day.toString()).getResponse().getStatus()).isEqualTo(400);

        assertThat(api.get("/api/v1/professionals/" + pro.userId() + "/slots", patient.token(),
                "type", "CHAT", "from", day.toString(), "to", day.plusDays(30).toString())
                .getResponse().getStatus()).isEqualTo(400);
        assertThat(api.get("/api/v1/professionals/" + UUID.randomUUID() + "/slots", patient.token(),
                "type", "CHAT", "from", day.toString()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void bookingTakesTheSlotAndRejectsRivals() throws Exception {
        Session pro = fx.approvedProfessional("Book" + UUID.randomUUID().toString().substring(0, 6));
        Session alice = fx.patient("Alice");
        Session bob = fx.patient("Bob");
        String first = slots(alice, pro.userId(), "TELECONSULTATION_VIDEO").get(0).get("startAt").asText();

        Map<String, Object> req = booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", first);
        req.put("reasonDetails", "Toux depuis trois jours");
        MvcResult ok = api.post("/api/v1/appointments", req, alice.token());
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        JsonNode created = api.body(ok);
        assertThat(created.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(created.get("fee").asInt()).isEqualTo(5000);
        assertThat(created.get("currency").asText()).isEqualTo("XOF");
        assertThat(created.get("reasonDetails").asText()).isEqualTo("Toux depuis trois jours");
        assertThat(created.get("beneficiaryName").asText()).contains("Alice");

        // le créneau n'est plus proposé et ne peut plus être réservé
        assertThat(slots(bob, pro.userId(), "TELECONSULTATION_VIDEO")).hasSize(7);
        MvcResult rival = api.post("/api/v1/appointments", req, bob.token());
        assertThat(rival.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.body(rival).get("code").asText()).isEqualTo("SLOT_UNAVAILABLE");

        // une heure qui n'est pas un créneau est refusée
        String offGrid = Instant.parse(first).plusSeconds(300).toString();
        assertThat(api.post("/api/v1/appointments", booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", offGrid),
                bob.token()).getResponse().getStatus()).isEqualTo(409);

        // seuls les patients réservent
        assertThat(api.post("/api/v1/appointments", req, pro.token()).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void simultaneousBookingsOfTheSameSlotYieldExactlyOneWinner() throws Exception {
        Session pro = fx.approvedProfessional("Race" + UUID.randomUUID().toString().substring(0, 6));
        int contenders = 4;
        List<Session> patients = new java.util.ArrayList<>();
        for (int i = 0; i < contenders; i++) patients.add(fx.patient("Racer" + i));
        String slot = slots(patients.get(0), pro.userId(), "TELECONSULTATION_VIDEO").get(0).get("startAt").asText();

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new java.util.ArrayList<>();
        for (Session p : patients) {
            results.add(pool.submit(() -> {
                go.await();
                return api.post("/api/v1/appointments",
                        booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", slot), p.token())
                        .getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            if (status == 201) created++;
            if (status == 409) conflicts++;
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(contenders - 1);
    }

    @Test
    void annulationLibereLeCreneau() throws Exception {
        Session pro = fx.approvedProfessional("Free" + UUID.randomUUID().toString().substring(0, 6));
        Session alice = fx.patient("Alice");
        String first = slots(alice, pro.userId(), "TELECONSULTATION_VIDEO").get(0).get("startAt").asText();
        JsonNode appt = api.body(api.post("/api/v1/appointments",
                booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", first), alice.token()));

        MvcResult cancelled = api.post("/api/v1/appointments/" + appt.get("id").asText() + "/cancel", Map.of(), alice.token());
        assertThat(cancelled.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.body(cancelled).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(slots(alice, pro.userId(), "TELECONSULTATION_VIDEO")).hasSize(8);

        // un second rendez-vous peut reprendre le même créneau
        assertThat(api.post("/api/v1/appointments",
                booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", first), alice.token())
                .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void reasonAndAddressRules() throws Exception {
        Session pro = fx.approvedProfessional("Rules" + UUID.randomUUID().toString().substring(0, 6));
        Session alice = fx.patient("Alice");
        String teleSlot = slots(alice, pro.userId(), "TELECONSULTATION_VIDEO").get(0).get("startAt").asText();
        String homeSlot = slots(alice, pro.userId(), "HOME_VISIT").get(0).get("startAt").asText();

        MvcResult injectionRemote = api.post("/api/v1/appointments",
                booking(pro.userId(), "TELECONSULTATION_VIDEO", "INJECTION", teleSlot), alice.token());
        assertThat(injectionRemote.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.body(injectionRemote).get("code").asText()).isEqualTo("REASON_REQUIRES_HOME_VISIT");

        MvcResult noAddress = api.post("/api/v1/appointments",
                booking(pro.userId(), "HOME_VISIT", "INJECTION", homeSlot), alice.token());
        assertThat(noAddress.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.body(noAddress).get("code").asText()).isEqualTo("ADDRESS_REQUIRED");

        Map<String, Object> home = booking(pro.userId(), "HOME_VISIT", "INJECTION", homeSlot);
        home.put("address", "Cocody, rue des Jardins");
        home.put("latitude", 5.36);
        home.put("longitude", -3.98);
        MvcResult ok = api.post("/api/v1/appointments", home, alice.token());
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        assertThat(api.body(ok).get("fee").asInt()).isEqualTo(8000); // tarif de la visite à domicile

        Map<String, Object> halfCoords = booking(pro.userId(), "HOME_VISIT", "DRESSING",
                slots(alice, pro.userId(), "HOME_VISIT").get(0).get("startAt").asText());
        halfCoords.put("address", "Plateau");
        halfCoords.put("latitude", 5.3);
        assertThat(api.post("/api/v1/appointments", halfCoords, alice.token()).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void patientCannotBeInTwoPlacesAtOnce() throws Exception {
        Session pro1 = fx.approvedProfessional("Busy1" + UUID.randomUUID().toString().substring(0, 6));
        Session pro2 = fx.approvedProfessional("Busy2" + UUID.randomUUID().toString().substring(0, 6));
        Session alice = fx.patient("Alice");
        String slot = slots(alice, pro1.userId(), "TELECONSULTATION_VIDEO").get(0).get("startAt").asText();

        assertThat(api.post("/api/v1/appointments", booking(pro1.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", slot),
                alice.token()).getResponse().getStatus()).isEqualTo(201);
        MvcResult clash = api.post("/api/v1/appointments", booking(pro2.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", slot),
                alice.token());
        assertThat(clash.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.body(clash).get("code").asText()).isEqualTo("PATIENT_BUSY");
    }

    @Test
    void bookingForADependent() throws Exception {
        Session pro = fx.approvedProfessional("Dep" + UUID.randomUUID().toString().substring(0, 6));
        Session parent = fx.patient("Parent");
        Session stranger = fx.patient("Etranger");
        String childId = api.body(api.post("/api/v1/patients/me/dependents", Map.of(
                "firstName", "Léa", "lastName", "Parent", "birthDate", "2018-03-01", "relationship", "CHILD"),
                parent.token())).get("id").asText();
        List<JsonNode> all = new java.util.ArrayList<>();
        slots(parent, pro.userId(), "TELECONSULTATION_VIDEO").forEach(all::add);

        // le parent réserve d'abord pour lui-même
        String slot = all.get(0).get("startAt").asText();
        assertThat(api.post("/api/v1/appointments", booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", slot),
                parent.token()).getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> forChild = booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION",
                all.get(1).get("startAt").asText());
        forChild.put("dependentId", childId);
        MvcResult ok = api.post("/api/v1/appointments", forChild, parent.token());
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = api.body(ok);
        assertThat(body.get("beneficiaryName").asText()).isEqualTo("Léa Parent");
        assertThat(body.get("beneficiaryBirthDate").asText()).isEqualTo("2018-03-01");
        assertThat(body.get("patient").get("id").asText()).isEqualTo(parent.userId());

        // la personne à charge d'un autre compte est introuvable
        Map<String, Object> stolen = booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION",
                all.get(2).get("startAt").asText());
        stolen.put("dependentId", childId);
        assertThat(api.post("/api/v1/appointments", stolen, stranger.token()).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void visibilityAndListing() throws Exception {
        Session pro = fx.approvedProfessional("Vis" + UUID.randomUUID().toString().substring(0, 6));
        Session otherPro = fx.approvedProfessional("Other" + UUID.randomUUID().toString().substring(0, 6));
        Session alice = fx.patient("Alice");
        Session bob = fx.patient("Bob");
        String slot = slots(alice, pro.userId(), "TELECONSULTATION_VIDEO").get(0).get("startAt").asText();
        Map<String, Object> req = booking(pro.userId(), "TELECONSULTATION_VIDEO", "CONSULTATION", slot);
        req.put("reasonDetails", "Douleur thoracique légère");
        String id = api.body(api.post("/api/v1/appointments", req, alice.token())).get("id").asText();
        String url = "/api/v1/appointments/" + id;

        assertThat(api.get(url, alice.token()).getResponse().getStatus()).isEqualTo(200);
        assertThat(api.get(url, pro.token()).getResponse().getStatus()).isEqualTo(200);
        assertThat(api.get(url, bob.token()).getResponse().getStatus()).isEqualTo(404);
        assertThat(api.get(url, otherPro.token()).getResponse().getStatus()).isEqualTo(404);
        assertThat(api.get(url, null).getResponse().getStatus()).isEqualTo(401);

        // l'administrateur voit le rendez-vous mais pas les données de santé
        JsonNode adminView = api.body(api.get(url, fx.adminToken()));
        assertThat(adminView.get("id").asText()).isEqualTo(id);
        assertThat(adminView.has("reasonDetails")).isFalse();
        assertThat(adminView.has("beneficiaryBirthDate")).isFalse();

        assertThat(api.body(api.get("/api/v1/appointments", alice.token())).get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.body(api.get("/api/v1/appointments", pro.token())).get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.body(api.get("/api/v1/appointments", bob.token())).get("totalElements").asInt()).isZero();
        assertThat(api.body(api.get("/api/v1/appointments", alice.token(), "status", "CONFIRMED"))
                .get("totalElements").asInt()).isZero();
        assertThat(api.get("/api/v1/appointments", alice.token(), "sort", "nope").getResponse().getStatus()).isEqualTo(400);

        MvcResult adminList = api.get("/api/v1/admin/appointments", fx.adminToken(), "professionalId", pro.userId());
        assertThat(adminList.getResponse().getStatus()).isEqualTo(200);
        assertThat(api.body(adminList).get("totalElements").asInt()).isEqualTo(1);
        assertThat(api.get("/api/v1/admin/appointments", alice.token()).getResponse().getStatus()).isEqualTo(403);
    }
}
