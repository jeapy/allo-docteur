package dev.jpzconception.allodocteur.pharmacy.api;

import dev.jpzconception.allodocteur.pharmacy.domain.DeliveryStatus;
import dev.jpzconception.allodocteur.pharmacy.domain.Fulfillment;
import dev.jpzconception.allodocteur.pharmacy.domain.OrderStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PharmacyDtos {

    private PharmacyDtos() {}

    static final String PHONE = "^\\+?[0-9 ()-]{8,20}$";

    // ------------------------------------------------------------ pharmacies

    public record PharmacyRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull Integer cityId,
            Integer districtId,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 255) String openingHours,
            boolean onDuty,
            boolean offersDelivery,
            @DecimalMin("0") BigDecimal deliveryFee) {}

    public record PharmacyDto(UUID id, String name, Integer cityId, String city, Integer districtId, String district,
                              String address, String phone, BigDecimal latitude, BigDecimal longitude,
                              String openingHours, boolean onDuty, boolean offersDelivery, BigDecimal deliveryFee,
                              String currency, boolean active, Double distanceKm) {}

    public record MemberDto(UUID userId, String name, String email, Instant addedAt) {}

    // ------------------------------------------------------------ commandes

    public record OrderRequest(
            @NotNull UUID prescriptionId,
            @NotNull UUID pharmacyId,
            @NotNull Fulfillment fulfillment,
            @NotBlank @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String contactPhone,
            @Size(max = 255) String deliveryAddress,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal deliveryLatitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal deliveryLongitude,
            @Size(max = 1000) String note) {}

    public record AcceptRequest(
            @DecimalMin("0") BigDecimal quotedTotal,
            @Size(max = 500) String message,
            /** Numéros des lignes que la pharmacie ne peut pas fournir. */
            List<Integer> unavailableLines) {}

    public record ReadyRequest(@Size(max = 500) String message) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record DeliverRequest(@NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code à 6 chiffres") String code) {}

    public record PharmacyRef(UUID id, String name, String phone, String address) {}

    public record OrderItemDto(int lineNumber, String medicationName, String dosage, String frequency, Integer quantity,
                               String instructions, boolean available) {}

    public record DeliveryDto(DeliveryStatus status, String trackingCode, String courierName, String courierPhone,
                              Instant dispatchedAt, Instant deliveredAt, String failureReason,
                              /** Visible du seul patient. */ String handoverCode) {}

    public record OrderDto(UUID id, String number, OrderStatus status, Fulfillment fulfillment, UUID prescriptionId,
                           PharmacyRef pharmacy, String beneficiaryName, String contactPhone, String deliveryAddress,
                           BigDecimal deliveryLatitude, BigDecimal deliveryLongitude, String patientNote,
                           String pharmacyMessage, BigDecimal quotedTotal, BigDecimal deliveryFee, String currency,
                           List<OrderItemDto> items, DeliveryDto delivery, Instant createdAt, Instant acceptedAt,
                           Instant readyAt, Instant completedAt, Instant cancelledAt) {}

    /** Liste : aucun médicament. */
    public record OrderSummaryDto(UUID id, String number, OrderStatus status, Fulfillment fulfillment, UUID pharmacyId,
                                  String pharmacyName, String beneficiaryName, Instant createdAt) {}

}
