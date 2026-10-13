package dev.jpzconception.allodocteur.subscription.application;

import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Marque comme expirés les abonnements arrivés à échéance (la lecture reste correcte même sans cette tâche). */
@Component
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public SubscriptionExpiryJob(SubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    @Transactional
    public void expire() {
        int n = subscriptions.expireAll(clock.instant());
        if (n > 0) {
            log.info("{} abonnement(s) expiré(s)", n);
        }
    }
}
