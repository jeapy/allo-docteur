package dev.jpzconception.allodocteur.review.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {
    private ReviewDtos() {}

    public record PostReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {}

    public record HideReviewRequest(@NotBlank @Size(max = 500) String reason) {}
}
