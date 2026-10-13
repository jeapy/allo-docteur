package dev.jpzconception.allodocteur.payment.domain;

/** PENDING → CAPTURED (fonds en séquestre) → RELEASED (répartis) ou REFUNDED ; PENDING → FAILED. */
public enum PaymentStatus {
    PENDING, FAILED, CAPTURED, RELEASED, REFUNDED
}
