package dev.jpzconception.allodocteur.sos.infrastructure;

import dev.jpzconception.allodocteur.sos.domain.EmergencyAccessGrant;
import dev.jpzconception.allodocteur.sos.domain.SosStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmergencyAccessGrantRepository extends JpaRepository<EmergencyAccessGrant, UUID> {

    Optional<EmergencyAccessGrant> findByAlertIdAndProfessionalId(UUID alertId, UUID professionalId);

    @Query("select count(g) > 0 from EmergencyAccessGrant g, SosAlert a where g.alertId = a.id "
            + "and g.professionalId = :pro and a.accountId = :account and a.dependentId is null "
            + "and a.status = :status and g.expiresAt > :now")
    boolean activeForSelf(@Param("pro") UUID pro, @Param("account") UUID account, @Param("status") SosStatus status,
                          @Param("now") Instant now);

    @Query("select count(g) > 0 from EmergencyAccessGrant g, SosAlert a where g.alertId = a.id "
            + "and g.professionalId = :pro and a.dependentId = :dependent "
            + "and a.status = :status and g.expiresAt > :now")
    boolean activeForDependent(@Param("pro") UUID pro, @Param("dependent") UUID dependent,
                               @Param("status") SosStatus status, @Param("now") Instant now);
}
