package dev.jpzconception.allodocteur.review.api;

import dev.jpzconception.allodocteur.review.api.ReviewDtos.PostReviewRequest;
import dev.jpzconception.allodocteur.review.application.ReviewService;
import dev.jpzconception.allodocteur.review.application.ReviewService.ReviewView;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Avis")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    /** Noter une consultation terminée (une seule fois par rendez-vous). */
    @PostMapping("/appointments/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public ReviewView post(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                           @Valid @RequestBody PostReviewRequest req) {
        return reviews.post(me, id, req.rating(), req.comment());
    }

    /** Avis visibles d'un professionnel (public pour tout utilisateur connecté). */
    @GetMapping("/professionals/{id}/reviews")
    @PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL', 'ADMIN')")
    public PageResponse<ReviewView> forProfessional(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(reviews.forProfessional(id, page, size));
    }

    @GetMapping("/reviews/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public PageResponse<ReviewView> mine(@AuthenticationPrincipal AuthenticatedUser me,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(reviews.mine(me.id(), page, size));
    }
}
