package dev.jpzconception.allodocteur.reference.infrastructure;

import dev.jpzconception.allodocteur.reference.domain.ProfessionalType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessionalTypeRepository extends JpaRepository<ProfessionalType, Integer> {
    boolean existsByCodeIgnoreCase(String code);
}
