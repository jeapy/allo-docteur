package dev.jpzconception.allodocteur.professional.infrastructure;

import dev.jpzconception.allodocteur.professional.domain.Professional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfessionalRepository extends JpaRepository<Professional, UUID>, JpaSpecificationExecutor<Professional> {

    @Override
    @EntityGraph(attributePaths = {"user", "type", "city", "district"})
    Page<Professional> findAll(Specification<Professional> spec, Pageable pageable);

    @Query("""
            select p.id from Professional p
            where p.verificationStatus = dev.jpzconception.allodocteur.professional.domain.VerificationStatus.APPROVED
              and p.offersHomeVisit = true and p.deletedAt is null and p.city.id = :cityId""")
    java.util.List<UUID> homeVisitIdsInCity(@Param("cityId") Integer cityId);

    @Query("""
            select p.id from Professional p
            where p.verificationStatus = dev.jpzconception.allodocteur.professional.domain.VerificationStatus.APPROVED
              and p.offersHomeVisit = true and p.deletedAt is null""")
    java.util.List<UUID> homeVisitIds();

    @Query("select count(p) > 0 from Professional p where lower(p.licenseNumber) = lower(:license) and p.id <> :excludeId")
    boolean licenseUsedByOther(@Param("license") String license, @Param("excludeId") UUID excludeId);

    @Query("select count(p) > 0 from Professional p where lower(p.licenseNumber) = lower(:license)")
    boolean licenseExists(@Param("license") String license);
}
