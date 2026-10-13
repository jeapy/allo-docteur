package dev.jpzconception.allodocteur.pharmacy.application;

import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import java.util.UUID;

/** Publié à chaque changement d'état d'une commande (notifications en phase 7, paiement en phase 6). */
public final class PharmacyEvents {

    private PharmacyEvents() {}

    public record OrderStatusChanged(UUID orderId, UUID accountId, UUID pharmacyId, OrderStatus status) {}
}
