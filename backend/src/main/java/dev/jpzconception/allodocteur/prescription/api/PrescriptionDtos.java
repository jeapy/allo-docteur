package dev.jpzconception.allodocteur.prescription.api;

import dev.jpzconception.allodocteur.prescription.domain.PrescriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PrescriptionDtos {

    private PrescriptionDtos() {}

    public record ItemRequest(
            @NotBlank @Size(max = 200) String medicationName,
            @Size(max = 200) String dosage,
            @Size(max = 200) String frequency,
            @Min(1) @Max(365) Integer durationDays,
            @Min(1) @Max(1000) Integer quantity,
            @Size(max = 1000) String instructions) {}

    public record IssueRequest(
            @NotEmpty @Size(max = 20) List<@Valid ItemRequest> items,
            @Size(max = 2000) String notes) {}

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {}

    public record ItemDto(int lineNumber, String medicationName, String dosage, String frequency,
                          Integer durationDays, Integer quantity, String instructions) {}

    /** Liste « mes ordonnances » : aucune donnée de santé (ni médicaments, ni notes). */
    public record SummaryDto(UUID id, String number, UUID appointmentId, UUID recordId, String professionalName,
                             String beneficiaryName, Instant issuedAt, Instant validUntil, PrescriptionStatus status,
                             boolean expired) {}

    public record DetailDto(UUID id, String number, UUID appointmentId, UUID recordId, UUID professionalId,
                            String professionalName, String professionalQualification, String professionalLicenseNumber,
                            String beneficiaryName, Instant issuedAt, Instant validUntil, PrescriptionStatus status,
                            boolean expired, Instant cancelledAt, String cancelReason, String notes,
                            List<ItemDto> items, boolean integrityValid) {}
}
