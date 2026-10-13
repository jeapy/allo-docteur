package dev.jpzconception.allodocteur.pharmacy.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
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

@Entity
@Table(name = "deliveries")
@Getter
@Setter
@NoArgsConstructor
public class Delivery extends AuditableEntity {

    public static final int MAX_CODE_ATTEMPTS = 5;

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.IN_TRANSIT;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "tracking_code", nullable = false, updatable = false)
    private String trackingCode;

    @Column(name = "courier_name")
    private String courierName;

    @Column(name = "courier_phone")
    private String courierPhone;

    /** Code à 6 chiffres remis au patient ; le livreur doit le recueillir pour clore la livraison. Chiffré au repos. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "handover_code", nullable = false, columnDefinition = "text")
    private String handoverCode;

    @Column(name = "code_attempts", nullable = false)
    private int codeAttempts;

    @Column(name = "dispatched_at", nullable = false, updatable = false)
    private Instant dispatchedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failure_reason")
    private String failureReason;
}
