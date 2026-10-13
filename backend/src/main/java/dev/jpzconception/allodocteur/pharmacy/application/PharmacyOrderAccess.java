package dev.jpzconception.allodocteur.pharmacy.application;

import dev.jpzconception.allodocteur.pharmacy.domain.Fulfillment;
import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrder;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyOrderRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accès du module paiement aux commandes de pharmacie (montant, propriétaire, état). */
@Service
@Transactional(readOnly = true)
public class PharmacyOrderAccess {

    public record Ref(UUID id, String number, UUID accountId, UUID pharmacyId, OrderStatus status,
                      Fulfillment fulfillment, BigDecimal quotedTotal, BigDecimal deliveryFee, String currency) {
        /** Total à régler : médicaments + frais de livraison si la commande est livrée. */
        public BigDecimal payableTotal() {
            if (quotedTotal == null) return null;
            return fulfillment == Fulfillment.DELIVERY ? quotedTotal.add(deliveryFee) : quotedTotal;
        }
    }

    private final PharmacyOrderRepository orders;
    private final dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyMemberRepository members;

    public PharmacyOrderAccess(PharmacyOrderRepository orders,
                               dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyMemberRepository members) {
        this.orders = orders;
        this.members = members;
    }

    public Ref requireOwned(UUID accountId, UUID orderId) {
        return orders.findById(orderId).filter(o -> o.getAccountId().equals(accountId)).map(PharmacyOrderAccess::toRef)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Commande introuvable."));
    }

    /** Comptes des pharmaciens rattachés à une pharmacie (pour les prévenir d'une nouvelle commande). */
    public java.util.List<UUID> memberUserIds(UUID pharmacyId) {
        return members.findByPharmacyId(pharmacyId).stream().map(m -> m.getUserId()).toList();
    }

    public Optional<Ref> find(UUID orderId) {
        return orders.findById(orderId).map(PharmacyOrderAccess::toRef);
    }

    private static Ref toRef(PharmacyOrder o) {
        return new Ref(o.getId(), o.getNumber(), o.getAccountId(), o.getPharmacyId(), o.getStatus(), o.getFulfillment(),
                o.getQuotedTotal(), o.getDeliveryFee(), o.getCurrency());
    }
}
