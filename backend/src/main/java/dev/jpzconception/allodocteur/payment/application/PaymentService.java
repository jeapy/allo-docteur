package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.application.PayableResolver.PayableInfo;
import dev.jpzconception.allodocteur.payment.application.PaymentProvider.ChargeRequest;
import dev.jpzconception.allodocteur.payment.application.PaymentProvider.ChargeResult;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import dev.jpzconception.allodocteur.payment.domain.Payment;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
import dev.jpzconception.allodocteur.payment.domain.TxReason;
import dev.jpzconception.allodocteur.payment.domain.WalletOwnerType;
import dev.jpzconception.allodocteur.payment.infrastructure.PaymentRepository;
import dev.jpzconception.allodocteur.payment.infrastructure.PaymentSpecifications;
import dev.jpzconception.allodocteur.admin.application.AuditService;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie d'un paiement : PENDING → CAPTURED (séquestre) → RELEASED (réparti entre le bénéficiaire et la plateforme)
 * ou REFUNDED. Les fonds d'un rendez-vous ou d'une commande restent en séquestre jusqu'à la prestation.
 */
@Service
@Transactional
public class PaymentService {

    /** Statuts d'un paiement « vivant » : au plus un par objet payable (index unique en base). */
    static final Set<PaymentStatus> LIVE = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.CAPTURED, PaymentStatus.RELEASED);

    private final PaymentRepository payments;
    private final Map<PayableType, PayableResolver> resolvers;
    private final PaymentProviders providers;
    private final WalletService wallets;
    private final CommissionPolicy commission;
    private final PaymentProperties props;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, List<PayableResolver> resolvers, PaymentProviders providers,
                          WalletService wallets, CommissionPolicy commission, PaymentProperties props,
                          AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.payments = payments;
        this.resolvers = resolvers.stream().collect(Collectors.toMap(PayableResolver::type, Function.identity()));
        this.providers = providers;
        this.wallets = wallets;
        this.commission = commission;
        this.props = props;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ initiation

    /** Paie un rendez-vous, une commande de pharmacie ou un abonnement. */
    public Payment pay(AuthenticatedUser me, PayableType type, UUID payableId, PaymentMethod method, String provider,
                       String payerReference, String idempotencyKey) {
        if (type == PayableType.WALLET_TOPUP) {
            throw ApiException.badRequest("INVALID_PAYABLE", "Utilisez la recharge de portefeuille.");
        }
        Payment replay = replay(me.id(), idempotencyKey);
        if (replay != null) {
            return replay;
        }
        PayableResolver resolver = resolver(type);
        PayableInfo info = resolver.resolve(me, payableId);
        return process(me.id(), type, payableId, info, method, provider, payerReference, idempotencyKey, resolver);
    }

    /** Recharge du portefeuille par Mobile Money, carte ou virement. */
    public Payment topUp(AuthenticatedUser me, BigDecimal amount, PaymentMethod method, String provider,
                         String payerReference, String idempotencyKey) {
        if (method == PaymentMethod.WALLET) {
            throw ApiException.badRequest("INVALID_METHOD", "Impossible de recharger le portefeuille avec lui-même.");
        }
        if (amount == null || amount.compareTo(props.minTopup()) < 0 || amount.compareTo(props.maxTopup()) > 0) {
            throw ApiException.badRequest("INVALID_AMOUNT",
                    "Le montant doit être compris entre " + props.minTopup().toPlainString() + " et "
                            + props.maxTopup().toPlainString() + ".");
        }
        Payment replay = replay(me.id(), idempotencyKey);
        if (replay != null) {
            return replay;
        }
        PayableInfo info = new PayableInfo(amount.setScale(0, java.math.RoundingMode.HALF_UP), "XOF", PayeeType.USER,
                me.id(), "Recharge du portefeuille");
        return process(me.id(), PayableType.WALLET_TOPUP, UUID.randomUUID(), info, method, provider, payerReference,
                idempotencyKey, null);
    }

    private Payment process(UUID payerId, PayableType type, UUID payableId, PayableInfo info, PaymentMethod method,
                            String providerCode, String payerReference, String idempotencyKey, PayableResolver resolver) {
        payments.findFirstByPayableTypeAndPayableIdAndStatusIn(type, payableId, LIVE).ifPresent(existing -> {
            throw ApiException.conflict(existing.getStatus() == PaymentStatus.PENDING ? "PAYMENT_PENDING" : "ALREADY_PAID",
                    existing.getStatus() == PaymentStatus.PENDING
                            ? "Un paiement est déjà en attente de confirmation."
                            : "Ce paiement a déjà été effectué.");
        });
        validateReference(method, payerReference);

        BigDecimal amount = info.amount();
        BigDecimal rate = commission.rateFor(type, info.payeeType(), info.payeeId());
        BigDecimal fee = commission.commission(amount, rate);

        Payment p = new Payment();
        p.setPayerId(payerId);
        p.setPayableType(type);
        p.setPayableId(payableId);
        p.setMethod(method);
        p.setAmount(amount);
        p.setCurrency(info.currency());
        p.setCommissionRate(rate);
        p.setCommissionAmount(fee);
        p.setNetAmount(amount.subtract(fee));
        p.setPayeeType(info.payeeType());
        p.setPayeeId(info.payeeId());
        p.setIdempotencyKey(blankToNull(idempotencyKey));
        p.setPhone(method == PaymentMethod.MOBILE_MONEY ? payerReference.trim() : null);

        PaymentProvider provider = null;
        if (method == PaymentMethod.WALLET) {
            p.setProvider("WALLET");
        } else {
            provider = providers.forMethod(method, providerCode);
            p.setProvider(provider.code());
        }
        try {
            payments.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("PAYMENT_EXISTS", "Un paiement existe déjà pour cet élément.");
        }

        if (method == PaymentMethod.WALLET) {
            wallets.debit(WalletOwnerType.USER, payerId, amount, TxReason.PAYMENT, p.getId(), null, info.description());
            capture(p, resolver);
            return p;
        }

        ChargeResult res = provider.charge(new ChargeRequest(p.getId(), amount, p.getCurrency(),
                payerReference, info.description()));
        p.setProviderRef(res.providerRef());
        switch (res.outcome()) {
            case SUCCEEDED -> capture(p, resolver);
            case FAILED -> fail(p, res.failureReason());
            case PENDING -> { /* confirmé plus tard : interrogation de l'opérateur ou validation de la finance */ }
        }
        return p;
    }

    // ------------------------------------------------------------------ suivi d'un paiement en attente

    /** Interroge l'opérateur pour un paiement resté en attente (Mobile Money, carte). */
    public Payment refresh(AuthenticatedUser me, UUID paymentId) {
        Payment p = payments.findById(paymentId).filter(x -> x.getPayerId().equals(me.id()))
                .orElseThrow(PaymentService::notFound);
        if (p.getStatus() != PaymentStatus.PENDING || p.getMethod() == PaymentMethod.WALLET) {
            return p;
        }
        ChargeResult res = providers.byCode(p.getProvider()).status(p.getProviderRef());
        switch (res.outcome()) {
            case SUCCEEDED -> capture(p, resolverOrNull(p.getPayableType()));
            case FAILED -> fail(p, res.failureReason());
            case PENDING -> { }
        }
        return p;
    }

    /** La finance confirme la réception d'un virement bancaire. */
    public Payment confirmTransfer(AuthenticatedUser admin, UUID paymentId) {
        Payment p = requirePendingTransfer(paymentId);
        capture(p, resolverOrNull(p.getPayableType()));
        audit.record(admin.id(), "PAYMENT_TRANSFER_CONFIRMED", "PAYMENT", p.getId().toString(), null);
        return p;
    }

    public Payment rejectTransfer(AuthenticatedUser admin, UUID paymentId, String reason) {
        Payment p = requirePendingTransfer(paymentId);
        fail(p, reason);
        audit.record(admin.id(), "PAYMENT_TRANSFER_REJECTED", "PAYMENT", p.getId().toString(), reason);
        return p;
    }

    /** Remboursement manuel d'un paiement encore en séquestre (litige, geste commercial). */
    public Payment adminRefund(AuthenticatedUser admin, UUID paymentId, String reason) {
        Payment p = payments.findById(paymentId).orElseThrow(PaymentService::notFound);
        if (p.getStatus() != PaymentStatus.CAPTURED) {
            throw ApiException.conflict("NOT_REFUNDABLE",
                    "Seul un paiement en séquestre peut être remboursé (statut actuel : " + p.getStatus() + ").");
        }
        refund(p);
        audit.record(admin.id(), "PAYMENT_REFUNDED", "PAYMENT", p.getId().toString(), reason);
        return p;
    }

    // ------------------------------------------------------------------ réactions aux prestations

    /** La prestation a été rendue : les fonds en séquestre sont répartis. Sans effet s'il n'y a rien en séquestre. */
    public void releaseFor(PayableType type, UUID payableId) {
        payments.findFirstByPayableTypeAndPayableIdAndStatusIn(type, payableId, EnumSet.of(PaymentStatus.CAPTURED))
                .ifPresent(this::release);
    }

    /** La prestation est annulée : les fonds en séquestre sont remboursés. */
    public void refundFor(PayableType type, UUID payableId) {
        payments.findFirstByPayableTypeAndPayableIdAndStatusIn(type, payableId, EnumSet.of(PaymentStatus.CAPTURED))
                .ifPresent(this::refund);
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public Payment get(AuthenticatedUser me, UUID id) {
        return payments.findById(id).filter(p -> p.involves(me.id())).orElseThrow(PaymentService::notFound);
    }

    @Transactional(readOnly = true)
    public Payment getAsAdmin(UUID id) {
        return payments.findById(id).orElseThrow(PaymentService::notFound);
    }

    @Transactional(readOnly = true)
    public Page<Payment> mine(AuthenticatedUser me, PaymentStatus status, int page, int size) {
        var spec = PaymentSpecifications.involving(me.id())
                .and(PaymentSpecifications.filter(null, status, null, null, null, null));
        return payments.findAll(spec, pageable(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Payment> adminList(UUID payerId, PaymentStatus status, PaymentMethod method, PayableType type,
                                   Instant from, Instant to, int page, int size) {
        return payments.findAll(PaymentSpecifications.filter(payerId, status, method, type, from, to),
                pageable(page, size));
    }

    // ------------------------------------------------------------------ transitions internes

    private void capture(Payment p, PayableResolver resolver) {
        p.capture(clock.instant());
        events.publishEvent(new PaymentEvents.Captured(p.getId(), p.getPayableType(), p.getPayableId(), p.getPayerId(),
                p.getAmount()));
        if (resolver == null || !resolver.escrow()) {
            release(p);
            return;
        }
        switch (resolver.state(p.getPayableId())) {
            case OPEN -> { /* reste en séquestre jusqu'à la prestation */ }
            case DELIVERED -> release(p);
            case CLOSED -> refund(p);
        }
    }

    private void fail(Payment p, String reason) {
        p.fail(reason);
        events.publishEvent(new PaymentEvents.Failed(p.getId(), p.getPayableType(), p.getPayableId(), p.getPayerId(), reason));
    }

    private void release(Payment p) {
        p.release(clock.instant());
        String label = p.getPayableType().name() + " " + p.getPayableId();
        BigDecimal net = p.getNetAmount();
        if (net.signum() > 0) {
            switch (p.getPayeeType()) {
                case USER -> wallets.credit(WalletOwnerType.USER, p.getPayeeId(), net,
                        p.getPayableType() == PayableType.WALLET_TOPUP ? TxReason.TOPUP : TxReason.EARNING,
                        p.getId(), null, label);
                case PHARMACY -> wallets.credit(WalletOwnerType.PHARMACY, p.getPayeeId(), net, TxReason.EARNING,
                        p.getId(), null, label);
                case PLATFORM -> wallets.credit(WalletOwnerType.PLATFORM, null, net, TxReason.SUBSCRIPTION,
                        p.getId(), null, label);
            }
        }
        if (p.getCommissionAmount().signum() > 0) {
            wallets.credit(WalletOwnerType.PLATFORM, null, p.getCommissionAmount(), TxReason.COMMISSION,
                    p.getId(), null, label);
        }
        events.publishEvent(new PaymentEvents.Released(p.getId(), p.getPayableType(), p.getPayableId(), p.getPayerId(),
                p.getAmount()));
    }

    private void refund(Payment p) {
        p.refund(clock.instant());
        if (p.getMethod() == PaymentMethod.WALLET) {
            wallets.credit(WalletOwnerType.USER, p.getPayerId(), p.getAmount(), TxReason.REFUND, p.getId(), null,
                    "Remboursement " + p.getPayableType());
        } else {
            providers.byCode(p.getProvider()).refund(p.getProviderRef(), p.getAmount(), p.getCurrency());
        }
        events.publishEvent(new PaymentEvents.Refunded(p.getId(), p.getPayableType(), p.getPayableId(), p.getPayerId(),
                p.getAmount()));
    }

    // ------------------------------------------------------------------ utilitaires

    private Payment requirePendingTransfer(UUID id) {
        Payment p = payments.findById(id).orElseThrow(PaymentService::notFound);
        if (p.getStatus() != PaymentStatus.PENDING || p.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw ApiException.conflict("INVALID_STATE", "Ce n'est pas un virement en attente.");
        }
        return p;
    }

    private Payment replay(UUID payerId, String key) {
        return key == null || key.isBlank() ? null
                : payments.findByPayerIdAndIdempotencyKey(payerId, key.trim()).orElse(null);
    }

    private PayableResolver resolver(PayableType type) {
        PayableResolver r = resolvers.get(type);
        if (r == null) {
            throw ApiException.badRequest("INVALID_PAYABLE", "Type de paiement non pris en charge.");
        }
        return r;
    }

    private PayableResolver resolverOrNull(PayableType type) {
        return resolvers.get(type);
    }

    private static void validateReference(PaymentMethod method, String ref) {
        switch (method) {
            case MOBILE_MONEY -> {
                if (ref == null || !ref.trim().matches("^\\+?[0-9]{8,15}$")) {
                    throw ApiException.badRequest("INVALID_PAYER_REFERENCE", "Numéro Mobile Money invalide.");
                }
            }
            case CARD -> {
                if (ref == null || ref.isBlank()) {
                    throw ApiException.badRequest("INVALID_PAYER_REFERENCE", "Jeton de carte requis.");
                }
            }
            case BANK_TRANSFER, WALLET -> { }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static ApiException notFound() {
        return ApiException.notFound("PAYMENT_NOT_FOUND", "Paiement introuvable.");
    }
}
