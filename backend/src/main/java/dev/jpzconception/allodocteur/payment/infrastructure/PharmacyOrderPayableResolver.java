package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.application.PayableResolver;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyOrderAccess;
import dev.jpzconception.allodocteur.pharmacy.application.PharmacyOrderAccess.Ref;
import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Une commande se paie une fois acceptée (le devis est connu) ; les fonds vont au portefeuille de la pharmacie. */
@Component
@Transactional(readOnly = true)
public class PharmacyOrderPayableResolver implements PayableResolver {

    private static final Set<OrderStatus> PAYABLE = EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.READY,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.COMPLETED, OrderStatus.DELIVERED);

    private final PharmacyOrderAccess orders;

    public PharmacyOrderPayableResolver(PharmacyOrderAccess orders) {
        this.orders = orders;
    }

    @Override
    public PayableType type() {
        return PayableType.PHARMACY_ORDER;
    }

    @Override
    public boolean escrow() {
        return true;
    }

    @Override
    public PayableInfo resolve(AuthenticatedUser payer, UUID payableId) {
        Ref o = orders.requireOwned(payer.id(), payableId);
        if (!PAYABLE.contains(o.status())) {
            throw ApiException.conflict("NOT_PAYABLE",
                    "Cette commande ne peut pas être payée maintenant (statut " + o.status() + ").");
        }
        if (o.payableTotal() == null || o.payableTotal().signum() <= 0) {
            throw ApiException.conflict("NOTHING_TO_PAY", "Aucun montant à payer pour cette commande.");
        }
        return new PayableInfo(o.payableTotal(), o.currency(), PayeeType.PHARMACY, o.pharmacyId(),
                "Commande " + o.number());
    }

    @Override
    public State state(UUID payableId) {
        return orders.find(payableId).map(o -> switch (o.status()) {
            case COMPLETED, DELIVERED -> State.DELIVERED;
            case REJECTED, CANCELLED, DELIVERY_FAILED -> State.CLOSED;
            default -> State.OPEN;
        }).orElse(State.CLOSED);
    }
}
