package dev.jpzconception.allodocteur.pharmacy.domain;

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
@Table(name = "pharmacy_order_items")
@Getter
@Setter
@NoArgsConstructor
public class PharmacyOrderItem {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

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

    private Integer quantity;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "text")
    private String instructions;

    @Column(nullable = false)
    private boolean available = true;
}
