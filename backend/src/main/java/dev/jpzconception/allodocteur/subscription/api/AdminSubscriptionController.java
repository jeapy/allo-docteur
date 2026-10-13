package dev.jpzconception.allodocteur.subscription.api;

import dev.jpzconception.allodocteur.subscription.api.SubscriptionDtos.PlanDto;
import dev.jpzconception.allodocteur.subscription.api.SubscriptionDtos.PlanUpdateRequest;
import dev.jpzconception.allodocteur.subscription.api.SubscriptionDtos.SubscriptionDto;
import dev.jpzconception.allodocteur.subscription.application.SubscriptionService;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Suivi des abonnements (tous niveaux) et gestion des formules (finance, super-administrateur). */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration – Abonnements")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptions;

    public AdminSubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping("/subscriptions")
    public PageResponse<SubscriptionDto> list(@RequestParam(required = false) SubscriptionStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(subscriptions.adminList(status, page, size));
    }

    @GetMapping("/subscription-plans")
    public List<PlanDto> plans() {
        return subscriptions.adminPlans();
    }

    @PutMapping("/subscription-plans/{id}")
    @PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_FINANCE')")
    public PlanDto update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                          @Valid @RequestBody PlanUpdateRequest req) {
        return subscriptions.updatePlan(me, id, new SubscriptionService.PlanUpdate(req.name(), req.description(),
                req.price(), req.durationDays(), req.commissionRate(), req.active()));
    }
}
