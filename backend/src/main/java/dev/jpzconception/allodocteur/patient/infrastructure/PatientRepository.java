package dev.jpzconception.allodocteur.patient.infrastructure;

import dev.jpzconception.allodocteur.patient.domain.Patient;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, UUID> {}
