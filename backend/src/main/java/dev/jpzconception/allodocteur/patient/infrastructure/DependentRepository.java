package dev.jpzconception.allodocteur.patient.infrastructure;

import dev.jpzconception.allodocteur.patient.domain.Dependent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DependentRepository extends JpaRepository<Dependent, UUID> {
    List<Dependent> findByGuardianIdOrderByFirstNameAsc(UUID guardianId);
    Optional<Dependent> findByIdAndGuardianId(UUID id, UUID guardianId);
    long countByGuardianId(UUID guardianId);
}
