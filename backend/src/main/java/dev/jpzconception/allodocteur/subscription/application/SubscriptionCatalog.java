package dev.jpzconception.allodocteur.subscription.application;

import dev.jpzconception.allodocteur.subscription.domain.PlanAudience;
import dev.jpzconception.allodocteur.subscription.domain.Subscription;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionPlan;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionPlanRepository;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lecture seule : plans proposés et abonnement en cours (utilisé aussi par le calcul de commission). */
@Service
@Transactional(readOnly = true)
public class SubscriptionCatalog {

    private final SubscriptionPlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public SubscriptionCatalog(SubscriptionPlanRepository plans, SubscriptionRepository subscriptions, Clock clock) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    public List<SubscriptionPlan> plansFor(PlanAudience audience) {
        return plans.findByAudienceAndActiveTrueOrderByPriceAsc(audience);
    }

    public Optional<Subscription> current(UUID userId) {
        return subscriptions.findFirstByUserIdAndStatusInAndEndsAtAfter(userId, SubscriptionStatus.LIVE, clock.instant());
    }

    /** Taux de commission imposé par le plan en cours du professionnel, s'il en définit un. */
    public Optional<BigDecimal> commissionRateOverride(UUID userId) {
        return current(userId).map(s -> s.getPlan().getCommissionRate());
    }
}
