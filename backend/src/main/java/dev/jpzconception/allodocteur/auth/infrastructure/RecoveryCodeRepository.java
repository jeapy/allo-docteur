package dev.jpzconception.allodocteur.auth.infrastructure;

import dev.jpzconception.allodocteur.auth.domain.RecoveryCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

    Optional<RecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    @Modifying
    @Query("delete from RecoveryCode c where c.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
