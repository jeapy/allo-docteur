package dev.jpzconception.allodocteur.sos.infrastructure;

import dev.jpzconception.allodocteur.sos.domain.SosAlert;
import dev.jpzconception.allodocteur.sos.domain.SosStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {

    Page<SosAlert> findByAccountId(UUID accountId, Pageable pageable);

    Page<SosAlert> findByStatusIn(Collection<SosStatus> statuses, Pageable pageable);

    long countByStatus(SosStatus status);

    /** Alertes ouvertes visibles d'un professionnel : sa ville, ou sans ville renseignée (diffusion large). */
    @Query("select a from SosAlert a where a.status = :status and (a.cityId is null or a.cityId = :cityId)")
    Page<SosAlert> findOpenFor(@Param("status") SosStatus status, @Param("cityId") Integer cityId, Pageable pageable);

    /** Prise en charge atomique : le premier professionnel l'emporte, les autres obtiennent 0 ligne. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update sos_alerts set status = 'ACCEPTED', assigned_professional_id = :pro, accepted_at = :now, "
            + "updated_at = :now, version = version + 1 where id = :id and status = 'OPEN'", nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("pro") UUID pro, @Param("now") Instant now);
}
