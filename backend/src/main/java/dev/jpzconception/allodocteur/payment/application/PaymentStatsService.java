package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
import dev.jpzconception.allodocteur.payment.domain.WalletOwnerType;
import dev.jpzconception.allodocteur.payment.domain.WithdrawalStatus;
import dev.jpzconception.allodocteur.payment.infrastructure.PaymentRepository;
import dev.jpzconception.allodocteur.payment.infrastructure.WithdrawalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indicateurs financiers pour l'administration (finance et super-administrateur). */
@Service
@Transactional(readOnly = true)
public class PaymentStatsService {

    public record Summary(Instant from, BigDecimal volume, BigDecimal commissions, BigDecimal subscriptionRevenue,
                          BigDecimal refunded, BigDecimal escrow, long pendingPayments, long pendingWithdrawals,
                          BigDecimal pendingWithdrawalsAmount, BigDecimal platformBalance, String currency) {}

    private final PaymentRepository payments;
    private final WithdrawalRepository withdrawals;
    private final WalletService wallets;

    public PaymentStatsService(PaymentRepository payments, WithdrawalRepository withdrawals, WalletService wallets) {
        this.payments = payments;
        this.withdrawals = withdrawals;
        this.wallets = wallets;
    }

    public Summary summary(Instant from) {
        Instant since = from == null ? Instant.EPOCH : from;
        return new Summary(since, payments.volumeSince(since), payments.commissionsSince(since),
                payments.subscriptionRevenueSince(since), payments.refundedSince(since), payments.escrowTotal(),
                payments.countByStatus(PaymentStatus.PENDING), withdrawals.countByStatus(WithdrawalStatus.REQUESTED),
                withdrawals.requestedTotal(), wallets.summary(WalletOwnerType.PLATFORM, null).balance(), "XOF");
    }
}
