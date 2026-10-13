package dev.jpzconception.allodocteur.payment.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** Demande de retrait des gains d'un professionnel ; le montant est débité du portefeuille dès la demande. */
@Entity
@Table(name = "withdrawals")
@Getter
@Setter
@NoArgsConstructor
public class Withdrawal extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency = "XOF";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private WithdrawalMethod method;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawalStatus status = WithdrawalStatus.REQUESTED;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    public void markPaid(UUID admin, Instant now, String ref) {
        require(status == WithdrawalStatus.REQUESTED, "payer");
        status = WithdrawalStatus.PAID;
        processedBy = admin;
        processedAt = now;
        providerRef = ref;
    }

    public void reject(UUID admin, Instant now, String reason) {
        require(status == WithdrawalStatus.REQUESTED, "rejeter");
        status = WithdrawalStatus.REJECTED;
        processedBy = admin;
        processedAt = now;
        rejectReason = reason;
    }

    private void require(boolean ok, String action) {
        if (!ok) {
            throw ApiException.conflict("INVALID_STATE", "Impossible de " + action + " un retrait au statut " + status + ".");
        }
    }
}
