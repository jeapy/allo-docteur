package dev.jpzconception.allodocteur.partner.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Police d'assurance ou de mutuelle enregistrée par un patient (pour lui-même ou une personne à charge). */
@Entity
@Table(name = "insurance_policies")
@Getter
@Setter
@NoArgsConstructor
public class InsurancePolicy extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "dependent_id")
    private UUID dependentId;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "policy_number", nullable = false, columnDefinition = "text")
    private String policyNumber;

    @Column(name = "coverage_rate", nullable = false)
    private BigDecimal coverageRate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;
}
