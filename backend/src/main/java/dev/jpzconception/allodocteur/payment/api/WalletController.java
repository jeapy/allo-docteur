package dev.jpzconception.allodocteur.payment.api;

import dev.jpzconception.allodocteur.payment.api.PaymentDtos.PaymentDto;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.TopUpRequest;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.TransactionDto;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.WalletDto;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.WithdrawalDto;
import dev.jpzconception.allodocteur.payment.api.PaymentDtos.WithdrawalRequest;
import dev.jpzconception.allodocteur.payment.application.PaymentService;
import dev.jpzconception.allodocteur.payment.application.WalletService;
import dev.jpzconception.allodocteur.payment.application.WithdrawalService;
import dev.jpzconception.allodocteur.payment.domain.WalletOwnerType;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/** Portefeuille électronique : solde, historique, recharge et (professionnels) retrait des gains. */
@RestController
@RequestMapping("/api/v1/wallet")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Portefeuille")
public class WalletController {

    private final WalletService wallets;
    private final PaymentService payments;
    private final WithdrawalService withdrawals;

    public WalletController(WalletService wallets, PaymentService payments, WithdrawalService withdrawals) {
        this.wallets = wallets;
        this.payments = payments;
        this.withdrawals = withdrawals;
    }

    @GetMapping
    public WalletDto get(@AuthenticationPrincipal AuthenticatedUser me) {
        var s = wallets.summary(WalletOwnerType.USER, me.id());
        return new WalletDto(s.balance(), s.currency());
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionDto> transactions(@AuthenticationPrincipal AuthenticatedUser me,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(wallets.transactions(me.id(), page, size).map(TransactionDto::of));
    }

    /** Recharge par Mobile Money, carte ou virement ; le solde est crédité à l'encaissement. */
    @PostMapping("/topups")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto topUp(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody TopUpRequest req,
                            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return PaymentDto.of(payments.topUp(me, req.amount(), req.method(), req.provider(), req.payerReference(),
                idempotencyKey));
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public WithdrawalDto withdraw(@AuthenticationPrincipal AuthenticatedUser me,
                                  @Valid @RequestBody WithdrawalRequest req) {
        return WithdrawalDto.masked(withdrawals.request(me, req.amount(), req.method(), req.destination()));
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public PageResponse<WithdrawalDto> myWithdrawals(@AuthenticationPrincipal AuthenticatedUser me,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(withdrawals.mine(me, page, size).map(WithdrawalDto::masked));
    }
}
