package dev.jpzconception.allodocteur.reference.infrastructure;

import dev.jpzconception.allodocteur.reference.domain.District;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, Integer> {
    List<District> findByActiveTrueOrderByNameAsc();
}
