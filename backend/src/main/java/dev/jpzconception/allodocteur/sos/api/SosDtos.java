package dev.jpzconception.allodocteur.sos.api;

import dev.jpzconception.allodocteur.sos.domain.FacilityType;
import dev.jpzconception.allodocteur.sos.domain.SosCategory;
import dev.jpzconception.allodocteur.sos.domain.SosStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SosDtos {

    private SosDtos() {}

    static final String PHONE = "^\\+?[0-9 ()-]{8,20}$";

    public record CreateRequest(
            @NotNull SosCategory category,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 255) String address,
            @Size(max = 2000) String description,
            @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String contactPhone,
            /** Ville de l'alerte ; à défaut, celle du profil patient. */
            Integer cityId,
            /** Personne à charge concernée ; absent = le titulaire du compte. */
            UUID dependentId) {}

    public record ResolveRequest(@Size(max = 2000) String note) {}

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}

    public record OptionalReasonRequest(@Size(max = 500) String reason) {}

    public record ProfessionalRef(UUID id, String name, String phone) {}

    public record FacilityDto(UUID id, String name, FacilityType type, Integer cityId, String city, Integer districtId,
                              String district, String address, String phone, BigDecimal latitude, BigDecimal longitude,
                              boolean emergencyService, boolean active, Double distanceKm) {}

    public record FacilityRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull FacilityType type,
            @NotNull Integer cityId,
            Integer districtId,
            @Size(max = 255) String address,
            @Pattern(regexp = PHONE, message = "Numéro de téléphone invalide") String phone,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            boolean emergencyService) {}

    /** Vue complète : patient propriétaire ou professionnel assigné. */
    public record AlertDto(UUID id, SosStatus status, SosCategory category, String beneficiaryName,
                           LocalDate beneficiaryBirthDate, BigDecimal latitude, BigDecimal longitude, String address,
                           Integer cityId, String contactPhone, String description, ProfessionalRef professional,
                           UUID appointmentId, Instant createdAt, Instant acceptedAt, Instant resolvedAt,
                           String resolutionNote, String cancelReason, Instant emergencyAccessUntil,
                           List<FacilityDto> nearestFacilities) {}

    /** Ce que voit un professionnel avant d'accepter : ni position précise, ni identité, ni description. */
    public record OpenAlertDto(UUID id, SosCategory category, Integer cityId, String city, Instant createdAt,
                               long waitingMinutes) {}

    /** Suivi administratif : pas de description médicale ni de coordonnées exactes. */
    public record AdminAlertDto(UUID id, SosStatus status, SosCategory category, UUID accountId, String patientName,
                                Integer cityId, String city, ProfessionalRef professional, UUID appointmentId,
                                Instant createdAt, Instant acceptedAt, Instant resolvedAt, String cancelReason,
                                long waitingMinutes) {}

    public record SummaryDto(long open, long accepted) {}

    public record EmergencyAccessDto(UUID alertId, Instant grantedAt, Instant expiresAt) {}
}
