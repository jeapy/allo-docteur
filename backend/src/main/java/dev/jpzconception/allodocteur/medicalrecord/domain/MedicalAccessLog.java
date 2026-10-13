package dev.jpzconception.allodocteur.medicalrecord.domain;

import dev.jpzconception.allodocteur.user.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Ligne du journal d'accès aux dossiers (qui, quand, quoi). En ajout seul : la base interdit UPDATE et DELETE. */
@Entity
@Table(name = "medical_access_logs")
@Getter
@NoArgsConstructor
public class MedicalAccessLog {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessAction action;

    @Column(name = "target_id")
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessOutcome outcome;

    private String reason;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public MedicalAccessLog(UUID recordId, UUID actorId, Role actorRole, AccessAction action, UUID targetId,
                            AccessOutcome outcome, String reason, String ipAddress, Instant occurredAt) {
        this.recordId = recordId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.targetId = targetId;
        this.outcome = outcome;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.occurredAt = occurredAt;
    }
}
