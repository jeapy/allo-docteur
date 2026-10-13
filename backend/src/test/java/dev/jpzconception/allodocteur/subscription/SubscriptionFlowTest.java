package dev.jpzconception.allodocteur.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jpzconception.allodocteur.AbstractIntegrationTest;
import dev.jpzconception.allodocteur.ApiClient;
import dev.jpzconception.allodocteur.ApiClient.Session;
import dev.jpzconception.allodocteur.Fixtures;
import dev.jpzconception.allodocteur.Fixtures.Booking;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Abonnements professionnels et Premium patient, et leur effet sur la commission. */
@TestPropertySource(properties = "app.payments.require-payment-to-confirm=true")
class SubscriptionFlowTest extends AbstractIntegrationTest {

    static final String PHONE = "+2250700000002";

    @Autowired ObjectMapper json;

    ApiClient api;
    Fixtures fx;
    Session pro;
    Session patient;
    Session admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        fx = new Fixtures(api);
        pro = fx.approvedProfessional("Abonne" + UUID.randomUUID().toString().substring(0, 6));
        patient = fx.patient("Premium");
        admin = api.login("admin@test.local", "AdminPassw0rd!");
    }

    private int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private String code(MvcResult r) throws Exception {
        return api.body(r).get("code").asText();
    }

    private MvcResult buy(Session who, String plan) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("planCode", plan);
        m.put("method", "MOBILE_MONEY");
        m.put("payerReference", PHONE);
        return api.post("/api/v1/subscriptions", m, who.token());
    }

    private JsonNode current(Session who) throws Exception {
        MvcResult r = api.get("/api/v1/subscriptions/current", who.token());
        assertThat(status(r)).isEqualTo(200);
        return api.body(r);
    }

    @Test
    void eachProfileSeesItsOwnPlans() throws Exception {
        JsonNode proPlans = api.body(api.get("/api/v1/subscriptions/plans", pro.token()));
        assertThat(proPlans.findValuesAsText("code")).containsExactly("PRO_STANDARD", "PRO_PREMIUM");
        JsonNode patientPlans = api.body(api.get("/api/v1/subscriptions/plans", patient.token()));
        assertThat(patientPlans.findValuesAsText("code")).containsExactly("PATIENT_PREMIUM");
        assertThat(status(api.get("/api/v1/subscriptions/plans", admin.token()))).isEqualTo(403);
        assertThat(status(api.get("/api/v1/subscriptions/current", patient.token()))).isEqualTo(204);
    }

    @Test
    void aProfessionalGetsOneFreeTrial() throws Exception {
        MvcResult t = api.post("/api/v1/subscriptions/trial", Map.of(), pro.token());
        assertThat(status(t)).isEqualTo(201);
        JsonNode s = api.body(t);
        assertThat(s.get("status").asText()).isEqualTo("TRIAL");
        assertThat(s.get("planCode").asText()).isEqualTo("PRO_STANDARD");
        long days = Duration.between(Instant.parse(s.get("startsAt").asText()), Instant.parse(s.get("endsAt").asText())).toDays();
        assertThat(days).isEqualTo(30);

        assertThat(code(api.post("/api/v1/subscriptions/trial", Map.of(), pro.token()))).isEqualTo("TRIAL_NOT_AVAILABLE");
        assertThat(status(api.post("/api/v1/subscriptions/trial", Map.of(), patient.token()))).isEqualTo(403);
    }

    @Test
    void buyingAPlanActivatesItAndRenewalExtendsIt() throws Exception {
        double revenueBefore = revenue();
        MvcResult bought = buy(patient, "PATIENT_PREMIUM");
        assertThat(status(bought)).isEqualTo(201);
        JsonNode payment = api.body(bought);
        assertThat(payment.get("status").asText()).isEqualTo("RELEASED"); // pas de séquestre pour un abonnement
        assertThat(payment.get("commissionAmount").asDouble()).isEqualTo(0.0);
        assertThat(payment.get("payeeType").asText()).isEqualTo("PLATFORM");

        JsonNode s1 = current(patient);
        assertThat(s1.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(s1.get("planCode").asText()).isEqualTo("PATIENT_PREMIUM");
        assertThat(s1.get("paymentId").asText()).isEqualTo(payment.get("id").asText());
        assertThat(revenue() - revenueBefore).isEqualTo(2000.0);

        // renouvellement : la période s'ajoute à la fin de l'abonnement en cours
        assertThat(status(buy(patient, "PATIENT_PREMIUM"))).isEqualTo(201);
        JsonNode s2 = current(patient);
        assertThat(s2.get("id").asText()).isEqualTo(s1.get("id").asText());
        long extended = Duration.between(Instant.parse(s1.get("endsAt").asText()), Instant.parse(s2.get("endsAt").asText())).toDays();
        assertThat(extended).isEqualTo(30);
        assertThat(revenue() - revenueBefore).isEqualTo(4000.0);
    }

    @Test
    void aFailedPaymentActivatesNothing() throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("planCode", "PATIENT_PREMIUM");
        m.put("method", "MOBILE_MONEY");
        m.put("payerReference", "+2250700000000");
        MvcResult r = api.post("/api/v1/subscriptions", m, patient.token());
        assertThat(api.body(r).get("status").asText()).isEqualTo("FAILED");
        assertThat(status(api.get("/api/v1/subscriptions/current", patient.token()))).isEqualTo(204);
    }

    @Test
    void plansAreRestrictedToTheirAudience() throws Exception {
        assertThat(status(buy(pro, "PATIENT_PREMIUM"))).isEqualTo(403);
        assertThat(status(buy(patient, "PRO_PREMIUM"))).isEqualTo(403);
        assertThat(status(buy(patient, "NOPE"))).isEqualTo(404);
        assertThat(status(buy(admin, "PRO_PREMIUM"))).isEqualTo(403);
    }

    @Test
    void upgradingReplacesThePreviousPlanAndCancellingEndsIt() throws Exception {
        api.post("/api/v1/subscriptions/trial", Map.of(), pro.token());
        assertThat(status(buy(pro, "PRO_STANDARD"))).isEqualTo(201); // même plan que l'essai : devient ACTIVE
        assertThat(current(pro).get("status").asText()).isEqualTo("ACTIVE");

        assertThat(status(buy(pro, "PRO_PREMIUM"))).isEqualTo(201);
        assertThat(current(pro).get("planCode").asText()).isEqualTo("PRO_PREMIUM");
        JsonNode history = api.body(api.get("/api/v1/subscriptions/history", pro.token())).get("content");
        assertThat(history).hasSize(2);
        assertThat(history.findValuesAsText("status")).containsExactlyInAnyOrder("ACTIVE", "CANCELLED");

        MvcResult cancelled = api.post("/api/v1/subscriptions/cancel", Map.of(), pro.token());
        assertThat(status(cancelled)).isEqualTo(200);
        assertThat(api.body(cancelled).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(status(api.get("/api/v1/subscriptions/current", pro.token()))).isEqualTo(204);
        assertThat(status(api.post("/api/v1/subscriptions/cancel", Map.of(), pro.token()))).isEqualTo(404);
    }

    @Test
    void thePremiumPlanLowersTheCommission() throws Exception {
        assertThat(commissionOfNextConsultation()).isEqualTo(500.0); // taux global de 10 %

        buy(pro, "PRO_PREMIUM");
        Session otherPatient = fx.patient("Autre");
        Booking b = fx.book(otherPatient, pro, "CHAT", 1);
        JsonNode p = api.body(api.post("/api/v1/payments", Map.of("payableType", "APPOINTMENT", "payableId", b.id(),
                "method", "MOBILE_MONEY", "payerReference", PHONE), otherPatient.token()));
        assertThat(p.get("commissionRate").asDouble()).isEqualTo(0.05);
        assertThat(p.get("commissionAmount").asDouble()).isEqualTo(250.0);
        assertThat(p.get("netAmount").asDouble()).isEqualTo(4750.0);
    }

    private double commissionOfNextConsultation() throws Exception {
        Booking b = fx.book(patient, pro, "CHAT", 0);
        JsonNode p = api.body(api.post("/api/v1/payments", Map.of("payableType", "APPOINTMENT", "payableId", b.id(),
                "method", "MOBILE_MONEY", "payerReference", PHONE), patient.token()));
        return p.get("commissionAmount").asDouble();
    }

    private double revenue() throws Exception {
        return api.body(api.get("/api/v1/admin/payments/summary", admin.token())).get("subscriptionRevenue").asDouble();
    }

    // ------------------------------------------------------------------ administration

    @Test
    void financeManagesPlansAndSeesSubscriptions() throws Exception {
        buy(patient, "PATIENT_PREMIUM");

        JsonNode subs = api.body(api.get("/api/v1/admin/subscriptions", admin.token(), "status", "ACTIVE")).get("content");
        assertThat(subs.findValuesAsText("userId")).contains(patient.userId());
        assertThat(subs.findValuesAsText("userName")).anyMatch(n -> n.contains("Premium"));

        JsonNode plans = api.body(api.get("/api/v1/admin/subscription-plans", admin.token()));
        JsonNode standard = null;
        for (JsonNode p : plans) if (p.get("code").asText().equals("PRO_STANDARD")) standard = p;
        assertThat(standard).isNotNull();

        Map<String, Object> update = new HashMap<>();
        update.put("name", standard.get("name").asText());
        update.put("description", "Formule mise à jour");
        update.put("price", 6000);
        update.put("durationDays", 30);
        update.put("active", true);
        MvcResult ok = api.put("/api/v1/admin/subscription-plans/" + standard.get("id").asText(), update, admin.token());
        assertThat(status(ok)).isEqualTo(200);
        assertThat(api.body(ok).get("price").asDouble()).isEqualTo(6000.0);

        update.put("price", -1);
        assertThat(status(api.put("/api/v1/admin/subscription-plans/" + standard.get("id").asText(), update, admin.token())))
                .isEqualTo(400);
        update.put("price", 5000);
        assertThat(status(api.put("/api/v1/admin/subscription-plans/" + standard.get("id").asText(), update, admin.token())))
                .isEqualTo(200); // remet le prix initial pour les autres scénarios

        assertThat(status(api.get("/api/v1/admin/subscriptions", patient.token()))).isEqualTo(403);
        assertThat(status(api.put("/api/v1/admin/subscription-plans/" + standard.get("id").asText(), update, pro.token())))
                .isEqualTo(403);
    }
}
