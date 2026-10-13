package dev.jpzconception.allodocteur.subscription.application;

import dev.jpzconception.allodocteur.subscription.domain.PlanAudience;
import dev.jpzconception.allodocteur.subscription.domain.Subscription;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionOrder;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionPlan;
import dev.jpzconception.allodocteur.subscription.domain.SubscriptionStatus;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionOrderRepository;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionPlanRepository;
import dev.jpzconception.allodocteur.subscription.infrastructure.SubscriptionRepository;
import dev.jpzconception.allodocteur.payment.application.PaymentService;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.Payment;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.subscription.api.SubscriptionDtos.PlanDto;
import dev.jpzconception.allodocteur.subscription.api.SubscriptionDtos.SubscriptionDto;
import dev.jpzconception.allodocteur.user.domain.User;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import dev.jpzconception.allodocteur.user.domain.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Abonnements des professionnels et Premium des patients. Enregistrés et facturés, sans blocage métier pour l'instant. */
@Service
@Transactional
public class SubscriptionService {

    private final SubscriptionPlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionOrderRepository orders;
    private final SubscriptionCatalog catalog;
    private final SubscriptionProperties props;
    private final PaymentService payments;
    private final AuditService audit;
    private final Clock clock;
    private final UserRepository users;

    public SubscriptionService(SubscriptionPlanRepository plans, SubscriptionRepository subscriptions,
                               SubscriptionOrderRepository orders, SubscriptionCatalog catalog,
                               SubscriptionProperties props, PaymentService payments, AuditService audit, Clock clock,
                               UserRepository users) {
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.orders = orders;
        this.catalog = catalog;
        this.props = props;
        this.payments = payments;
        this.audit = audit;
        this.clock = clock;
        this.users = users;
    }

    // ------------------------------------------------------------------ utilisateur

    @Transactional(readOnly = true)
    public List<PlanDto> plansFor(AuthenticatedUser me) {
        return catalog.plansFor(audienceOf(me)).stream().map(SubscriptionService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionDto> current(AuthenticatedUser me) {
        return catalog.current(me.id()).map(s -> toDto(s, null));
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionDto> history(AuthenticatedUser me, int page, int size) {
        return subscriptions.findByUserId(me.id(), pageable(page, size, "startsAt")).map(s -> toDto(s, null));
    }

    /** Essai gratuit du professionnel, une seule fois. */
    public SubscriptionDto startTrial(AuthenticatedUser me) {
        if (me.role() != Role.PROFESSIONAL) {
            throw ApiException.forbidden("ACCESS_DENIED", "L'essai est réservé aux professionnels.");
        }
        if (subscriptions.existsByUserId(me.id())) {
            throw ApiException.conflict("TRIAL_NOT_AVAILABLE", "Vous avez déjà bénéficié d'un abonnement ou d'un essai.");
        }
        SubscriptionPlan plan = plans.findByCode(props.trialPlanCode()).orElseThrow();
        Instant now = clock.instant();
        Subscription s = new Subscription();
        s.setUserId(me.id());
        s.setPlan(plan);
        s.setStatus(SubscriptionStatus.TRIAL);
        s.setStartsAt(now);
        s.setEndsAt(now.plus(props.trialDays(), ChronoUnit.DAYS));
        return toDto(subscriptions.save(s), null);
    }

    /** Commande une période et la paie ; l'abonnement s'active à l'encaissement. */
    public Payment purchase(AuthenticatedUser me, String planCode, PaymentMethod method, String provider,
                            String payerReference, String idempotencyKey) {
        SubscriptionPlan plan = plans.findByCode(planCode).filter(SubscriptionPlan::isActive)
                .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Formule introuvable."));
        if (plan.getAudience() != audienceOf(me)) {
            throw ApiException.forbidden("PLAN_NOT_AVAILABLE", "Cette formule n'est pas destinée à votre profil.");
        }
        if (plan.getPrice().signum() <= 0) {
            throw ApiException.conflict("PLAN_NOT_PURCHASABLE", "Cette formule n'est pas payante.");
        }
        SubscriptionOrder order = new SubscriptionOrder();
        order.setUserId(me.id());
        order.setPlan(plan);
        order.setPrice(plan.getPrice());
        order.setCurrency(plan.getCurrency());
        order.setDurationDays(plan.getDurationDays());
        orders.saveAndFlush(order);
        return payments.pay(me, PayableType.SUBSCRIPTION, order.getId(), method, provider, payerReference, idempotencyKey);
    }

    /** Résiliation immédiate, sans remboursement. */
    public SubscriptionDto cancel(AuthenticatedUser me) {
        Instant now = clock.instant();
        Subscription s = catalog.current(me.id())
                .orElseThrow(() -> ApiException.notFound("NO_ACTIVE_SUBSCRIPTION", "Aucun abonnement en cours."));
        s.setStatus(SubscriptionStatus.CANCELLED);
        s.setCancelledAt(now);
        return toDto(s, null);
    }

    // ------------------------------------------------------------------ administration

    @Transactional(readOnly = true)
    public Page<SubscriptionDto> adminList(SubscriptionStatus status, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "endsAt"));
        Page<Subscription> result = status == null ? subscriptions.findAll(pageable)
                : subscriptions.findByStatus(status, pageable);
        Map<UUID, User> byId = users.findAllById(result.stream().map(Subscription::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return result.map(s -> toDto(s, displayName(byId.get(s.getUserId()))));
    }

    @Transactional(readOnly = true)
    public List<PlanDto> adminPlans() {
        return plans.findAllByOrderByAudienceAscPriceAsc().stream().map(SubscriptionService::toDto).toList();
    }

    public record PlanUpdate(String name, String description, BigDecimal price, int durationDays,
                             BigDecimal commissionRate, boolean active) {}

    public PlanDto updatePlan(AuthenticatedUser admin, UUID id, PlanUpdate req) {
        SubscriptionPlan p = plans.findById(id)
                .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Formule introuvable."));
        if (p.getAudience() == PlanAudience.PATIENT && req.commissionRate() != null) {
            throw ApiException.badRequest("INVALID_COMMISSION", "Un plan patient n'a pas de taux de commission.");
        }
        p.setName(req.name());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setDurationDays(req.durationDays());
        p.setCommissionRate(req.commissionRate());
        p.setActive(req.active());
        audit.record(admin.id(), "SUBSCRIPTION_PLAN_UPDATED", "SUBSCRIPTION_PLAN", p.getId().toString(), p.getCode());
        return toDto(p);
    }

    static PlanDto toDto(SubscriptionPlan p) {
        return new PlanDto(p.getId(), p.getCode(), p.getAudience(), p.getName(), p.getDescription(), p.getPrice(),
                p.getCurrency(), p.getDurationDays(), p.getCommissionRate(), p.isActive());
    }

    static SubscriptionDto toDto(Subscription s, String userName) {
        return new SubscriptionDto(s.getId(), s.getUserId(), userName, s.getPlan().getCode(), s.getPlan().getName(),
                s.getStatus(), s.getStartsAt(), s.getEndsAt(), s.getPaymentId());
    }

    private static String displayName(User u) {
        if (u == null) return null;
        String full = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return full.isEmpty() ? u.getEmail() : full;
    }

    private static PlanAudience audienceOf(AuthenticatedUser me) {
        return switch (me.role()) {
            case PROFESSIONAL -> PlanAudience.PROFESSIONAL;
            case PATIENT -> PlanAudience.PATIENT;
            default -> throw ApiException.forbidden("ACCESS_DENIED", "Les administrateurs n'ont pas d'abonnement.");
        };
    }

    private static PageRequest pageable(int page, int size, String sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, sort));
    }
}
