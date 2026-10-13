package dev.jpzconception.allodocteur.subscription.infrastructure;

import dev.jpzconception.allodocteur.subscription.domain.PlanAudience;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findByAudienceAndActiveTrueOrderByPriceAsc(PlanAudience audience);

    List<SubscriptionPlan> findAllByOrderByAudienceAscPriceAsc();
}
