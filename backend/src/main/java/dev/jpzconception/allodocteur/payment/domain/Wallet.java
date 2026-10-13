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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Portefeuille interne d'un utilisateur, d'une pharmacie ou de la plateforme. Le solde ne peut jamais être négatif. */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false)
    private WalletOwnerType ownerType;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private String currency = "XOF";

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    public void credit(BigDecimal amount) {
        positive(amount);
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        positive(amount);
        if (balance.compareTo(amount) < 0) {
            throw ApiException.conflict("INSUFFICIENT_FUNDS", "Solde insuffisant.");
        }
        balance = balance.subtract(amount);
    }

    private static void positive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Le montant doit être strictement positif.");
        }
    }
}
