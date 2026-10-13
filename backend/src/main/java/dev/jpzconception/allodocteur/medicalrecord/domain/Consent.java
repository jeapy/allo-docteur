package dev.jpzconception.allodocteur.medicalrecord.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Consentement horodaté et versionné d'un compte. Jamais modifié, sauf pour être révoqué. */
@Entity
@Table(name = "consents")
@Getter
@NoArgsConstructor
public class Consent {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String version;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    public Consent(UUID userId, String version, Instant grantedAt, String ipAddress) {
        this.userId = userId;
        this.version = version;
        this.grantedAt = grantedAt;
        this.ipAddress = ipAddress;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public boolean isActive() {
        return revokedAt == null;
    }
}
