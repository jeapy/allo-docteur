package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port vers un opérateur de paiement (Mobile Money, passerelle carte, banque). Plusieurs implémentations peuvent
 * coexister (stratégie) : l'implémentation par défaut est un stub. {@link #code()} identifie l'opérateur.
 */
public interface PaymentProvider {

    String code();

    PaymentMethod method();

    ChargeResult charge(ChargeRequest request);

    /** Interroge l'opérateur sur une opération restée en attente. */
    ChargeResult status(String providerRef);

    void refund(String providerRef, BigDecimal amount, String currency);

    PayoutResult payout(PayoutRequest request);

    /** @param payerReference numéro Mobile Money, jeton de carte (jamais le numéro de carte) ou rien pour un virement. */
    record ChargeRequest(UUID paymentId, BigDecimal amount, String currency, String payerReference, String description) {}

    enum Outcome { SUCCEEDED, PENDING, FAILED }

    record ChargeResult(Outcome outcome, String providerRef, String failureReason) {
        public static ChargeResult succeeded(String ref) { return new ChargeResult(Outcome.SUCCEEDED, ref, null); }
        public static ChargeResult pending(String ref) { return new ChargeResult(Outcome.PENDING, ref, null); }
        public static ChargeResult failed(String ref, String reason) { return new ChargeResult(Outcome.FAILED, ref, reason); }
    }

    record PayoutRequest(UUID withdrawalId, BigDecimal amount, String currency, String destination) {}

    record PayoutResult(boolean success, String providerRef, String failureReason) {}
}
