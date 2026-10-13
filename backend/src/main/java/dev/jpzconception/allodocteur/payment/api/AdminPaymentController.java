package dev.jpzconception.allodocteur.payment.api;

import dev.jpzconception.allodocteur.payment.api.PaymentDtos.PaymentDto;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.ReasonRequest;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.SummaryDto;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.WithdrawalDto;
import dev.jpzconception.allodocteur.payment.application.PaymentService;
import dev.jpzconception.allodocteur.payment.application.PaymentStatsService;
import dev.jpzconception.allodocteur.payment.application.WithdrawalService;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
import dev.jpzconception.allodocteur.payment.domain.WithdrawalStatus;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Finance : paiements, commissions, virements à confirmer, retraits des professionnels. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN') and hasAnyAuthority('LEVEL_SUPER', 'LEVEL_FINANCE')")
@Tag(name = "Administration – Paiements")
public class AdminPaymentController {

    private final PaymentService payments;
    private final PaymentStatsService stats;
    private final WithdrawalService withdrawals;

    public AdminPaymentController(PaymentService payments, PaymentStatsService stats, WithdrawalService withdrawals) {
        this.payments = payments;
        this.stats = stats;
        this.withdrawals = withdrawals;
    }

    @GetMapping("/payments")
    public PageResponse<PaymentDto> list(@RequestParam(required = false) UUID payerId,
                                         @RequestParam(required = false) PaymentStatus status,
                                         @RequestParam(required = false) PaymentMethod method,
                                         @RequestParam(required = false) PayableType type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(payments.adminList(payerId, status, method, type, from, to, page, size).map(PaymentDto::of));
    }

    /** Volume, commissions, séquestre, remboursements et retraits en attente (depuis {@code from}, sinon depuis toujours). */
    @GetMapping("/payments/summary")
    public SummaryDto summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from) {
        return SummaryDto.of(stats.summary(from));
    }

    @GetMapping("/payments/{id}")
    public PaymentDto get(@PathVariable UUID id) {
        return PaymentDto.of(payments.getAsAdmin(id));
    }

    @PostMapping("/payments/{id}/confirm-transfer")
    public PaymentDto confirmTransfer(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return PaymentDto.of(payments.confirmTransfer(me, id));
    }

    @PostMapping("/payments/{id}/reject-transfer")
    public PaymentDto rejectTransfer(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                     @Valid @RequestBody ReasonRequest req) {
        return PaymentDto.of(payments.rejectTransfer(me, id, req.reason()));
    }

    @PostMapping("/payments/{id}/refund")
    public PaymentDto refund(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                             @Valid @RequestBody ReasonRequest req) {
        return PaymentDto.of(payments.adminRefund(me, id, req.reason()));
    }

    @GetMapping("/withdrawals")
    public PageResponse<WithdrawalDto> withdrawals(@RequestParam(required = false) WithdrawalStatus status,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(withdrawals.adminList(status, page, size).map(WithdrawalDto::full));
    }

    @PostMapping("/withdrawals/{id}/pay")
    public WithdrawalDto pay(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return WithdrawalDto.full(withdrawals.pay(me, id));
    }

    @PostMapping("/withdrawals/{id}/reject")
    public WithdrawalDto reject(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                @Valid @RequestBody ReasonRequest req) {
        return WithdrawalDto.full(withdrawals.reject(me, id, req.reason()));
    }
}
