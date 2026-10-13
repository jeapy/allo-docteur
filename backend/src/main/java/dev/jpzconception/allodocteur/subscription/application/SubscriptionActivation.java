package dev.jpzconception.allodocteur.subscription.application;

import dev.jpzconception.allodocteur.subscription.domain.Subscription;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionOrder;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionOrderRepository;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionRepository;
import dev.jpzconception.allodocteur.payment.application.PaymentEvents;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active la période achetée dès que le paiement est encaissé. Même plan : la période s'ajoute à la fin de l'abonnement
 * en cours. Autre plan : l'ancien est clos et le nouveau démarre aussitôt (sans prorata).
 */
@Component
@Transactional
public class SubscriptionActivation {

    private final SubscriptionOrderRepository orders;
    private final SubscriptionRepository subscriptions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SubscriptionActivation(SubscriptionOrderRepository orders, SubscriptionRepository subscriptions,
                                  ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.subscriptions = subscriptions;
        this.events = events;
        this.clock = clock;
    }

    @EventListener
    public void onCaptured(PaymentEvents.Captured e) {
        if (e.payableType() != PayableType.SUBSCRIPTION) {
            return;
        }
        SubscriptionOrder order = orders.findById(e.payableId()).orElseThrow();
        Instant now = clock.instant();
        subscriptions.expireStale(order.getUserId(), now);
        var live = subscriptions.findFirstByUserIdAndStatusInAndEndsAtAfter(order.getUserId(), SubscriptionStatus.LIVE, now);
        Subscription target;
        if (live.isPresent() && live.get().getPlan().getId().equals(order.getPlan().getId())) {
            target = live.get();
            target.setEndsAt(target.getEndsAt().plus(order.getDurationDays(), ChronoUnit.DAYS));
            target.setStatus(SubscriptionStatus.ACTIVE);
            target.setPaymentId(e.paymentId());
        } else {
            live.ifPresent(old -> {
                old.setStatus(SubscriptionStatus.CANCELLED);
                old.setCancelledAt(now);
                subscriptions.saveAndFlush(old);
            });
            target = new Subscription();
            target.setUserId(order.getUserId());
            target.setPlan(order.getPlan());
            target.setStatus(SubscriptionStatus.ACTIVE);
            target.setStartsAt(now);
            target.setEndsAt(now.plus(order.getDurationDays(), ChronoUnit.DAYS));
            target.setPaymentId(e.paymentId());
        }
        subscriptions.save(target);
        events.publishEvent(new SubscriptionEvents.Activated(target.getId(), order.getUserId(), order.getPlan().getCode(),
                target.getEndsAt()));
    }
}
