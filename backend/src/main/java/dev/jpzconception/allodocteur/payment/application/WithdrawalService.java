package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.application.PaymentProvider.PayoutResult;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.payment.domain.TxReason;
import dev.jpzconception.allodocteur.payment.domain.WalletOwnerType;
import dev.jpzconception.allodocteur.payment.domain.Withdrawal;
import dev.jpzconception.allodocteur.payment.domain.WithdrawalMethod;
import dev.jpzconception.allodocteur.payment.domain.WithdrawalStatus;
import dev.jpzconception.allodocteur.payment.infrastructure.WithdrawalRepository;
import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retrait des gains : le montant est réservé (débité) dès la demande, puis payé ou restitué par la finance. */
@Service
@Transactional
public class WithdrawalService {

    private final WithdrawalRepository withdrawals;
    private final WalletService wallets;
    private final PaymentProviders providers;
    private final PaymentProperties props;
    private final AuditService audit;
    private final Clock clock;

    public WithdrawalService(WithdrawalRepository withdrawals, WalletService wallets, PaymentProviders providers,
                             PaymentProperties props, AuditService audit, Clock clock) {
        this.withdrawals = withdrawals;
        this.wallets = wallets;
        this.providers = providers;
        this.props = props;
        this.audit = audit;
        this.clock = clock;
    }

    public Withdrawal request(AuthenticatedUser me, BigDecimal amount, WithdrawalMethod method, String destination) {
        BigDecimal value = amount == null ? null : amount.setScale(0, RoundingMode.HALF_UP);
        if (value == null || value.compareTo(props.minWithdrawal()) < 0) {
            throw ApiException.badRequest("AMOUNT_BELOW_MINIMUM",
                    "Le retrait minimum est de " + props.minWithdrawal().toPlainString() + ".");
        }
        if (destination == null || destination.isBlank()) {
            throw ApiException.badRequest("INVALID_DESTINATION", "Destination du versement requise.");
        }
        UUID walletId = wallets.ensureWallet(WalletOwnerType.USER, me.id());
        Withdrawal wd = new Withdrawal();
        wd.setWalletId(walletId);
        wd.setUserId(me.id());
        wd.setAmount(value);
        wd.setMethod(method);
        wd.setDestination(destination.trim());
        withdrawals.saveAndFlush(wd);
        wallets.debit(WalletOwnerType.USER, me.id(), value, TxReason.WITHDRAWAL, null, wd.getId(),
                "Demande de retrait");
        return wd;
    }

    @Transactional(readOnly = true)
    public Page<Withdrawal> mine(AuthenticatedUser me, int page, int size) {
        return withdrawals.findByUserId(me.id(), pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Withdrawal> adminList(WithdrawalStatus status, int page, int size) {
        return status == null ? withdrawals.findAll(pageable(page, size)) : withdrawals.findByStatus(status, pageable(page, size));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Withdrawal pay(AuthenticatedUser admin, UUID id) {
        Withdrawal wd = withdrawals.findById(id).orElseThrow(WithdrawalService::notFound);
        if (wd.getStatus() != WithdrawalStatus.REQUESTED) {
            throw ApiException.conflict("INVALID_STATE", "Ce retrait a déjà été traité.");
        }
        PaymentMethod method = wd.getMethod() == WithdrawalMethod.MOBILE_MONEY
                ? PaymentMethod.MOBILE_MONEY : PaymentMethod.BANK_TRANSFER;
        PayoutResult res = providers.forMethod(method, null).payout(
                new PaymentProvider.PayoutRequest(wd.getId(), wd.getAmount(), wd.getCurrency(), wd.getDestination()));
        if (!res.success()) {
            throw ApiException.conflict("PAYOUT_FAILED", "Le versement a échoué : " + res.failureReason());
        }
        wd.markPaid(admin.id(), clock.instant(), res.providerRef());
        audit.record(admin.id(), "WITHDRAWAL_PAID", "WITHDRAWAL", wd.getId().toString(), null);
        return wd;
    }

    public Withdrawal reject(AuthenticatedUser admin, UUID id, String reason) {
        Withdrawal wd = withdrawals.findById(id).orElseThrow(WithdrawalService::notFound);
        wd.reject(admin.id(), clock.instant(), reason);
        wallets.credit(WalletOwnerType.USER, wd.getUserId(), wd.getAmount(), TxReason.WITHDRAWAL_REVERSAL, null,
                wd.getId(), "Retrait rejeté");
        audit.record(admin.id(), "WITHDRAWAL_REJECTED", "WITHDRAWAL", wd.getId().toString(), reason);
        return wd;
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static ApiException notFound() {
        return ApiException.notFound("WITHDRAWAL_NOT_FOUND", "Retrait introuvable.");
    }
}
