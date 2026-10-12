package dev.jpzconception.allodocteur.appointment.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    /** Compte qui a réservé (le patient lui-même, ou le tuteur d'une personne à charge). */
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "dependent_id")
    private UUID dependentId;

    /** Instantané du nom de la personne concernée, figé à la réservation. */
    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_birth_date")
    private LocalDate beneficiaryBirthDate;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReasonCategory reason;

    /** Description libre des symptômes : donnée de santé, chiffrée au repos. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "reason_details", columnDefinition = "text")
    private String reasonDetails;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.REQUESTED;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(nullable = false)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "XOF";

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    // ---- transitions ----

    public void confirm(Instant now) {
        require(status == AppointmentStatus.REQUESTED, "confirmer");
        status = AppointmentStatus.CONFIRMED;
        confirmedAt = now;
    }

    public void cancel(UUID by, String reason, Instant now) {
        require(status == AppointmentStatus.REQUESTED || status == AppointmentStatus.CONFIRMED, "annuler");
        status = AppointmentStatus.CANCELLED;
        cancelledBy = by;
        cancelReason = reason;
        cancelledAt = now;
    }

    public void start(Instant now) {
        require(status == AppointmentStatus.CONFIRMED, "démarrer");
        status = AppointmentStatus.IN_PROGRESS;
        startedAt = now;
    }

    public void complete(Instant now) {
        require(status == AppointmentStatus.IN_PROGRESS, "terminer");
        status = AppointmentStatus.COMPLETED;
        completedAt = now;
    }

    public void markNoShow() {
        require(status == AppointmentStatus.CONFIRMED, "déclarer absent");
        status = AppointmentStatus.NO_SHOW;
    }

    public boolean isParticipant(UUID userId) {
        return patientId.equals(userId) || professionalId.equals(userId);
    }

    private void require(boolean ok, String action) {
        if (!ok) {
            throw ApiException.conflict("INVALID_STATE",
                    "Impossible de " + action + " un rendez-vous au statut " + status + ".");
        }
    }
}
