package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.Payment;
import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findFirstByPayableTypeAndPayableIdAndStatusIn(PayableType type, UUID payableId,
                                                                    Collection<PaymentStatus> statuses);

    Optional<Payment> findByPayerIdAndIdempotencyKey(UUID payerId, String idempotencyKey);

    boolean existsByPayableTypeAndPayableIdAndStatusIn(PayableType type, UUID payableId, Collection<PaymentStatus> statuses);

    /** Chiffre d'affaires encaissé sur les services (hors recharges de portefeuille) depuis une date. */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status in (dev.jpzconception.allodocteur.payment.domain.PaymentStatus.CAPTURED,
                               dev.jpzconception.allodocteur.payment.domain.PaymentStatus.RELEASED)
              and p.payableType <> dev.jpzconception.allodocteur.payment.domain.PayableType.WALLET_TOPUP
              and p.capturedAt >= :from""")
    BigDecimal volumeSince(@Param("from") Instant from);

    @Query("""
            select coalesce(sum(p.commissionAmount), 0) from Payment p
            where p.status = dev.jpzconception.allodocteur.payment.domain.PaymentStatus.RELEASED
              and p.releasedAt >= :from""")
    BigDecimal commissionsSince(@Param("from") Instant from);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = dev.jpzconception.allodocteur.payment.domain.PaymentStatus.RELEASED
              and p.payableType = dev.jpzconception.allodocteur.payment.domain.PayableType.SUBSCRIPTION
              and p.releasedAt >= :from""")
    BigDecimal subscriptionRevenueSince(@Param("from") Instant from);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = dev.jpzconception.allodocteur.payment.domain.PaymentStatus.REFUNDED
              and p.refundedAt >= :from""")
    BigDecimal refundedSince(@Param("from") Instant from);

    /** Fonds actuellement en séquestre. */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = dev.jpzconception.allodocteur.payment.domain.PaymentStatus.CAPTURED""")
    BigDecimal escrowTotal();

    long countByStatus(PaymentStatus status);
}
