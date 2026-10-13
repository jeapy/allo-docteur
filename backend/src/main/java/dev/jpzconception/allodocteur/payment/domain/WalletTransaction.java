package dev.jpzconception.allodocteur.payment.domain;

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
import org.hibernate.annotations.Immutable;

/** Ligne du grand livre : jamais modifiée ni supprimée (garanti aussi par un trigger PostgreSQL). */
@Entity
@Immutable
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
public class WalletTransaction {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxReason reason;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "withdrawal_id")
    private UUID withdrawalId;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
