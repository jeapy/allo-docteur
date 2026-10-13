package dev.jpzconception.allodocteur.partner.domain;

import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Structure partenaire : clinique, hôpital, laboratoire, assurance, mutuelle… */
@Entity
@Table(name = "partners")
@Getter
@Setter
@NoArgsConstructor
public class Partner extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartnerType type;

    @Column(name = "city_id")
    private Integer cityId;

    private String address;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartnerStatus status = PartnerStatus.ACTIVE;
}
