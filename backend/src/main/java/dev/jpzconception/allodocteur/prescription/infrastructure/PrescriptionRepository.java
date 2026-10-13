package dev.jpzconception.allodocteur.prescription.infrastructure;

import dev.jpzconception.allodocteur.prescription.domain.Prescription;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    Page<Prescription> findByAccountId(UUID accountId, Pageable pageable);

    Page<Prescription> findByProfessionalId(UUID professionalId, Pageable pageable);

    Page<Prescription> findByRecordId(UUID recordId, Pageable pageable);

    @Query(value = "select nextval('prescription_number_seq')", nativeQuery = true)
    long nextNumber();
}
