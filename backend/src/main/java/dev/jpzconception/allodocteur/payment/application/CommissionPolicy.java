package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.subscription.application.SubscriptionCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Taux de commission : global (configuration), surchargé par le plan d'abonnement du professionnel s'il en définit un. */
@Component
public class CommissionPolicy {

    private final PaymentProperties props;
    private final SubscriptionCatalog catalog;

    public CommissionPolicy(PaymentProperties props, SubscriptionCatalog catalog) {
        this.props = props;
        this.catalog = catalog;
    }

    public BigDecimal rateFor(PayableType type, PayeeType payeeType, UUID payeeId) {
        return switch (type) {
            case APPOINTMENT -> payeeType == PayeeType.USER
                    ? catalog.commissionRateOverride(payeeId).orElse(props.commissionRate())
                    : props.commissionRate();
            case PHARMACY_ORDER -> props.pharmacyCommissionRate();
            case SUBSCRIPTION, WALLET_TOPUP -> BigDecimal.ZERO;
        };
    }

    /** Francs entiers (XOF n'a pas de centimes), arrondi au plus proche. */
    public BigDecimal commission(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(0, RoundingMode.HALF_UP);
    }
}
