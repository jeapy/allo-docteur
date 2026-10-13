package dev.jpzconception.allodocteur.prescription.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
public class Prescription extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "professional_id", nullable = false, updatable = false)
    private UUID professionalId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "dependent_id", updatable = false)
    private UUID dependentId;

    @Column(name = "beneficiary_name", nullable = false, updatable = false)
    private String beneficiaryName;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrescriptionStatus status = PrescriptionStatus.ISSUED;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "integrity_hash", nullable = false, updatable = false)
    private String integrityHash;

    public boolean isExpired(Instant now) {
        return !now.isBefore(validUntil);
    }

    public boolean isActive(Instant now) {
        return status == PrescriptionStatus.ISSUED && !isExpired(now);
    }

    public void cancel(Instant now, String reason) {
        this.status = PrescriptionStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelReason = reason;
    }
}
