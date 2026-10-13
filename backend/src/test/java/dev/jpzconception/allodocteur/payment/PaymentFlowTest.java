package dev.jpzconception.allodocteur.payment;

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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Paiement des consultations : séquestre, commission, remboursement, portefeuille, retraits. */
@Import(ClockTestConfig.class)
@TestPropertySource(properties = "app.payments.require-payment-to-confirm=true")
class PaymentFlowTest extends AbstractIntegrationTest {

    static final String PHONE_OK = "+2250700000001";
    static final String PHONE_REFUSED = "+2250700000000"; // finit par « 0000 » : refusé par l'opérateur simulé
    static final String PHONE_SLOW = "+2250709999999";   // finit par « 9999 » : reste en attente

    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    ApiClient api;
    Fixtures fx;
    Session pro;
    Session patient;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        pro = fx.approvedProfessional("Caisse" + UUID.randomUUID().toString().substring(0, 6));
        patient = fx.patient("Payeur");
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // ------------------------------------------------------------------ outils

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String code(MvcResult r) throws Exception {
        return api.body(r).get("code").asText();
    }

    private MvcResult payAppointment(Session who, String appointmentId, String method, String reference) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("payableType", "APPOINTMENT");
        m.put("payableId", appointmentId);
        m.put("method", method);
        if (reference != null) m.put("payerReference", reference);
        return api.post("/api/v1/payments", m, who.token());
    }

    private double balance(Session s) throws Exception {
        return api.body(api.get("/api/v1/wallet", s.token())).get("balance").asDouble();
    }

    private double summary(String field) throws Exception {
        return api.body(api.get("/api/v1/admin/payments/summary", admin.token())).get(field).asDouble();
    }

    private Booking book() throws Exception {
        return fx.book(patient, pro, "CHAT", 0);
    }

    private void relogin() throws Exception {
        pro = api.login(pro.email(), ApiClient.PASSWORD);
        patient = api.login(patient.email(), ApiClient.PASSWORD);
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    /** Réserve, paie, fait confirmer, puis mène la consultation à son terme. */
    private Booking consultationDone() throws Exception {
        Booking b = book();
        assertThat(status(payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK))).isEqualTo(201);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);
        clock.travelTo(b.startAt().minusSeconds(60));
        relogin();
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/start", Map.of(), pro.token()))).isEqualTo(200);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/complete", Map.of(), pro.token()))).isEqualTo(200);
        return b;
    }

    // ------------------------------------------------------------------ séquestre et commission

    @Test
    void appointmentMustBePaidBeforeConfirmationAndFundsStayInEscrowUntilCompletion() throws Exception {
        Booking b = book();

        MvcResult early = api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token());
        assertThat(status(early)).isEqualTo(409);
        assertThat(code(early)).isEqualTo("PAYMENT_REQUIRED");

        double commissionsBefore = summary("commissions");
        MvcResult paid = payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK);
        assertThat(status(paid)).isEqualTo(201);
        JsonNode p = api.body(paid);
        assertThat(p.get("status").asText()).isEqualTo("CAPTURED");
        assertThat(p.get("amount").asDouble()).isEqualTo(5000.0);
        assertThat(p.get("commissionAmount").asDouble()).isEqualTo(500.0);
        assertThat(p.get("netAmount").asDouble()).isEqualTo(4500.0);
        assertThat(p.get("payeeId").asText()).isEqualTo(pro.userId());

        // pas de double paiement
        MvcResult again = payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK);
        assertThat(status(again)).isEqualTo(409);
        assertThat(code(again)).isEqualTo("ALREADY_PAID");

        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);
        assertThat(balance(pro)).isEqualTo(0.0); // toujours en séquestre

        clock.travelTo(b.startAt().minusSeconds(60));
        relogin();
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/start", Map.of(), pro.token()))).isEqualTo(200);
        assertThat(balance(pro)).isEqualTo(0.0);
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/complete", Map.of(), pro.token()))).isEqualTo(200);

        assertThat(balance(pro)).isEqualTo(4500.0);
        assertThat(summary("commissions") - commissionsBefore).isEqualTo(500.0);
        JsonNode after = api.body(api.get("/api/v1/payments/" + p.get("id").asText(), pro.token()));
        assertThat(after.get("status").asText()).isEqualTo("RELEASED");

        // le grand livre du professionnel trace le gain
        JsonNode tx = api.body(api.get("/api/v1/wallet/transactions", pro.token())).get("content");
        assertThat(tx).hasSize(1);
        assertThat(tx.get(0).get("reason").asText()).isEqualTo("EARNING");
        assertThat(tx.get(0).get("balanceAfter").asDouble()).isEqualTo(4500.0);
    }

    @Test
    void onlyThePatientOfAnAppointmentCanPayIt() throws Exception {
        Booking b = book();
        Session stranger = fx.patient("Etranger");
        assertThat(status(payAppointment(stranger, b.id(), "MOBILE_MONEY", PHONE_OK))).isEqualTo(404);
        assertThat(status(payAppointment(pro, b.id(), "MOBILE_MONEY", PHONE_OK))).isEqualTo(403);
        assertThat(status(payAppointment(patient, UUID.randomUUID().toString(), "MOBILE_MONEY", PHONE_OK))).isEqualTo(404);

        // le payeur et le professionnel voient le paiement, pas un tiers
        String id = api.body(payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK)).get("id").asText();
        assertThat(status(api.get("/api/v1/payments/" + id, patient.token()))).isEqualTo(200);
        assertThat(status(api.get("/api/v1/payments/" + id, pro.token()))).isEqualTo(200);
        assertThat(status(api.get("/api/v1/payments/" + id, stranger.token()))).isEqualTo(404);
    }

    @Test
    void cancellingARequestedAppointmentRefundsTheEscrow() throws Exception {
        Booking b = book();
        String id = api.body(payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK)).get("id").asText();

        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/cancel", Map.of("reason", "Empêchement"),
                patient.token()))).isEqualTo(200);

        assertThat(api.body(api.get("/api/v1/payments/" + id, patient.token())).get("status").asText()).isEqualTo("REFUNDED");
        assertThat(balance(pro)).isEqualTo(0.0);
    }

    @Test
    void adminCanRefundEscrowedFunds() throws Exception {
        Booking b = book();
        String id = api.body(payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK)).get("id").asText();

        assertThat(status(api.post("/api/v1/admin/payments/" + id + "/refund", Map.of("reason", " "), admin.token())))
                .isEqualTo(400);
        MvcResult ok = api.post("/api/v1/admin/payments/" + id + "/refund", Map.of("reason", "Litige"), admin.token());
        assertThat(status(ok)).isEqualTo(200);
        assertThat(api.body(ok).get("status").asText()).isEqualTo("REFUNDED");
        assertThat(code(api.post("/api/v1/admin/payments/" + id + "/refund", Map.of("reason", "Encore"), admin.token())))
                .isEqualTo("NOT_REFUNDABLE");
    }

    @Test
    void releasedFundsCannotBeRefundedAutomatically() throws Exception {
        consultationDone();
        String releasedId = api.body(api.get("/api/v1/payments", patient.token(), "status", "RELEASED"))
                .get("content").get(0).get("id").asText();
        MvcResult nope = api.post("/api/v1/admin/payments/" + releasedId + "/refund", Map.of("reason", "Trop tard"), admin.token());
        assertThat(status(nope)).isEqualTo(409);
        assertThat(code(nope)).isEqualTo("NOT_REFUNDABLE");
    }

    // ------------------------------------------------------------------ opérateurs

    @Test
    void aRefusedPaymentIsRecordedAsFailedAndCanBeRetried() throws Exception {
        Booking b = book();
        MvcResult refused = payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_REFUSED);
        assertThat(status(refused)).isEqualTo(201);
        assertThat(api.body(refused).get("status").asText()).isEqualTo("FAILED");
        assertThat(api.body(refused).get("failureReason").asText()).isNotBlank();

        MvcResult retry = payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK);
        assertThat(status(retry)).isEqualTo(201);
        assertThat(api.body(retry).get("status").asText()).isEqualTo("CAPTURED");
    }

    @Test
    void aSlowPaymentStaysPendingUntilTheOperatorConfirms() throws Exception {
        Booking b = book();
        MvcResult slow = payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_SLOW);
        assertThat(api.body(slow).get("status").asText()).isEqualTo("PENDING");
        String id = api.body(slow).get("id").asText();

        MvcResult second = payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK);
        assertThat(status(second)).isEqualTo(409);
        assertThat(code(second)).isEqualTo("PAYMENT_PENDING");
        assertThat(code(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token())))
                .isEqualTo("PAYMENT_REQUIRED");

        MvcResult refreshed = api.post("/api/v1/payments/" + id + "/refresh", Map.of(), patient.token());
        assertThat(status(refreshed)).isEqualTo(200);
        assertThat(api.body(refreshed).get("status").asText()).isEqualTo("CAPTURED");
        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/confirm", Map.of(), pro.token()))).isEqualTo(200);
    }

    @Test
    void invalidReferencesAreRejected() throws Exception {
        Booking b = book();
        assertThat(status(payAppointment(patient, b.id(), "MOBILE_MONEY", "abc"))).isEqualTo(400);
        assertThat(status(payAppointment(patient, b.id(), "MOBILE_MONEY", null))).isEqualTo(400);
        assertThat(status(payAppointment(patient, b.id(), "CARD", " "))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/payments", Map.of("payableType", "WALLET_TOPUP", "payableId", b.id(),
                "method", "MOBILE_MONEY", "payerReference", PHONE_OK), patient.token()))).isEqualTo(400);
        assertThat(status(payAppointment(patient, b.id(), "CARD", "tok_visa_4242"))).isEqualTo(201);
    }

    // ------------------------------------------------------------------ portefeuille

    @Test
    void topUpThenPayWithTheWalletAndGetRefundedIntoIt() throws Exception {
        MvcResult top = api.post("/api/v1/wallet/topups", Map.of("amount", 10000, "method", "MOBILE_MONEY",
                "payerReference", PHONE_OK), patient.token());
        assertThat(status(top)).isEqualTo(201);
        assertThat(balance(patient)).isEqualTo(10000.0);

        Booking b = book();
        MvcResult paid = payAppointment(patient, b.id(), "WALLET", null);
        assertThat(status(paid)).isEqualTo(201);
        assertThat(api.body(paid).get("status").asText()).isEqualTo("CAPTURED");
        assertThat(balance(patient)).isEqualTo(5000.0);

        assertThat(status(api.post("/api/v1/appointments/" + b.id() + "/cancel", Map.of("reason", "Annulé"), patient.token())))
                .isEqualTo(200);
        assertThat(balance(patient)).isEqualTo(10000.0);

        JsonNode tx = api.body(api.get("/api/v1/wallet/transactions", patient.token())).get("content");
        assertThat(tx).hasSize(3); // recharge, paiement, remboursement (plus récent d'abord)
        assertThat(tx.get(0).get("reason").asText()).isEqualTo("REFUND");
        assertThat(tx.get(1).get("reason").asText()).isEqualTo("PAYMENT");
        assertThat(tx.get(2).get("reason").asText()).isEqualTo("TOPUP");
    }

    @Test
    void walletPaymentWithInsufficientFundsLeavesNoTrace() throws Exception {
        Booking b = book();
        MvcResult r = payAppointment(patient, b.id(), "WALLET", null);
        assertThat(status(r)).isEqualTo(409);
        assertThat(code(r)).isEqualTo("INSUFFICIENT_FUNDS");
        // la tentative n'a pas bloqué le paiement suivant
        assertThat(status(payAppointment(patient, b.id(), "MOBILE_MONEY", PHONE_OK))).isEqualTo(201);
    }

    @Test
    void topUpAmountsAreBounded() throws Exception {
        assertThat(status(api.post("/api/v1/wallet/topups", Map.of("amount", 10, "method", "MOBILE_MONEY",
                "payerReference", PHONE_OK), patient.token()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/wallet/topups", Map.of("amount", 5000000, "method", "MOBILE_MONEY",
                "payerReference", PHONE_OK), patient.token()))).isEqualTo(400);
        assertThat(status(api.post("/api/v1/wallet/topups", Map.of("amount", 5000, "method", "WALLET"),
                patient.token()))).isEqualTo(400);
    }

    @Test
    void anIdempotencyKeyReplaysTheSamePayment() throws Exception {
        String key = UUID.randomUUID().toString();
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/wallet/topups")
                .contentType("application/json").header("Idempotency-Key", key)
                .content(json.writeValueAsString(Map.of("amount", 3000, "method", "MOBILE_MONEY", "payerReference", PHONE_OK)));
        String first = api.body(api.perform(req, patient.token())).get("id").asText();
        String second = api.body(api.perform(req, patient.token())).get("id").asText();
        assertThat(second).isEqualTo(first);
        assertThat(balance(patient)).isEqualTo(3000.0);
    }

    @Test
    void bankTransfersAreConfirmedByFinance() throws Exception {
        MvcResult top = api.post("/api/v1/wallet/topups", Map.of("amount", 20000, "method", "BANK_TRANSFER"), patient.token());
        assertThat(api.body(top).get("status").asText()).isEqualTo("PENDING");
        String id = api.body(top).get("id").asText();
        assertThat(balance(patient)).isEqualTo(0.0);

        assertThat(status(api.post("/api/v1/admin/payments/" + id + "/confirm-transfer", Map.of(), patient.token()))).isEqualTo(403);
        MvcResult ok = api.post("/api/v1/admin/payments/" + id + "/confirm-transfer", Map.of(), admin.token());
        assertThat(status(ok)).isEqualTo(200);
        assertThat(balance(patient)).isEqualTo(20000.0);
        assertThat(status(api.post("/api/v1/admin/payments/" + id + "/confirm-transfer", Map.of(), admin.token()))).isEqualTo(409);

        MvcResult second = api.post("/api/v1/wallet/topups", Map.of("amount", 6000, "method", "BANK_TRANSFER"), patient.token());
        String id2 = api.body(second).get("id").asText();
        MvcResult rej = api.post("/api/v1/admin/payments/" + id2 + "/reject-transfer", Map.of("reason", "Virement non reçu"), admin.token());
        assertThat(api.body(rej).get("status").asText()).isEqualTo("FAILED");
        assertThat(balance(patient)).isEqualTo(20000.0);
    }

    // ------------------------------------------------------------------ retraits

    @Test
    void aProfessionalWithdrawsEarningsThroughTheFinanceWorkflow() throws Exception {
        consultationDone(); // 4 500 de gains
        assertThat(balance(pro)).isEqualTo(4500.0);

        // règles de la demande
        assertThat(code(api.post("/api/v1/wallet/withdrawals", Map.of("amount", 500, "method", "MOBILE_MONEY",
                "destination", PHONE_OK), pro.token()))).isEqualTo("AMOUNT_BELOW_MINIMUM");
        assertThat(code(api.post("/api/v1/wallet/withdrawals", Map.of("amount", 9000, "method", "MOBILE_MONEY",
                "destination", PHONE_OK), pro.token()))).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(status(api.post("/api/v1/wallet/withdrawals", Map.of("amount", 2000, "method", "MOBILE_MONEY",
                "destination", PHONE_OK), patient.token()))).isEqualTo(403);

        // la demande réserve le montant
        MvcResult req = api.post("/api/v1/wallet/withdrawals", Map.of("amount", 2000, "method", "MOBILE_MONEY",
                "destination", PHONE_OK), pro.token());
        assertThat(status(req)).isEqualTo(201);
        JsonNode w = api.body(req);
        assertThat(w.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(w.get("destination").asText()).endsWith("0001").startsWith("*"); // masquée pour le professionnel
        assertThat(balance(pro)).isEqualTo(2500.0);
        String id = w.get("id").asText();

        // la finance voit la destination complète et rejette : le montant est restitué
        JsonNode listed = api.body(api.get("/api/v1/admin/withdrawals", admin.token(), "status", "REQUESTED")).get("content");
        assertThat(listed.findValuesAsText("id")).contains(id);
        assertThat(listed.findValuesAsText("destination")).contains(PHONE_OK);
        assertThat(status(api.post("/api/v1/admin/withdrawals/" + id + "/reject", Map.of("reason", "Numéro invalide"), admin.token())))
                .isEqualTo(200);
        assertThat(balance(pro)).isEqualTo(4500.0);
        assertThat(status(api.post("/api/v1/admin/withdrawals/" + id + "/pay", Map.of(), admin.token()))).isEqualTo(409);

        // nouvelle demande, payée cette fois
        String id2 = api.body(api.post("/api/v1/wallet/withdrawals", Map.of("amount", 2000, "method", "MOBILE_MONEY",
                "destination", PHONE_OK), pro.token())).get("id").asText();
        MvcResult paid = api.post("/api/v1/admin/withdrawals/" + id2 + "/pay", Map.of(), admin.token());
        assertThat(status(paid)).isEqualTo(200);
        assertThat(api.body(paid).get("status").asText()).isEqualTo("PAID");
        assertThat(balance(pro)).isEqualTo(2500.0);

        JsonNode mine = api.body(api.get("/api/v1/wallet/withdrawals", pro.token())).get("content");
        assertThat(mine).hasSize(2);
    }

    @Test
    void aFailedPayoutKeepsTheWithdrawalRequested() throws Exception {
        consultationDone();
        String id = api.body(api.post("/api/v1/wallet/withdrawals", Map.of("amount", 1500, "method", "MOBILE_MONEY",
                "destination", PHONE_REFUSED), pro.token())).get("id").asText();
        MvcResult r = api.post("/api/v1/admin/withdrawals/" + id + "/pay", Map.of(), admin.token());
        assertThat(status(r)).isEqualTo(409);
        assertThat(code(r)).isEqualTo("PAYOUT_FAILED");
        assertThat(api.body(api.get("/api/v1/wallet/withdrawals", pro.token())).get("content").get(0).get("status").asText())
                .isEqualTo("REQUESTED");
        assertThat(balance(pro)).isEqualTo(3000.0);
    }

    // ------------------------------------------------------------------ autorisations

    @Test
    void financeEndpointsAreAdminOnly() throws Exception {
        assertThat(status(api.get("/api/v1/admin/payments", patient.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/admin/payments/summary", pro.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/admin/withdrawals", pro.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/admin/payments", admin.token()))).isEqualTo(200);
        assertThat(status(api.get("/api/v1/wallet", admin.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/payments", null))).isIn(401, 403);
    }

    @Test
    void theLedgerIsAppendOnly() throws Exception {
        api.post("/api/v1/wallet/topups", Map.of("amount", 1000, "method", "MOBILE_MONEY", "payerReference", PHONE_OK),
                patient.token());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> jdbc.update("UPDATE wallet_transactions SET amount = amount + 1"))
                .hasMessageContaining("ajout seul");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("DELETE FROM wallet_transactions"))
                .hasMessageContaining("ajout seul");
    }

    @Test
    void dateFiltersAreAccepted() throws Exception {
        assertThat(status(api.get("/api/v1/admin/payments", admin.token(), "from",
                Instant.now().minusSeconds(3600).toString()))).isEqualTo(200);
    }
}
