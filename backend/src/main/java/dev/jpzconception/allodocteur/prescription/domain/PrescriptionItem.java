package dev.jpzconception.allodocteur.prescription.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_items")
@Getter
@Setter
@NoArgsConstructor
public class PrescriptionItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "medication_name", nullable = false, columnDefinition = "text")
    private String medicationName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String dosage;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String frequency;

    @Column(name = "duration_days")
    private Integer durationDays;

    private Integer quantity;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String instructions;
}
