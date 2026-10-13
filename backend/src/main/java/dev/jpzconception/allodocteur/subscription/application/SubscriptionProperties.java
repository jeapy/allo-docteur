package dev.jpzconception.allodocteur.subscription.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Règles d'abonnement (app.subscriptions.*). */
@ConfigurationProperties(prefix = "app.subscriptions")
public record SubscriptionProperties(
        /** Durée de l'essai gratuit d'un professionnel. */
        @DefaultValue("30") int trialDays,
        /** Code du plan sur lequel porte l'essai. */
        @DefaultValue("PRO_STANDARD") String trialPlanCode) {}
