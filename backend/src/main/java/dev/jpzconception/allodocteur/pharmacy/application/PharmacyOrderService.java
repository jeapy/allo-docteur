package dev.jpzconception.allodocteur.pharmacy.application;

import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderRequest;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderSummaryDto;
import dev.jpzconception.allodocteur.pharmacy.domain.Fulfillment;
import dev.jpzconception.allodocteur.pharmacy.domain.Pharmacy;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrder;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrderItem;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyOrderItemRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyOrderRepository;
import dev.jpzconception.allodocteur.prescription.application.PrescriptionAccess;
import dev.jpzconception.allodocteur.prescription.application.PrescriptionAccess.Line;
import dev.jpzconception.allodocteur.prescription.application.PrescriptionAccess.Orderable;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Côté patient : transmettre une ordonnance à une pharmacie et suivre la commande. */
@Service
@Transactional
public class PharmacyOrderService {

    private final PharmacyOrderRepository orders;
    private final PharmacyOrderItemRepository items;
    private final PrescriptionAccess prescriptions;
    private final PharmacyService pharmacies;
    private final OrderAssembler assembler;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PharmacyOrderService(PharmacyOrderRepository orders, PharmacyOrderItemRepository items,
                                PrescriptionAccess prescriptions, PharmacyService pharmacies, OrderAssembler assembler,
                                ApplicationEventPublisher events, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.prescriptions = prescriptions;
        this.pharmacies = pharmacies;
        this.assembler = assembler;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Transmet l'ordonnance à la pharmacie. C'est un acte volontaire du patient : la pharmacie reçoit une copie des
     * lignes de l'ordonnance (et rien d'autre du dossier).
     */
    public OrderDto place(AuthenticatedUser me, OrderRequest req) {
        Orderable rx = prescriptions.requireOrderable(me.id(), req.prescriptionId());
        Pharmacy ph = pharmacies.require(req.pharmacyId());
        if (!ph.isActive()) throw ApiException.notFound("PHARMACY_NOT_FOUND", "Pharmacie introuvable.");

        String address = req.deliveryAddress() == null || req.deliveryAddress().isBlank() ? null : req.deliveryAddress().trim();
        if (req.fulfillment() == Fulfillment.DELIVERY) {
            if (!ph.isOffersDelivery()) {
                throw ApiException.badRequest("DELIVERY_NOT_OFFERED", "Cette pharmacie ne livre pas.");
            }
            if (address == null) {
                throw ApiException.badRequest("ADDRESS_REQUIRED", "L'adresse de livraison est obligatoire.");
            }
        }
        if ((req.deliveryLatitude() == null) != (req.deliveryLongitude() == null)) {
            throw ApiException.badRequest("INVALID_COORDINATES", "Indiquez la latitude et la longitude, ou aucune.");
        }
        boolean delivery = req.fulfillment() == Fulfillment.DELIVERY;

        PharmacyOrder o = new PharmacyOrder();
        o.setNumber(formatNumber(Instant.now(clock), orders.nextNumber()));
        o.setPrescriptionId(rx.id());
        o.setRecordId(rx.recordId());
        o.setAccountId(rx.accountId());
        o.setDependentId(rx.dependentId());
        o.setBeneficiaryName(rx.beneficiaryName());
        o.setPharmacyId(ph.getId());
        o.setFulfillment(req.fulfillment());
        o.setContactPhone(req.contactPhone().trim());
        o.setPatientNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        o.setCurrency(ph.getCurrency());
        if (delivery) {
            o.setDeliveryAddress(address);
            o.setDeliveryLatitude(req.deliveryLatitude());
            o.setDeliveryLongitude(req.deliveryLongitude());
            o.setDeliveryFee(ph.getDeliveryFee());
        }
        try {
            orders.saveAndFlush(o);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ORDER_ALREADY_ACTIVE", "Une commande est déjà en cours pour cette ordonnance.");
        }
        for (Line l : rx.lines()) {
            PharmacyOrderItem i = new PharmacyOrderItem();
            i.setOrderId(o.getId());
            i.setLineNumber(l.lineNumber());
            i.setMedicationName(l.medicationName());
            i.setDosage(l.dosage());
            i.setFrequency(l.frequency());
            i.setQuantity(l.quantity());
            i.setInstructions(l.instructions());
            items.save(i);
        }
        publish(o);
        return assembler.toDto(o, true);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> mine(AuthenticatedUser me, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<PharmacyOrder> found = orders.findByAccountId(me.id(), pageable);
        return new PageImpl<>(assembler.toSummaries(found.getContent()), pageable, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public OrderDto get(AuthenticatedUser me, UUID id) {
        return assembler.toDto(requireOwn(me, id), true);
    }

    /** Annulation possible tant que la pharmacie n'a pas déclaré la commande prête. */
    public OrderDto cancel(AuthenticatedUser me, UUID id) {
        PharmacyOrder o = requireOwn(me, id);
        o.cancel(Instant.now(clock));
        publish(o);
        return assembler.toDto(o, true);
    }

    private PharmacyOrder requireOwn(AuthenticatedUser me, UUID id) {
        return orders.findById(id).filter(o -> o.getAccountId().equals(me.id()))
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Commande introuvable."));
    }

    private void publish(PharmacyOrder o) {
        events.publishEvent(new PharmacyEvents.OrderStatusChanged(o.getId(), o.getAccountId(), o.getPharmacyId(), o.getStatus()));
    }

    static String formatNumber(Instant now, long seq) {
        return String.format("CMD-%d-%06d", now.atZone(java.time.ZoneOffset.UTC).getYear(), seq);
    }
}
