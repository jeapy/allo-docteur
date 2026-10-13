package dev.jpzconception.allodocteur.medicalrecord.infrastructure;

import dev.jpzconception.allodocteur.medicalrecord.domain.EntryKind;
import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalRecordEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordEntryRepository extends JpaRepository<MedicalRecordEntry, UUID> {

    Page<MedicalRecordEntry> findByRecordId(UUID recordId, Pageable pageable);

    Page<MedicalRecordEntry> findByRecordIdAndKind(UUID recordId, EntryKind kind, Pageable pageable);

    Optional<MedicalRecordEntry> findByIdAndRecordId(UUID id, UUID recordId);
}
