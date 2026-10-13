package dev.jpzconception.allodocteur.medicalrecord.application;

import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalRecord;
import dev.jpzconception.allodocteur.medicalrecord.infrastructure.MedicalRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le dossier d'une personne à la première demande. Transaction indépendante : le dossier est validé avant d'être
 * référencé par le journal d'accès (qui peut s'écrire dans une autre transaction). Idempotent et sûr en concurrence.
 */
@Component
public class MedicalRecordProvisioner {

    private final MedicalRecordRepository records;
    private final Clock clock;

    public MedicalRecordProvisioner(MedicalRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID ensure(UUID accountId, UUID dependentId) {
        Instant now = Instant.now(clock);
        if (dependentId == null) {
            records.insertSelfIfAbsent(UUID.randomUUID(), accountId, now);
            return records.findSelf(accountId).map(MedicalRecord::getId).orElseThrow();
        }
        records.insertDependentIfAbsent(UUID.randomUUID(), accountId, dependentId, now);
        return records.findByDependentId(dependentId).map(MedicalRecord::getId).orElseThrow();
    }
}
