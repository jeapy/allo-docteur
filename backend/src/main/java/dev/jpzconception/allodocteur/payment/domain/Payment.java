package dev.jpzconception.allodocteur.payment.domain;

import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un paiement d'un objet payable (rendez-vous, commande, abonnement) ou une recharge de portefeuille. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "payer_id", nullable = false, updatable = false)
    private UUID payerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payable_type", nullable = false, updatable = false)
    private PayableType payableType;

    @Column(name = "payable_id", nullable = false, updatable = false)
    private UUID payableId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentMethod method;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_ref")
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency = "XOF";

    @Column(name = "commission_rate", nullable = false, updatable = false)
    private BigDecimal commissionRate = BigDecimal.ZERO;

    @Column(name = "commission_amount", nullable = false, updatable = false)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, updatable = false)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_type", nullable = false, updatable = false)
    private PayeeType payeeType;

    @Column(name = "payee_id", updatable = false)
    private UUID payeeId;

    @Column
    private String phone;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public void capture(Instant now) {
        require(status == PaymentStatus.PENDING, "capturer");
        status = PaymentStatus.CAPTURED;
        capturedAt = now;
    }

    public void fail(String reason) {
        require(status == PaymentStatus.PENDING, "faire échouer");
        status = PaymentStatus.FAILED;
        failureReason = reason == null ? null : reason.length() > 255 ? reason.substring(0, 255) : reason;
    }

    public void release(Instant now) {
        require(status == PaymentStatus.CAPTURED, "répartir");
        status = PaymentStatus.RELEASED;
        releasedAt = now;
    }

    public void refund(Instant now) {
        require(status == PaymentStatus.CAPTURED, "rembourser");
        status = PaymentStatus.REFUNDED;
        refundedAt = now;
    }

    public boolean involves(UUID userId) {
        return payerId.equals(userId) || (payeeType == PayeeType.USER && userId.equals(payeeId));
    }

    private void require(boolean ok, String action) {
        if (!ok) {
            throw ApiException.conflict("INVALID_STATE", "Impossible de " + action + " un paiement au statut " + status + ".");
        }
    }
}
