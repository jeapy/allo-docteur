package dev.jpzconception.allodocteur.pharmacy.application;

import dev.jpzconception.allodocteur.medicalrecord.application.MedicalAccessLogger;
import dev.jpzconception.allodocteur.medicalrecord.domain.AccessAction;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.AcceptRequest;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderSummaryDto;
import dev.jpzconception.allodocteur.pharmacy.domain.Delivery;
import dev.jpzconception.allodocteur.pharmacy.domain.DeliveryStatus;
import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import dev.jpzconception.allodocteur.pharmacy.domain.Pharmacy;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyMember;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrder;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrderItem;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.DeliveryRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyMemberRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyOrderItemRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyOrderRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Côté pharmacie : traitement des commandes par les pharmaciens rattachés, retrait ou livraison. */
@Service
@Transactional
public class PharmacyFulfillmentService {

    private final PharmacyOrderRepository orders;
    private final PharmacyOrderItemRepository items;
    private final PharmacyMemberRepository members;
    private final PharmacyRepository pharmacies;
    private final DeliveryRepository deliveries;
    private final DeliveryProvider provider;
    private final OrderAssembler assembler;
    private final MedicalAccessLogger accessLogger;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PharmacyFulfillmentService(PharmacyOrderRepository orders, PharmacyOrderItemRepository items,
                                      PharmacyMemberRepository members, PharmacyRepository pharmacies,
                                      DeliveryRepository deliveries, DeliveryProvider provider, OrderAssembler assembler,
                                      MedicalAccessLogger accessLogger, ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.members = members;
        this.pharmacies = pharmacies;
        this.deliveries = deliveries;
        this.provider = provider;
        this.assembler = assembler;
        this.accessLogger = accessLogger;
        this.events = events;
        this.clock = clock;
    }

    /** Commandes des pharmacies du pharmacien : résumés sans médicament. */
    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> list(AuthenticatedUser me, Collection<OrderStatus> statuses, int page, int size) {
        Set<UUID> ids = members.findByUserId(me.id()).stream().map(PharmacyMember::getPharmacyId).collect(Collectors.toSet());
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        if (ids.isEmpty()) return new PageImpl<>(List.of(), pageable, 0);
        Page<PharmacyOrder> found = statuses == null || statuses.isEmpty()
                ? orders.findByPharmacyIdIn(ids, pageable)
                : orders.findByPharmacyIdInAndStatusIn(ids, statuses, pageable);
        return new PageImpl<>(assembler.toSummaries(found.getContent()), pageable, found.getTotalElements());
    }

    /** Lecture du contenu de la commande : tracée dans le journal d'accès du dossier du patient. */
    public OrderDto get(AuthenticatedUser me, UUID id) {
        PharmacyOrder o = requireMemberOrder(me, id);
        accessLogger.granted(me, o.getRecordId(), AccessAction.VIEW_PHARMACY_ORDER, o.getId());
        return assembler.toDto(o, false);
    }

    public OrderDto accept(AuthenticatedUser me, UUID id, AcceptRequest req) {
        PharmacyOrder o = requireMemberOrder(me, id);
        List<PharmacyOrderItem> lines = items.findByOrderIdOrderByLineNumber(id);
        Set<Integer> unavailable = req.unavailableLines() == null ? Set.of() : Set.copyOf(req.unavailableLines());
        Set<Integer> known = lines.stream().map(PharmacyOrderItem::getLineNumber).collect(Collectors.toSet());
        if (!known.containsAll(unavailable)) {
            throw ApiException.badRequest("UNKNOWN_LINE", "Une ligne indiquée comme indisponible n'existe pas.");
        }
        if (unavailable.size() == lines.size()) {
            throw ApiException.badRequest("ALL_UNAVAILABLE", "Aucune ligne disponible : refusez la commande.");
        }
        o.accept(now(), req.quotedTotal(), blankToNull(req.message()));
        lines.forEach(l -> l.setAvailable(!unavailable.contains(l.getLineNumber())));
        publish(o);
        return assembler.toDto(o, false);
    }

    public OrderDto reject(AuthenticatedUser me, UUID id, String reason) {
        PharmacyOrder o = requireMemberOrder(me, id);
        o.reject(now(), reason.trim());
        publish(o);
        return assembler.toDto(o, false);
    }

    public OrderDto ready(AuthenticatedUser me, UUID id, String message) {
        PharmacyOrder o = requireMemberOrder(me, id);
        o.markReady(now(), blankToNull(message));
        publish(o);
        return assembler.toDto(o, false);
    }

    /** Retrait en officine : remise au patient. */
    public OrderDto handOver(AuthenticatedUser me, UUID id) {
        PharmacyOrder o = requireMemberOrder(me, id);
        o.handOver(now());
        publish(o);
        return assembler.toDto(o, false);
    }

    /** Confie la commande au prestataire de livraison ; le patient reçoit un code de remise à 6 chiffres. */
    public OrderDto dispatch(AuthenticatedUser me, UUID id) {
        PharmacyOrder o = requireMemberOrder(me, id);
        o.dispatch();
        Pharmacy ph = pharmacies.findById(o.getPharmacyId()).orElseThrow();
        DeliveryProvider.Dispatch d = provider.dispatch(new DeliveryProvider.Request(o.getNumber(), ph.getName(),
                ph.getAddress(), o.getDeliveryAddress(), o.getDeliveryLatitude(), o.getDeliveryLongitude(), o.getContactPhone()));
        Delivery delivery = new Delivery();
        delivery.setOrderId(o.getId());
        delivery.setProvider(d.provider());
        delivery.setTrackingCode(d.trackingCode());
        delivery.setCourierName(d.courierName());
        delivery.setCourierPhone(d.courierPhone());
        delivery.setHandoverCode(String.format("%06d", random.nextInt(1_000_000)));
        delivery.setDispatchedAt(now());
        deliveries.save(delivery);
        publish(o);
        return assembler.toDto(o, false);
    }

    /**
     * Clôt la livraison avec le code donné par le patient au livreur. Les essais erronés sont comptés même si la
     * requête échoue (pas de retour arrière), et bloqués au bout de {@value Delivery#MAX_CODE_ATTEMPTS}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public OrderDto deliver(AuthenticatedUser me, UUID id, String code) {
        PharmacyOrder o = requireMemberOrder(me, id);
        if (o.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw ApiException.conflict("INVALID_STATE", "Cette commande n'est pas en cours de livraison.");
        }
        Delivery d = deliveries.findByOrderId(id).orElseThrow();
        if (d.getCodeAttempts() >= Delivery.MAX_CODE_ATTEMPTS) {
            throw ApiException.conflict("CODE_ATTEMPTS_EXCEEDED",
                    "Trop d'essais : contactez le support pour clore cette livraison.");
        }
        boolean ok = MessageDigest.isEqual(d.getHandoverCode().getBytes(StandardCharsets.UTF_8),
                code.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            d.setCodeAttempts(d.getCodeAttempts() + 1);
            throw ApiException.badRequest("INVALID_HANDOVER_CODE", "Code de remise incorrect.");
        }
        Instant now = now();
        o.delivered(now);
        d.setStatus(DeliveryStatus.DELIVERED);
        d.setDeliveredAt(now);
        publish(o);
        return assembler.toDto(o, false);
    }

    public OrderDto deliveryFailed(AuthenticatedUser me, UUID id, String reason) {
        PharmacyOrder o = requireMemberOrder(me, id);
        Instant now = now();
        o.deliveryFailed(now);
        Delivery d = deliveries.findByOrderId(id).orElseThrow();
        d.setStatus(DeliveryStatus.FAILED);
        d.setFailureReason(reason.trim());
        publish(o);
        return assembler.toDto(o, false);
    }

    /** 404 si la commande n'existe pas ou n'est pas celle d'une pharmacie du pharmacien. */
    private PharmacyOrder requireMemberOrder(AuthenticatedUser me, UUID id) {
        return orders.findById(id)
                .filter(o -> members.existsByPharmacyIdAndUserId(o.getPharmacyId(), me.id()))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Commande introuvable."));
    }

    private void publish(PharmacyOrder o) {
        events.publishEvent(new PharmacyEvents.OrderStatusChanged(o.getId(), o.getAccountId(), o.getPharmacyId(), o.getStatus()));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
