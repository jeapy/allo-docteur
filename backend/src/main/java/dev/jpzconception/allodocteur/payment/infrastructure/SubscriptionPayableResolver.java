package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.application.PayableResolver;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.subscription.application.SubscriptionOrderAccess;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Un abonnement se paie à la plateforme, sans séquestre ni commission. */
@Component
@Transactional(readOnly = true)
public class SubscriptionPayableResolver implements PayableResolver {

    private final SubscriptionOrderAccess orders;

    public SubscriptionPayableResolver(SubscriptionOrderAccess orders) {
        this.orders = orders;
    }

    @Override
    public PayableType type() {
        return PayableType.SUBSCRIPTION;
    }

    @Override
    public boolean escrow() {
        return false;
    }

    @Override
    public PayableInfo resolve(AuthenticatedUser payer, UUID payableId) {
        var o = orders.requireOwned(payer.id(), payableId);
        return new PayableInfo(o.price(), o.currency(), PayeeType.PLATFORM, null, "Abonnement " + o.planName());
    }

    @Override
    public State state(UUID payableId) {
        return State.OPEN;
    }
}
