package dev.jpzconception.allodocteur.sos.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** « Bris de glace » : accès d'urgence au dossier du patient d'une alerte, motivé et limité dans le temps. */
@Entity
@Table(name = "emergency_access_grants")
@Getter
@Setter
@NoArgsConstructor
public class EmergencyAccessGrant {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
