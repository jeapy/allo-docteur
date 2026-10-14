package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.domain.Pharmacy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PharmacyRepository extends JpaRepository<Pharmacy, UUID>, JpaSpecificationExecutor<Pharmacy> {

    List<Pharmacy> findByActiveTrueAndLatitudeIsNotNull();

    /** Pharmacies actives géolocalisées dans un rectangle (index ix_pharmacies_geo). */
    @Query("""
            select p from Pharmacy p
            where p.active = true and (:onDutyOnly = false or p.onDuty = true)
              and p.latitude between :minLat and :maxLat and p.longitude between :minLon and :maxLon""")
    List<Pharmacy> findInBox(@Param("onDutyOnly") boolean onDutyOnly,
                             @Param("minLat") java.math.BigDecimal minLat, @Param("maxLat") java.math.BigDecimal maxLat,
                             @Param("minLon") java.math.BigDecimal minLon, @Param("maxLon") java.math.BigDecimal maxLon);
}
