package dev.jpzconception.allodocteur.sos.domain;

import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Structure de santé vers laquelle orienter un patient en urgence. Saisie et tenue à jour par l'administration. */
@Entity
@Table(name = "health_facilities")
@Getter
@Setter
@NoArgsConstructor
public class HealthFacility extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FacilityType type;

    @Column(name = "city_id", nullable = false)
    private Integer cityId;

    @Column(name = "district_id")
    private Integer districtId;

    private String address;

    private String phone;

    @Column(nullable = false)
    private BigDecimal latitude;

    @Column(nullable = false)
    private BigDecimal longitude;

    @Column(name = "emergency_service", nullable = false)
    private boolean emergencyService = true;

    @Column(nullable = false)
    private boolean active = true;
}
