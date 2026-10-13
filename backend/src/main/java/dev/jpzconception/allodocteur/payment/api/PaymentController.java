package dev.jpzconception.allodocteur.payment.api;

import dev.jpzconception.allodocteur.payment.api.PaymentDtos.PayRequest;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.PaymentDto;
import dev.jpzconception.allodocteur.payment.application.PaymentService;
import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Paiement des rendez-vous et des commandes de pharmacie. */
@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Paiements")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * Initie un paiement (rendez-vous ou commande de pharmacie). L'en-tête optionnel {@code Idempotency-Key} rend
     * l'appel rejouable sans double paiement. Un refus de l'opérateur se lit dans le statut {@code FAILED}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PATIENT')")
    public PaymentDto pay(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody PayRequest req,
                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return PaymentDto.of(payments.pay(me, req.payableType(), req.payableId(), req.method(), req.provider(),
                req.payerReference(), idempotencyKey));
    }

    /** Mes paiements : ceux que j'ai faits et, pour un professionnel, ceux qui lui sont destinés. */
    @GetMapping
    public PageResponse<PaymentDto> mine(@AuthenticationPrincipal AuthenticatedUser me,
                                         @RequestParam(required = false) PaymentStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(payments.mine(me, status, page, size).map(PaymentDto::of));
    }

    @GetMapping("/{id}")
    public PaymentDto get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return PaymentDto.of(payments.get(me, id));
    }

    /** Interroge l'opérateur pour un paiement resté en attente. */
    @PostMapping("/{id}/refresh")
    @PreAuthorize("hasRole('PATIENT')")
    public PaymentDto refresh(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return PaymentDto.of(payments.refresh(me, id));
    }
}
