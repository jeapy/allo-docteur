package dev.jpzconception.allodocteur.professional.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.jpzconception.allodocteur.professional.domain.DocumentType;
import dev.jpzconception.allodocteur.professional.domain.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProfessionalDtos {

    private ProfessionalDtos() {}

    // ------------------------------------------------------------ requêtes

    public record ZoneRequest(@NotNull Integer cityId, Integer districtId) {}

    public record ProfileRequest(
            @NotNull Integer professionalTypeId,
            @NotBlank @Size(max = 150) String qualification,
            @NotBlank @Size(max = 100) String licenseNumber,
            @Min(0) @Max(70) Integer yearsExperience,
            @Size(max = 4000) String bio,
            @NotNull Integer cityId,
            Integer districtId,
            @DecimalMin("0") BigDecimal consultationFee,
            @DecimalMin("0") BigDecimal homeVisitFee,
            boolean offersTeleconsultation,
            boolean offersHomeVisit,
            @NotEmpty Set<Integer> specialtyIds,
            @Valid List<ZoneRequest> coverageZones) {}

    public record RuleRequest(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            boolean teleconsultation,
            boolean homeVisit) {}

    public record RulesRequest(@NotNull @Valid List<RuleRequest> rules) {}

    public record ExceptionRequest(
            @NotNull LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            @Size(max = 255) String reason) {}

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {}

    // ------------------------------------------------------------ réponses

    public record Ref(Integer id, String name) {}

    public record TypeRef(Integer id, String code, String label) {}

    public record ZoneDto(Integer cityId, String city, Integer districtId, String district) {}

    public record RuleDto(UUID id, int dayOfWeek, LocalTime startTime, LocalTime endTime,
                          boolean teleconsultation, boolean homeVisit) {}

    public record ExceptionDto(UUID id, LocalDate date, LocalTime startTime, LocalTime endTime, String reason) {}

    public record DocumentDto(UUID id, DocumentType type, String filename, String contentType, long sizeBytes,
                              Instant uploadedAt) {}

    /** Ligne de résultat de recherche / de liste. */
    public record ProfessionalSummary(
            UUID id,
            String firstName,
            String lastName,
            String photoUrl,
            TypeRef type,
            String qualification,
            List<Ref> specialties,
            Integer yearsExperience,
            String city,
            String district,
            BigDecimal consultationFee,
            BigDecimal homeVisitFee,
            String currency,
            boolean offersTeleconsultation,
            boolean offersHomeVisit,
            BigDecimal ratingAvg,
            int ratingCount) {}

    /** Fiche complète. Les champs privés (email, statut, justificatifs) ne sont renseignés que pour le propriétaire et les admins. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProfessionalDetail(
            UUID id,
            String firstName,
            String lastName,
            String photoUrl,
            String email,
            TypeRef type,
            String qualification,
            String licenseNumber,
            Integer yearsExperience,
            String bio,
            Ref city,
            Ref district,
            BigDecimal consultationFee,
            BigDecimal homeVisitFee,
            String currency,
            boolean offersTeleconsultation,
            boolean offersHomeVisit,
            BigDecimal ratingAvg,
            int ratingCount,
            List<Ref> specialties,
            List<ZoneDto> coverageZones,
            List<RuleDto> availability,
            VerificationStatus verificationStatus,
            String rejectionReason,
            Instant reviewedAt,
            List<DocumentDto> documents,
            Instant createdAt) {}

    public record AdminProfessionalRow(
            UUID id,
            String firstName,
            String lastName,
            String email,
            TypeRef type,
            String licenseNumber,
            String city,
            VerificationStatus verificationStatus,
            Instant createdAt) {}
}
