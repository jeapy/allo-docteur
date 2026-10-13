package dev.jpzconception.allodocteur.medicalrecord.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dossier médical d'une personne : le titulaire du compte ({@code dependentId} nul) ou une personne à charge. */
@Entity
@Table(name = "medical_records")
@Getter
@Setter
@NoArgsConstructor
public class MedicalRecord extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "dependent_id")
    private UUID dependentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group")
    private BloodGroup bloodGroup;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String allergies;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "chronic_conditions", columnDefinition = "text")
    private String chronicConditions;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "current_medications", columnDefinition = "text")
    private String currentMedications;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "family_history", columnDefinition = "text")
    private String familyHistory;
}
