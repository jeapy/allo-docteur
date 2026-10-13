package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.domain.Pharmacy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PharmacyRepository extends JpaRepository<Pharmacy, UUID>, JpaSpecificationExecutor<Pharmacy> {

    List<Pharmacy> findByActiveTrueAndLatitudeIsNotNull();
}
