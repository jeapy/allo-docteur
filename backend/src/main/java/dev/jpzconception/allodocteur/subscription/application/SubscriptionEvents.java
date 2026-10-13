package dev.jpzconception.allodocteur.subscription.application;

import java.time.Instant;
import java.util.UUID;

public final class SubscriptionEvents {

    private SubscriptionEvents() {}

    public record Activated(UUID subscriptionId, UUID userId, String planCode, Instant endsAt) {}
}
