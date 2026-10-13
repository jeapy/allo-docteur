package dev.jpzconception.allodocteur.review.application;

import java.util.UUID;

public final class ReviewEvents {

    private ReviewEvents() {}

    public record Posted(UUID reviewId, UUID professionalId, int rating) {}
}
