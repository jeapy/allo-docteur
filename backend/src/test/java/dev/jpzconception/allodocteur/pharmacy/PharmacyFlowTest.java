package dev.jpzconception.allodocteur.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.ClockTestConfig;
import dev.jpzconception.allodocteur.Fixtures;
import dev.jpzconception.allodocteur.Fixtures.Booking;
import dev.jpzconception.allodocteur.MutableClock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

@Import(ClockTestConfig.class)
class PharmacyFlowTest extends AbstractIntegrationTest {

    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    ApiClient api;
    Fixtures fx;
    Session doctor;
    Session patient;
    Session pharmacist;
    Session otherPharmacist;
    String pharmacyId;       // livre, de garde
    String otherPharmacyId;  // ne livre pas
    Booking booking;
    String adminToken;

    static final Map<String, Object> RX = Map.of("items", List.of(
            Map.of("medicationName", "Paracétamol 500 mg", "dosage", "1 comprimé", "quantity", 10),
            Map.of("medicationName", "Amoxicilline 1 g", "quantity", 14)));

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        doctor = fx.approvedProfessional("Prescripteur" + UUID.randomUUID().toString().substring(0, 6));
        patient = fx.patient("Client");
        pharmacist = fx.approvedProfessionalOfType("Pharma" + UUID.randomUUID().toString().substring(0, 6), "PHARMACIST");
        otherPharmacist = fx.approvedProfessionalOfType("Autre" + UUID.randomUUID().toString().substring(0, 6), "PHARMACIST");
        String admin = fx.adminToken();

        pharmacyId = createPharmacy(admin, "Pharmacie du Plateau", true, true, 1000);
        otherPharmacyId = createPharmacy(admin, "Pharmacie de la Gare", false, false, 0);
        assertThat(status(api.put("/api/v1/admin/pharmacies/" + pharmacyId + "/members/" + pharmacist.userId(), Map.of(), admin)))
                .isEqualTo(204);
        assertThat(status(api.put("/api/v1/admin/pharmacies/" + otherPharmacyId + "/members/" + otherPharmacist.userId(), Map.of(), admin)))
                .isEqualTo(204);

        booking = fx.book(patient, doctor, "CHAT", 0);
        assertThat(status(api.post("/api/v1/appointments/" + booking.id() + "/confirm", Map.of(), doctor.token()))).isEqualTo(200);
        accept();
        travel(booking.startAt().minusSeconds(60));
        assertThat(status(api.post("/api/v1/appointments/" + booking.id() + "/start", Map.of(), doctor.token()))).isEqualTo(200);
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String code(MvcResult r) throws Exception {
        return api.body(r).get("code").asText();
    }

    private String createPharmacy(String admin, String name, boolean delivery, boolean onDuty, int fee) throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("name", name + " " + UUID.randomUUID().toString().substring(0, 6));
        p.put("cityId", fx.cityId());
        p.put("address", "Rue des Jardins");
        p.put("phone", "+2250700000001");
        p.put("latitude", 5.36);
        p.put("longitude", -4.01);
        p.put("openingHours", "8h-20h");
        p.put("onDuty", onDuty);
        p.put("offersDelivery", delivery);
        if (delivery) p.put("deliveryFee", fee);
        MvcResult r = api.post("/api/v1/admin/pharmacies", p, admin);
        assertThat(status(r)).isEqualTo(201);
        return api.body(r).get("id").asText();
    }

    private void accept() throws Exception {
        String version = api.body(api.get("/api/v1/consents/current", patient.token())).get("version").asText();
        assertThat(status(api.post("/api/v1/consents", Map.of("version", version), patient.token()))).isEqualTo(200);
    }

    /** Les jetons dépendent de l'horloge : on reconnecte tout le monde après un déplacement. */
    private void travel(java.time.Instant target) throws Exception {
        clock.travelTo(target);
        doctor = api.login(doctor.email(), ApiClient.PASSWORD);
        patient = api.login(patient.email(), ApiClient.PASSWORD);
        pharmacist = api.login(pharmacist.email(), ApiClient.PASSWORD);
        otherPharmacist = api.login(otherPharmacist.email(), ApiClient.PASSWORD);
        adminToken = api.login("admin@test.local", "AdminPassw0rd!").token();
    }

    private String prescription() throws Exception {
        MvcResult r = api.post("/api/v1/appointments/" + booking.id() + "/prescriptions", RX, doctor.token());
        assertThat(status(r)).isEqualTo(201);
        return api.body(r).get("id").asText();
    }

    private Map<String, Object> pickup(String prescriptionId, String pharmacy) {
        Map<String, Object> o = new HashMap<>();
        o.put("prescriptionId", prescriptionId);
        o.put("pharmacyId", pharmacy);
        o.put("fulfillment", "PICKUP");
        o.put("contactPhone", "+2250700000002");
        return o;
    }

    private Map<String, Object> delivery(String prescriptionId) {
        Map<String, Object> o = pickup(prescriptionId, pharmacyId);
        o.put("fulfillment", "DELIVERY");
        o.put("deliveryAddress", "Cocody, rue des Jardins, villa 12");
        return o;
    }

    private JsonNode place(Map<String, Object> req) throws Exception {
        MvcResult r = api.post("/api/v1/pharmacy-orders", req, patient.token());
        assertThat(status(r)).isEqualTo(201);
        return api.body(r);
    }

    private JsonNode act(String orderId, String action, Object body, Session who) throws Exception {
        MvcResult r = api.post("/api/v1/pharmacy/orders/" + orderId + "/" + action, body, who.token());
        assertThat(status(r)).as(action).isEqualTo(200);
        return api.body(r);
    }

    // ------------------------------------------------------------------ retrait en officine

    @Test
    void pickupOrderGoesFromSentToCompleted() throws Exception {
        String rx = prescription();
        JsonNode order = place(pickup(rx, pharmacyId));
        String id = order.get("id").asText();
        assertThat(order.get("status").asText()).isEqualTo("SENT");
        assertThat(order.get("number").asText()).matches("CMD-\\d{4}-\\d{6}");
        assertThat(order.get("items")).hasSize(2);

        String rawName = jdbc.queryForObject("select medication_name from pharmacy_order_items where order_id = ?::uuid and line_number = 1",
                String.class, id);
        assertThat(rawName).startsWith("v1:").doesNotContain("Paracétamol");

        // liste du patient et de la pharmacie : aucun médicament
        MvcResult mine = api.get("/api/v1/pharmacy-orders", patient.token());
        assertThat(mine.getResponse().getContentAsString()).contains(id).doesNotContain("Paracétamol");
        MvcResult queue = api.get("/api/v1/pharmacy/orders", pharmacist.token());
        assertThat(queue.getResponse().getContentAsString()).contains(id).doesNotContain("Paracétamol");

        // seul un pharmacien de CETTE pharmacie accède au contenu
        assertThat(status(api.get("/api/v1/pharmacy/orders/" + id, otherPharmacist.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/pharmacy/orders/" + id, doctor.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/pharmacy/orders/" + id, patient.token()))).isEqualTo(403);
        MvcResult detail = api.get("/api/v1/pharmacy/orders/" + id, pharmacist.token());
        assertThat(status(detail)).isEqualTo(200);
        assertThat(api.body(detail).get("items").get(0).get("medicationName").asText()).isEqualTo("Paracétamol 500 mg");

        // cette consultation est tracée dans le journal d'accès du patient
        String recordId = api.body(api.get("/api/v1/prescriptions/" + rx, patient.token())).get("recordId").asText();
        List<String> actions = api.body(api.get("/api/v1/medical-records/" + recordId + "/access-log", patient.token()))
                .get("content").findValuesAsText("action");
        assertThat(actions).contains("VIEW_PHARMACY_ORDER");

        // acceptation partielle avec devis
        Map<String, Object> accept = Map.of("quotedTotal", 4500, "message", "Amoxicilline en rupture", "unavailableLines", List.of(2));
        JsonNode accepted = act(id, "accept", accept, pharmacist);
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("quotedTotal").asDouble()).isEqualTo(4500.0);
        assertThat(accepted.get("items").get(1).get("available").asBoolean()).isFalse();
        assertThat(accepted.get("items").get(0).get("available").asBoolean()).isTrue();

        assertThat(act(id, "ready", Map.of("message", "Prête au comptoir"), pharmacist).get("status").asText()).isEqualTo("READY");
        // plus d'annulation une fois prête
        assertThat(code(api.post("/api/v1/pharmacy-orders/" + id + "/cancel", Map.of(), patient.token()))).isEqualTo("INVALID_STATE");
        // la livraison n'a pas de sens pour un retrait
        assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/dispatch", Map.of(), pharmacist.token()))).isEqualTo(409);
        assertThat(act(id, "handover", Map.of(), pharmacist).get("status").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectedOrderFreesThePrescriptionAndAcceptingEverythingUnavailableIsRefused() throws Exception {
        String rx = prescription();
        String id = place(pickup(rx, pharmacyId)).get("id").asText();

        MvcResult dup = api.post("/api/v1/pharmacy-orders", pickup(rx, otherPharmacyId), patient.token());
        assertThat(status(dup)).isEqualTo(409);
        assertThat(code(dup)).isEqualTo("ORDER_ALREADY_ACTIVE");

        MvcResult all = api.post("/api/v1/pharmacy/orders/" + id + "/accept", Map.of("unavailableLines", List.of(1, 2)),
                pharmacist.token());
        assertThat(status(all)).isEqualTo(400);
        assertThat(code(all)).isEqualTo("ALL_UNAVAILABLE");
        assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/accept", Map.of("unavailableLines", List.of(9)),
                pharmacist.token()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/reject", Map.of("reason", " "), pharmacist.token()))).isEqualTo(400);

        JsonNode rejected = act(id, "reject", Map.of("reason", "Ordonnance illisible"), pharmacist);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("pharmacyMessage").asText()).isEqualTo("Ordonnance illisible");

        // l'ordonnance peut être renvoyée ailleurs
        assertThat(place(pickup(rx, otherPharmacyId)).get("status").asText()).isEqualTo("SENT");
    }

    @Test
    void patientCancelsBeforeReadyAndCanReorder() throws Exception {
        String rx = prescription();
        String id = place(pickup(rx, pharmacyId)).get("id").asText();
        MvcResult cancelled = api.post("/api/v1/pharmacy-orders/" + id + "/cancel", Map.of(), patient.token());
        assertThat(status(cancelled)).isEqualTo(200);
        assertThat(api.body(cancelled).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/accept", Map.of(), pharmacist.token()))).isEqualTo(409);
        place(pickup(rx, pharmacyId));
    }

    // ------------------------------------------------------------------ livraison

    @Test
    void deliveryRequiresAnAddressAndAPharmacyThatDelivers() throws Exception {
        String rx = prescription();
        Map<String, Object> noAddress = delivery(rx);
        noAddress.remove("deliveryAddress");
        MvcResult r1 = api.post("/api/v1/pharmacy-orders", noAddress, patient.token());
        assertThat(status(r1)).isEqualTo(400);
        assertThat(code(r1)).isEqualTo("ADDRESS_REQUIRED");

        Map<String, Object> noDelivery = delivery(rx);
        noDelivery.put("pharmacyId", otherPharmacyId);
        MvcResult r2 = api.post("/api/v1/pharmacy-orders", noDelivery, patient.token());
        assertThat(status(r2)).isEqualTo(400);
        assertThat(code(r2)).isEqualTo("DELIVERY_NOT_OFFERED");
    }

    @Test
    void deliveryIsClosedWithTheHandoverCodeOnlyThePatientKnows() throws Exception {
        String rx = prescription();
        JsonNode order = place(delivery(rx));
        String id = order.get("id").asText();
        assertThat(order.get("deliveryFee").asInt()).isEqualTo(1000);

        act(id, "accept", Map.of("quotedTotal", 6000), pharmacist);
        act(id, "ready", Map.of(), pharmacist);
        JsonNode dispatched = act(id, "dispatch", Map.of(), pharmacist);
        assertThat(dispatched.get("status").asText()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(dispatched.get("delivery").get("trackingCode").asText()).startsWith("FAKE-");
        // la pharmacie ne voit jamais le code
        assertThat(dispatched.get("delivery").path("handoverCode").isMissingNode()
                || dispatched.get("delivery").get("handoverCode").isNull()).isTrue();

        String handoverCode = api.body(api.get("/api/v1/pharmacy-orders/" + id, patient.token()))
                .get("delivery").get("handoverCode").asText();
        assertThat(handoverCode).matches("\\d{6}");
        String raw = jdbc.queryForObject("select handover_code from deliveries where order_id = ?::uuid", String.class, id);
        assertThat(raw).startsWith("v1:").doesNotContain(handoverCode);

        String wrong = handoverCode.equals("000000") ? "111111" : "000000";
        MvcResult bad = api.post("/api/v1/pharmacy/orders/" + id + "/deliver", Map.of("code", wrong), pharmacist.token());
        assertThat(status(bad)).isEqualTo(400);
        assertThat(code(bad)).isEqualTo("INVALID_HANDOVER_CODE");
        assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/deliver", Map.of("code", "12"), pharmacist.token()))).isEqualTo(400);

        JsonNode done = act(id, "deliver", Map.of("code", handoverCode), pharmacist);
        assertThat(done.get("status").asText()).isEqualTo("DELIVERED");
        // une fois livrée, le code n'est plus affiché
        JsonNode after = api.body(api.get("/api/v1/pharmacy-orders/" + id, patient.token()));
        assertThat(after.get("delivery").path("handoverCode").isMissingNode() || after.get("delivery").get("handoverCode").isNull()).isTrue();
    }

    @Test
    void wrongCodesAreCountedAndLockTheDelivery() throws Exception {
        String rx = prescription();
        String id = place(delivery(rx)).get("id").asText();
        act(id, "accept", Map.of(), pharmacist);
        act(id, "ready", Map.of(), pharmacist);
        act(id, "dispatch", Map.of(), pharmacist);
        String handoverCode = api.body(api.get("/api/v1/pharmacy-orders/" + id, patient.token()))
                .get("delivery").get("handoverCode").asText();
        String wrong = handoverCode.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/deliver", Map.of("code", wrong), pharmacist.token())))
                    .isEqualTo(400);
        }
        // même le bon code est refusé : les essais sont épuisés (et comptés malgré les erreurs)
        MvcResult locked = api.post("/api/v1/pharmacy/orders/" + id + "/deliver", Map.of("code", handoverCode), pharmacist.token());
        assertThat(status(locked)).isEqualTo(409);
        assertThat(code(locked)).isEqualTo("CODE_ATTEMPTS_EXCEEDED");

        assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/delivery-failed", Map.of("reason", " "), pharmacist.token())))
                .isEqualTo(400);
        JsonNode failed = act(id, "delivery-failed", Map.of("reason", "Patient injoignable"), pharmacist);
        assertThat(failed.get("status").asText()).isEqualTo("DELIVERY_FAILED");
        assertThat(failed.get("delivery").get("status").asText()).isEqualTo("FAILED");
    }

    // ------------------------------------------------------------------ autorisations

    @Test
    void prescriptionsBelongToTheirOwnerAndMustBeUsable() throws Exception {
        String rx = prescription();
        Session stranger = fx.patient("Etranger");
        stranger = api.login(stranger.email(), ApiClient.PASSWORD);
        MvcResult foreign = api.post("/api/v1/pharmacy-orders", pickup(rx, pharmacyId), stranger.token());
        assertThat(status(foreign)).isEqualTo(404);
        assertThat(code(foreign)).isEqualTo("PRESCRIPTION_NOT_FOUND");

        String cancelledRx = prescription();
        assertThat(status(api.post("/api/v1/prescriptions/" + cancelledRx + "/cancel", Map.of("reason", "Erreur"), doctor.token())))
                .isEqualTo(200);
        MvcResult unusable = api.post("/api/v1/pharmacy-orders", pickup(cancelledRx, pharmacyId), patient.token());
        assertThat(status(unusable)).isEqualTo(409);
        assertThat(code(unusable)).isEqualTo("PRESCRIPTION_NOT_USABLE");

        String id = place(pickup(rx, pharmacyId)).get("id").asText();
        assertThat(status(api.get("/api/v1/pharmacy-orders/" + id, stranger.token()))).isEqualTo(404);
        assertThat(status(api.post("/api/v1/pharmacy-orders/" + id + "/cancel", Map.of(), stranger.token()))).isEqualTo(404);
        assertThat(status(api.get("/api/v1/pharmacy-orders", doctor.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/pharmacy/orders", patient.token()))).isEqualTo(403);
        assertThat(status(api.post("/api/v1/pharmacy/orders/" + id + "/accept", Map.of(), otherPharmacist.token()))).isEqualTo(404);
    }

    // ------------------------------------------------------------------ pharmacies et administration

    @Test
    void searchHidesInactivePharmaciesAndAdminCanManageMembers() throws Exception {
        String deliveryOnly = api.get("/api/v1/pharmacies", patient.token(), "delivery", "true", "cityId",
                String.valueOf(fx.cityId()), "size", "100").getResponse().getContentAsString();
        assertThat(deliveryOnly).contains(pharmacyId).doesNotContain(otherPharmacyId);
        String onDuty = api.get("/api/v1/pharmacies", patient.token(), "onDuty", "true", "size", "100")
                .getResponse().getContentAsString();
        assertThat(onDuty).contains(pharmacyId).doesNotContain(otherPharmacyId);
        assertThat(api.get("/api/v1/pharmacies/nearby", patient.token(), "latitude", "5.36", "longitude", "-4.01",
                "limit", "20").getResponse().getContentAsString()).contains(pharmacyId);

        assertThat(status(api.post("/api/v1/admin/pharmacies/" + otherPharmacyId + "/deactivate", Map.of(), adminToken))).isEqualTo(200);
        assertThat(api.get("/api/v1/pharmacies", patient.token(), "size", "100").getResponse().getContentAsString())
                .doesNotContain(otherPharmacyId);
        assertThat(api.get("/api/v1/admin/pharmacies", adminToken, "size", "100").getResponse().getContentAsString())
                .contains(otherPharmacyId);
        String rx = prescription();
        assertThat(status(api.post("/api/v1/pharmacy-orders", pickup(rx, otherPharmacyId), patient.token()))).isEqualTo(404);

        // seuls les pharmaciens approuvés peuvent être rattachés
        MvcResult notPharmacist = api.put("/api/v1/admin/pharmacies/" + pharmacyId + "/members/" + doctor.userId(), Map.of(), adminToken);
        assertThat(status(notPharmacist)).isEqualTo(400);
        assertThat(code(notPharmacist)).isEqualTo("NOT_A_PHARMACIST");
        assertThat(api.get("/api/v1/admin/pharmacies/" + pharmacyId + "/members", adminToken).getResponse().getContentAsString())
                .contains(pharmacist.userId());
        assertThat(status(api.delete("/api/v1/admin/pharmacies/" + pharmacyId + "/members/" + pharmacist.userId(), adminToken)))
                .isEqualTo(204);
        assertThat(api.get("/api/v1/pharmacy/mine", pharmacist.token()).getResponse().getContentAsString()).doesNotContain(pharmacyId);

        assertThat(status(api.post("/api/v1/admin/pharmacies", Map.of("name", "X"), patient.token()))).isEqualTo(403);
        assertThat(status(api.post("/api/v1/admin/pharmacies", Map.of("name", "X"), adminToken))).isEqualTo(400);
    }
}
