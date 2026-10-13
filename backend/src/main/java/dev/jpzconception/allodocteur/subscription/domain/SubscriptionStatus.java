package dev.jpzconception.allodocteur.subscription.domain;

import java.util.EnumSet;
import java.util.Set;

public enum SubscriptionStatus {
    TRIAL, ACTIVE, EXPIRED, CANCELLED;

    /** Statuts d'un abonnement en cours (un seul par utilisateur, garanti en base). */
    public static final Set<SubscriptionStatus> LIVE = EnumSet.of(TRIAL, ACTIVE);
}
