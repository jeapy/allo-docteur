package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.domain.PayableType;
import java.math.BigDecimal;
import java.util.UUID;

/** Événements de paiement (activation des abonnements ici, notifications en phase 7). */
public final class PaymentEvents {

    private PaymentEvents() {}

    public record Captured(UUID paymentId, PayableType payableType, UUID payableId, UUID payerId, BigDecimal amount) {}

    public record Released(UUID paymentId, PayableType payableType, UUID payableId, UUID payerId, BigDecimal amount) {}

    public record Refunded(UUID paymentId, PayableType payableType, UUID payableId, UUID payerId, BigDecimal amount) {}

    public record Failed(UUID paymentId, PayableType payableType, UUID payableId, UUID payerId, String reason) {}
}
