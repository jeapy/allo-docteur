package dev.jpzconception.allodocteur.complaint.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import dev.jpzconception.allodocteur.user.domain.Role;
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

/** Réclamation déposée par un patient ou un professionnel ; description et résolution chiffrées au repos. */
@Entity
@Table(name = "complaints")
@Getter
@Setter
@NoArgsConstructor
public class Complaint extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, updatable = false)
    private Role authorRole;

    @Column(name = "appointment_id", updatable = false)
    private UUID appointmentId;

    @Column(nullable = false)
    private String subject;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
