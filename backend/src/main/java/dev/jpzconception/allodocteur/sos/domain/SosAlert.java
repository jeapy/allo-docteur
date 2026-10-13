package dev.jpzconception.allodocteur.sos.domain;

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
@Table(name = "sos_alerts")
@Getter
@Setter
@NoArgsConstructor
public class SosAlert extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "dependent_id")
    private UUID dependentId;

    @Column(name = "beneficiary_name", nullable = false)
    private String beneficiaryName;

    @Column(name = "beneficiary_birth_date")
    private LocalDate beneficiaryBirthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SosCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SosStatus status = SosStatus.OPEN;

    @Column(nullable = false)
    private BigDecimal latitude;

    @Column(nullable = false)
    private BigDecimal longitude;

    @Column(nullable = false)
    private String address;

    @Column(name = "city_id")
    private Integer cityId;

    @Column(name = "contact_phone")
    private String contactPhone;

    /** Description de la situation : donnée de santé, chiffrée au repos. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "assigned_professional_id")
    private UUID assignedProfessionalId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    public boolean isActive() {
        return SosStatus.ACTIVE.contains(status);
    }

    public void resolve(Instant now, String note) {
        if (status != SosStatus.ACCEPTED) {
            throw ApiException.conflict("SOS_INVALID_STATE", "Seule une alerte prise en charge peut être clôturée.");
        }
        status = SosStatus.RESOLVED;
        resolvedAt = now;
        resolutionNote = note;
    }

    public void cancel(UUID by, String reason, Instant now) {
        if (!isActive()) {
            throw ApiException.conflict("SOS_INVALID_STATE", "Cette alerte est déjà terminée.");
        }
        status = SosStatus.CANCELLED;
        cancelledBy = by;
        cancelReason = reason;
        cancelledAt = now;
    }
}
