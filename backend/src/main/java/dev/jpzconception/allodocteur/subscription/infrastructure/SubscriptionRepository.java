package dev.jpzconception.allodocteur.subscription.infrastructure;

import dev.jpzconception.allodocteur.subscription.domain.Subscription;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findFirstByUserIdAndStatusInAndEndsAtAfter(UUID userId, Collection<SubscriptionStatus> statuses,
                                                                      Instant now);

    boolean existsByUserId(UUID userId);

    Page<Subscription> findByUserId(UUID userId, Pageable pageable);

    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);

    /**
     * Passe en EXPIRED les abonnements en cours dont la date de fin est atteinte (pour un utilisateur). Sans vidage du
     * contexte de persistance : appelé au milieu d'une transaction de paiement qui garde d'autres entités gérées.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Subscription s set s.status = dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus.EXPIRED,
                   s.updatedAt = :now, s.version = s.version + 1
            where s.userId = :userId and s.endsAt <= :now
              and s.status in (dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus.TRIAL,
                               dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus.ACTIVE)""")
    int expireStale(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Même chose pour tous les utilisateurs (tâche périodique). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Subscription s set s.status = dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus.EXPIRED,
                   s.updatedAt = :now, s.version = s.version + 1
            where s.endsAt <= :now
              and s.status in (dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus.TRIAL,
                               dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus.ACTIVE)""")
    int expireAll(@Param("now") Instant now);
}
