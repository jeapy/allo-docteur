package dev.jpzconception.allodocteur.professional.infrastructure;

import dev.jpzconception.allodocteur.professional.domain.AvailabilityRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

    List<AvailabilityRule> findByProfessionalIdOrderByDayOfWeekAscStartTimeAsc(UUID professionalId);

    @Modifying(flushAutomatically = true)
    @Query("delete from AvailabilityRule r where r.professionalId = :pid")
    void deleteAllFor(@Param("pid") UUID professionalId);
}
