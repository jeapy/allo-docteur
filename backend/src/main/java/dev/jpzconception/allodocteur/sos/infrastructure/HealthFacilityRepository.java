package dev.jpzconception.allodocteur.sos.infrastructure;

import dev.jpzconception.allodocteur.sos.domain.HealthFacility;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthFacilityRepository extends JpaRepository<HealthFacility, UUID> {

    List<HealthFacility> findByActiveTrueAndEmergencyServiceTrue();

    /** Structures d'urgence actives dans un rectangle (index ix_facilities_geo). */
    @Query("""
            select f from HealthFacility f
            where f.active = true and f.emergencyService = true
              and f.latitude between :minLat and :maxLat and f.longitude between :minLon and :maxLon""")
    List<HealthFacility> findEmergencyInBox(@Param("minLat") java.math.BigDecimal minLat,
                                            @Param("maxLat") java.math.BigDecimal maxLat,
                                            @Param("minLon") java.math.BigDecimal minLon,
                                            @Param("maxLon") java.math.BigDecimal maxLon);

    @Query("select f from HealthFacility f where (:activeOnly = false or f.active = true)")
    Page<HealthFacility> search(@Param("activeOnly") boolean activeOnly, Pageable pageable);

    @Query("select f from HealthFacility f where f.cityId = :cityId and f.active = true")
    Page<HealthFacility> searchByCity(@Param("cityId") Integer cityId, Pageable pageable);
}
