package dev.jpzconception.allodocteur.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jpzconception.allodocteur.payment.application.CommissionPolicy;
import dev.jpzconception.allodocteur.payment.application.PaymentProperties;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.payment.domain.Wallet;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyOrderAccess;
import dev.jpzconception.allodocteur.pharmacy.domain.Fulfillment;
import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.subscription.application.SubscriptionCatalog;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommissionPolicyTest {

    private final SubscriptionCatalog catalog = mock(SubscriptionCatalog.class);
    private final PaymentProperties props = new PaymentProperties(new BigDecimal("0.10"), BigDecimal.ZERO, true,
            new BigDecimal("1000"), new BigDecimal("500"), new BigDecimal("1000000"));
    private final CommissionPolicy policy = new CommissionPolicy(props, catalog);

    @Test
    void usesTheGlobalRateUnlessThePlanOverridesIt() {
        UUID pro = UUID.randomUUID();
        UUID premium = UUID.randomUUID();
        when(catalog.commissionRateOverride(pro)).thenReturn(Optional.empty());
        when(catalog.commissionRateOverride(premium)).thenReturn(Optional.of(new BigDecimal("0.05")));

        assertThat(policy.rateFor(PayableType.APPOINTMENT, PayeeType.USER, pro)).isEqualByComparingTo("0.10");
        assertThat(policy.rateFor(PayableType.APPOINTMENT, PayeeType.USER, premium)).isEqualByComparingTo("0.05");
    }

    @Test
    void onlyConsultationsCarryACommissionByDefault() {
        assertThat(policy.rateFor(PayableType.PHARMACY_ORDER, PayeeType.PHARMACY, UUID.randomUUID())).isEqualByComparingTo("0");
        assertThat(policy.rateFor(PayableType.SUBSCRIPTION, PayeeType.PLATFORM, null)).isEqualByComparingTo("0");
        assertThat(policy.rateFor(PayableType.WALLET_TOPUP, PayeeType.USER, UUID.randomUUID())).isEqualByComparingTo("0");
    }

    @Test
    void commissionIsRoundedToWholeFrancs() {
        assertThat(policy.commission(new BigDecimal("5000"), new BigDecimal("0.10"))).isEqualByComparingTo("500");
        assertThat(policy.commission(new BigDecimal("1005"), new BigDecimal("0.10"))).isEqualByComparingTo("101"); // 100,5
        assertThat(policy.commission(new BigDecimal("3333"), new BigDecimal("0.10"))).isEqualByComparingTo("333");
        assertThat(policy.commission(new BigDecimal("4000"), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void aWalletNeverGoesNegative() {
        Wallet w = new Wallet();
        w.credit(new BigDecimal("1000"));
        w.debit(new BigDecimal("400"));
        assertThat(w.getBalance()).isEqualByComparingTo("600");
        assertThatThrownBy(() -> w.debit(new BigDecimal("601"))).isInstanceOf(ApiException.class)
                .hasMessageContaining("Solde insuffisant");
        assertThatThrownBy(() -> w.credit(BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThat(w.getBalance()).isEqualByComparingTo("600");
    }

    @Test
    void aPharmacyOrderTotalIncludesDeliveryOnlyWhenDelivered() {
        UUID id = UUID.randomUUID();
        var delivery = new PharmacyOrderAccess.Ref(id, "CMD-1", id, id, OrderStatus.ACCEPTED, Fulfillment.DELIVERY,
                new BigDecimal("12000"), new BigDecimal("1500"), "XOF");
        var pickup = new PharmacyOrderAccess.Ref(id, "CMD-2", id, id, OrderStatus.ACCEPTED, Fulfillment.PICKUP,
                new BigDecimal("12000"), new BigDecimal("1500"), "XOF");
        var unquoted = new PharmacyOrderAccess.Ref(id, "CMD-3", id, id, OrderStatus.SENT, Fulfillment.PICKUP,
                null, BigDecimal.ZERO, "XOF");
        assertThat(delivery.payableTotal()).isEqualByComparingTo("13500");
        assertThat(pickup.payableTotal()).isEqualByComparingTo("12000");
        assertThat(unquoted.payableTotal()).isNull();
    }
}
