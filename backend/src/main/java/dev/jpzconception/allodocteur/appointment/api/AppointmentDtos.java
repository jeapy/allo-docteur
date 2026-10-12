package dev.jpzconception.allodocteur.appointment.api;

import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import dev.jpzconception.allodocteur.appointment.domain.ReasonCategory;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class AppointmentDtos {

    private AppointmentDtos() {}

    public record BookRequest(
            @NotNull UUID professionalId,
            @NotNull AppointmentType type,
            @NotNull ReasonCategory reason,
            @Size(max = 2000) String reasonDetails,
            @NotNull Instant startAt,
            /** Personne à charge concernée ; absent = le titulaire du compte. */
            UUID dependentId,
            @Size(max = 255) String address,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}

    public record CancelRequest(@Size(max = 1000) String reason) {}

    public record PersonRef(UUID id, String name) {}

    public record ProfessionalRef(UUID id, String name, String qualification, String typeLabel) {}

    public record AppointmentDto(
            UUID id,
            AppointmentStatus status,
            AppointmentType type,
            ReasonCategory reason,
            /** Absent pour les administrateurs : donnée de santé. */
            String reasonDetails,
            Instant startAt,
            Instant endAt,
            BigDecimal fee,
            String currency,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            PersonRef patient,
            UUID dependentId,
            String beneficiaryName,
            LocalDate beneficiaryBirthDate,
            ProfessionalRef professional,
            Instant confirmedAt,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt,
            UUID cancelledBy,
            String cancelReason,
            Instant createdAt) {}

    public record SlotDto(Instant startAt, Instant endAt) {}
}
