package dev.jpzconception.allodocteur.reference.infrastructure;

import dev.jpzconception.allodocteur.reference.domain.Specialty;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, Integer> {
    List<Specialty> findByActiveTrueOrderByNameFrAsc();
}
