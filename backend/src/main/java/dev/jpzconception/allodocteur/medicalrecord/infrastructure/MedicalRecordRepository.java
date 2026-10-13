package dev.jpzconception.allodocteur.medicalrecord.infrastructure;

import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    @Query("select r from MedicalRecord r where r.accountId = :account and r.dependentId is null")
    Optional<MedicalRecord> findSelf(@Param("account") UUID accountId);

    Optional<MedicalRecord> findByDependentId(UUID dependentId);

    /** Création idempotente : deux requêtes simultanées ne produisent qu'un dossier. */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into medical_records (id, account_id, created_at, updated_at, version)
            values (:id, :account, :now, :now, 0)
            on conflict (account_id) where dependent_id is null do nothing
            """)
    int insertSelfIfAbsent(@Param("id") UUID id, @Param("account") UUID accountId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            insert into medical_records (id, account_id, dependent_id, created_at, updated_at, version)
            values (:id, :account, :dependent, :now, :now, 0)
            on conflict (dependent_id) where dependent_id is not null do nothing
            """)
    int insertDependentIfAbsent(@Param("id") UUID id, @Param("account") UUID accountId,
                                @Param("dependent") UUID dependentId, @Param("now") Instant now);
}
