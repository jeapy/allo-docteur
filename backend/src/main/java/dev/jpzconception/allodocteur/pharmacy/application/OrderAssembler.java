package dev.jpzconception.allodocteur.pharmacy.application;

import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.DeliveryDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderItemDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.OrderSummaryDto;
import dev.jpzconception.allodocteur.pharmacy.api.PharmacyDtos.PharmacyRef;
import dev.jpzconception.allodocteur.pharmacy.domain.Delivery;
import dev.jpzconception.allodocteur.pharmacy.domain.DeliveryStatus;
import dev.jpzconception.allodocteur.pharmacy.domain.Pharmacy;
import dev.jpzconception.allodocteur.pharmacy.domain.PharmacyOrder;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.DeliveryRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyOrderItemRepository;
import dev.jpzconception.allodocteur.pharmacy.infrastructure.PharmacyRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OrderAssembler {

    private final PharmacyOrderItemRepository items;
    private final DeliveryRepository deliveries;
    private final PharmacyRepository pharmacies;

    public OrderAssembler(PharmacyOrderItemRepository items, DeliveryRepository deliveries, PharmacyRepository pharmacies) {
        this.items = items;
        this.deliveries = deliveries;
        this.pharmacies = pharmacies;
    }

    /** @param forPatient vrai pour le patient : seul lui voit le code de remise de la livraison. */
    public OrderDto toDto(PharmacyOrder o, boolean forPatient) {
        Pharmacy ph = pharmacies.findById(o.getPharmacyId()).orElse(null);
        List<OrderItemDto> lines = items.findByOrderIdOrderByLineNumber(o.getId()).stream()
                .map(i -> new OrderItemDto(i.getLineNumber(), i.getMedicationName(), i.getDosage(), i.getFrequency(),
                        i.getQuantity(), i.getInstructions(), i.isAvailable())).toList();
        DeliveryDto delivery = deliveries.findByOrderId(o.getId()).map(d -> toDelivery(d, forPatient)).orElse(null);
        return new OrderDto(o.getId(), o.getNumber(), o.getStatus(), o.getFulfillment(), o.getPrescriptionId(),
                ph == null ? null : new PharmacyRef(ph.getId(), ph.getName(), ph.getPhone(), ph.getAddress()),
                o.getBeneficiaryName(), o.getContactPhone(), o.getDeliveryAddress(), o.getDeliveryLatitude(),
                o.getDeliveryLongitude(), o.getPatientNote(), o.getPharmacyMessage(), o.getQuotedTotal(),
                o.getDeliveryFee(), o.getCurrency(), lines, delivery, o.getCreatedAt(), o.getAcceptedAt(),
                o.getReadyAt(), o.getCompletedAt(), o.getCancelledAt());
    }

    public List<OrderSummaryDto> toSummaries(Collection<PharmacyOrder> orders) {
        Map<java.util.UUID, String> names = pharmacies.findAllById(
                        orders.stream().map(PharmacyOrder::getPharmacyId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Pharmacy::getId, Pharmacy::getName, (a, b) -> a, java.util.HashMap::new));
        return orders.stream().map(o -> new OrderSummaryDto(o.getId(), o.getNumber(), o.getStatus(), o.getFulfillment(),
                o.getPharmacyId(), names.get(o.getPharmacyId()), o.getBeneficiaryName(), o.getCreatedAt())).toList();
    }

    private static DeliveryDto toDelivery(Delivery d, boolean forPatient) {
        // le code n'a de sens que tant que le colis est en route
        String code = forPatient && d.getStatus() == DeliveryStatus.IN_TRANSIT ? d.getHandoverCode() : null;
        return new DeliveryDto(d.getStatus(), d.getTrackingCode(), d.getCourierName(), d.getCourierPhone(),
                d.getDispatchedAt(), d.getDeliveredAt(), d.getFailureReason(), code);
    }
}
