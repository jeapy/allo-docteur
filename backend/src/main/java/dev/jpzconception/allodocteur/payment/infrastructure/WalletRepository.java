package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.domain.Wallet;
import dev.jpzconception.allodocteur.payment.domain.WalletOwnerType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerTypeAndOwnerId(WalletOwnerType ownerType, UUID ownerId);

    Optional<Wallet> findByOwnerTypeAndOwnerIdIsNull(WalletOwnerType ownerType);

    /** Verrouille la ligne : tout mouvement de fonds passe par ces deux requêtes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.ownerType = :type and w.ownerId = :owner")
    Optional<Wallet> lockByOwner(@Param("type") WalletOwnerType type, @Param("owner") UUID owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.ownerType = :type and w.ownerId is null")
    Optional<Wallet> lockPlatform(@Param("type") WalletOwnerType type);

    /** Identifiant seul (projection scalaire) : n'attache pas l'entité au contexte, donc pas de solde périmé. */
    @Query("select w.id from Wallet w where w.ownerType = :type and w.ownerId = :owner")
    Optional<UUID> findIdByOwner(@Param("type") WalletOwnerType type, @Param("owner") UUID owner);

    /** Création idempotente (sans erreur de contrainte, qui avorterait la transaction PostgreSQL). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO wallets (id, owner_type, owner_id, currency, balance, created_at, updated_at, version)
            VALUES (:id, :type, :owner, 'XOF', 0, now(), now(), 0)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("type") String type, @Param("owner") UUID owner);
}
