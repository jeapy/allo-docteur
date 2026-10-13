package dev.jpzconception.allodocteur.review.api;

import dev.jpzconception.allodocteur.review.api.ReviewDtos.HideReviewRequest;
import dev.jpzconception.allodocteur.review.application.ReviewService;
import dev.jpzconception.allodocteur.review.application.ReviewService.ReviewView;
import dev.jpzconception.allodocteur.review.domain.ReviewStatus;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Modération des avis (support, super-administrateur). */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
@Tag(name = "Administration – Avis")
public class AdminReviewController {

    private final ReviewService reviews;

    public AdminReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public PageResponse<ReviewView> list(@RequestParam(required = false) ReviewStatus status,
                                         @RequestParam(required = false) UUID professionalId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(reviews.adminList(status, professionalId, page, size));
    }

    @PostMapping("/{id}/hide")
    public ReviewView hide(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                           @Valid @RequestBody HideReviewRequest req) {
        return reviews.hide(me, id, req.reason());
    }

    @PostMapping("/{id}/show")
    public ReviewView show(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return reviews.show(me, id);
    }
}
