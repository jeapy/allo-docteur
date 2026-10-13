package dev.jpzconception.allodocteur.subscription.application;

import dev.jpzconception.allodocteur.subscription.domain.PlanAudience;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionOrderRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accès du module paiement aux commandes d'abonnement. */
@Service
@Transactional(readOnly = true)
public class SubscriptionOrderAccess {

    public record Ref(UUID id, UUID userId, String planName, PlanAudience audience, BigDecimal price, String currency) {}

    private final SubscriptionOrderRepository orders;

    public SubscriptionOrderAccess(SubscriptionOrderRepository orders) {
        this.orders = orders;
    }

    public Ref requireOwned(UUID userId, UUID orderId) {
        return orders.findById(orderId).filter(o -> o.getUserId().equals(userId))
                .map(o -> new Ref(o.getId(), o.getUserId(), o.getPlan().getName(), o.getPlan().getAudience(),
                        o.getPrice(), o.getCurrency()))
                .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_ORDER_NOT_FOUND", "Commande d'abonnement introuvable."));
    }
}
