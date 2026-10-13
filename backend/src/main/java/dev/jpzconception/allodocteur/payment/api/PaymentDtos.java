package dev.jpzconception.allodocteur.payment.api;

import dev.jpzconception.allodocteur.payment.application.PaymentStatsService;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.payment.domain.Payment;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
import dev.jpzconception.allodocteur.payment.domain.TxReason;
import dev.jpzconception.allodocteur.payment.domain.TxType;
import dev.jpzconception.allodocteur.payment.domain.WalletTransaction;
import dev.jpzconception.allodocteur.payment.domain.Withdrawal;
import dev.jpzconception.allodocteur.payment.domain.WithdrawalMethod;
import dev.jpzconception.allodocteur.payment.domain.WithdrawalStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {}

    public record PayRequest(
            @NotNull PayableType payableType,
            @NotNull UUID payableId,
            @NotNull PaymentMethod method,
            @Size(max = 30) String provider,
            @Size(max = 100) String payerReference) {}

    public record TopUpRequest(
            @NotNull @DecimalMin("1") BigDecimal amount,
            @NotNull PaymentMethod method,
            @Size(max = 30) String provider,
            @Size(max = 100) String payerReference) {}

    public record WithdrawalRequest(
            @NotNull @DecimalMin("1") BigDecimal amount,
            @NotNull WithdrawalMethod method,
            @NotBlank @Size(max = 100) String destination) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record PaymentDto(UUID id, UUID payerId, PayableType payableType, UUID payableId, PaymentMethod method,
                             String provider, String providerRef, PaymentStatus status, BigDecimal amount,
                             String currency, BigDecimal commissionRate, BigDecimal commissionAmount,
                             BigDecimal netAmount, PayeeType payeeType, UUID payeeId, String failureReason,
                             Instant createdAt, Instant capturedAt, Instant releasedAt, Instant refundedAt) {
        public static PaymentDto of(Payment p) {
            return new PaymentDto(p.getId(), p.getPayerId(), p.getPayableType(), p.getPayableId(), p.getMethod(),
                    p.getProvider(), p.getProviderRef(), p.getStatus(), p.getAmount(), p.getCurrency(),
                    p.getCommissionRate(), p.getCommissionAmount(), p.getNetAmount(), p.getPayeeType(), p.getPayeeId(),
                    p.getFailureReason(), p.getCreatedAt(), p.getCapturedAt(), p.getReleasedAt(), p.getRefundedAt());
        }
    }

    public record WalletDto(BigDecimal balance, String currency) {}

    public record TransactionDto(UUID id, TxType type, TxReason reason, BigDecimal amount, BigDecimal balanceAfter,
                                 UUID paymentId, UUID withdrawalId, String description, Instant createdAt) {
        public static TransactionDto of(WalletTransaction t) {
            return new TransactionDto(t.getId(), t.getType(), t.getReason(), t.getAmount(), t.getBalanceAfter(),
                    t.getPaymentId(), t.getWithdrawalId(), t.getDescription(), t.getCreatedAt());
        }
    }

    public record WithdrawalDto(UUID id, UUID userId, BigDecimal amount, String currency, WithdrawalMethod method,
                                String destination, WithdrawalStatus status, String providerRef, String rejectReason,
                                Instant createdAt, Instant processedAt) {
        /** Vue du professionnel : destination masquée sauf les 4 derniers caractères. */
        public static WithdrawalDto masked(Withdrawal w) {
            return of(w, mask(w.getDestination()));
        }

        /** Vue de la finance : destination complète, nécessaire au versement. */
        public static WithdrawalDto full(Withdrawal w) {
            return of(w, w.getDestination());
        }

        private static WithdrawalDto of(Withdrawal w, String destination) {
            return new WithdrawalDto(w.getId(), w.getUserId(), w.getAmount(), w.getCurrency(), w.getMethod(),
                    destination, w.getStatus(), w.getProviderRef(), w.getRejectReason(), w.getCreatedAt(),
                    w.getProcessedAt());
        }

        private static String mask(String s) {
            return s.length() <= 4 ? "****" : "*".repeat(s.length() - 4) + s.substring(s.length() - 4);
        }
    }

    public record SummaryDto(Instant from, BigDecimal volume, BigDecimal commissions, BigDecimal subscriptionRevenue,
                             BigDecimal refunded, BigDecimal escrow, long pendingPayments, long pendingWithdrawals,
                             BigDecimal pendingWithdrawalsAmount, BigDecimal platformBalance, String currency) {
        public static SummaryDto of(PaymentStatsService.Summary s) {
            return new SummaryDto(s.from(), s.volume(), s.commissions(), s.subscriptionRevenue(), s.refunded(),
                    s.escrow(), s.pendingPayments(), s.pendingWithdrawals(), s.pendingWithdrawalsAmount(),
                    s.platformBalance(), s.currency());
        }
    }
}
