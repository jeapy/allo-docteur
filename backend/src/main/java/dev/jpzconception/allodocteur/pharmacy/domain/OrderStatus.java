package dev.jpzconception.allodocteur.pharmacy.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    SENT, ACCEPTED, READY, OUT_FOR_DELIVERY, COMPLETED, DELIVERED, DELIVERY_FAILED, REJECTED, CANCELLED;

    /** Commandes encore en traitement. */
    public static final Set<OrderStatus> ACTIVE = EnumSet.of(SENT, ACCEPTED, READY, OUT_FOR_DELIVERY);
}
