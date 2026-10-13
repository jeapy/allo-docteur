package dev.jpzconception.allodocteur.subscription.api;

import dev.jpzconception.allodocteur.payment.api.PaymentDtos.PaymentDto;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.subscription.api.SubscriptionDtos.PlanDto;
import dev.jpzconception.allodocteur.subscription.api.SubscriptionDtos.SubscriptionDto;
import dev.jpzconception.allodocteur.subscription.application.SubscriptionService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Abonnements professionnels et Premium patient. */
@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Abonnements")
public class SubscriptionController {

    public record PurchaseRequest(@NotBlank @Size(max = 30) String planCode, @NotNull PaymentMethod method,
                                  @Size(max = 30) String provider, @Size(max = 100) String payerReference) {}

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** Formules proposées à mon profil. */
    @GetMapping("/plans")
    public List<PlanDto> plans(@AuthenticationPrincipal AuthenticatedUser me) {
        return subscriptions.plansFor(me);
    }

    /** Abonnement en cours (204 s'il n'y en a pas). */
    @GetMapping("/current")
    public ResponseEntity<SubscriptionDto> current(@AuthenticationPrincipal AuthenticatedUser me) {
        return subscriptions.current(me).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    public PageResponse<SubscriptionDto> history(@AuthenticationPrincipal AuthenticatedUser me,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(subscriptions.history(me, page, size));
    }

    @PostMapping("/trial")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public SubscriptionDto trial(@AuthenticationPrincipal AuthenticatedUser me) {
        return subscriptions.startTrial(me);
    }

    /** Achète une période : l'abonnement s'active dès que le paiement est encaissé. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto purchase(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody PurchaseRequest req,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return PaymentDto.of(subscriptions.purchase(me, req.planCode(), req.method(), req.provider(),
                req.payerReference(), idempotencyKey));
    }

    @PostMapping("/cancel")
    public SubscriptionDto cancel(@AuthenticationPrincipal AuthenticatedUser me) {
        return subscriptions.cancel(me);
    }
}
