package dev.jpzconception.allodocteur.payment.application;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Règles de paiement (app.payments.*). */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(
        /** Taux de commission par défaut sur les consultations (0,10 = 10 %), surchargé par le plan du professionnel. */
        @DefaultValue("0.10") BigDecimal commissionRate,
        /** Commission sur les commandes de pharmacie (0 par défaut : le cahier des charges ne la prévoit pas). */
        @DefaultValue("0") BigDecimal pharmacyCommissionRate,
        /** Un rendez-vous payant doit être payé avant que le professionnel puisse le confirmer. */
        @DefaultValue("true") boolean requirePaymentToConfirm,
        @DefaultValue("1000") BigDecimal minWithdrawal,
        @DefaultValue("500") BigDecimal minTopup,
        @DefaultValue("1000000") BigDecimal maxTopup) {}
