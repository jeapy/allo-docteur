package dev.jpzconception.allodocteur.appointment.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Port : le module paiement dit si un rendez-vous payant a été réglé avant que le professionnel puisse le confirmer. */
public interface PaymentGate {

    /** Lève 409 PAYMENT_REQUIRED si un règlement est exigé et n'a pas eu lieu. */
    void requirePaid(UUID appointmentId, BigDecimal fee);
}
