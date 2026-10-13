package dev.jpzconception.allodocteur.pharmacy.domain;

import dev.jpzconception.allodocteur.shared.crypto.EncryptedStringConverter;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Commande d'ordonnance auprès d'une pharmacie : retrait en officine ou livraison. */
@Entity
@Table(name = "pharmacy_orders")
@Getter
@Setter
@NoArgsConstructor
public class PharmacyOrder extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, updatable = false)
    private String number;

    @Column(name = "prescription_id", nullable = false, updatable = false)
    private UUID prescriptionId;

    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "dependent_id", updatable = false)
    private UUID dependentId;

    @Column(name = "beneficiary_name", nullable = false, updatable = false)
    private String beneficiaryName;

    @Column(name = "pharmacy_id", nullable = false, updatable = false)
    private UUID pharmacyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Fulfillment fulfillment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.SENT;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "delivery_latitude")
    private BigDecimal deliveryLatitude;

    @Column(name = "delivery_longitude")
    private BigDecimal deliveryLongitude;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "patient_note", columnDefinition = "text")
    private String patientNote;

    @Column(name = "pharmacy_message")
    private String pharmacyMessage;

    @Column(name = "quoted_total")
    private BigDecimal quotedTotal;

    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "XOF";

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // ---- transitions ----

    public void accept(Instant now, BigDecimal total, String message) {
        require(status == OrderStatus.SENT, "accepter");
        status = OrderStatus.ACCEPTED;
        acceptedAt = now;
        quotedTotal = total;
        pharmacyMessage = message;
    }

    public void reject(Instant now, String message) {
        require(status == OrderStatus.SENT, "refuser");
        status = OrderStatus.REJECTED;
        completedAt = now;
        pharmacyMessage = message;
    }

    public void markReady(Instant now, String message) {
        require(status == OrderStatus.ACCEPTED, "déclarer prête");
        status = OrderStatus.READY;
        readyAt = now;
        if (message != null) pharmacyMessage = message;
    }

    public void handOver(Instant now) {
        require(status == OrderStatus.READY && fulfillment == Fulfillment.PICKUP, "remettre en officine");
        status = OrderStatus.COMPLETED;
        completedAt = now;
    }

    public void dispatch() {
        require(status == OrderStatus.READY && fulfillment == Fulfillment.DELIVERY, "expédier");
        status = OrderStatus.OUT_FOR_DELIVERY;
    }

    public void delivered(Instant now) {
        require(status == OrderStatus.OUT_FOR_DELIVERY, "confirmer la livraison de");
        status = OrderStatus.DELIVERED;
        completedAt = now;
    }

    public void deliveryFailed(Instant now) {
        require(status == OrderStatus.OUT_FOR_DELIVERY, "déclarer l'échec de livraison de");
        status = OrderStatus.DELIVERY_FAILED;
        completedAt = now;
    }

    public void cancel(Instant now) {
        require(status == OrderStatus.SENT || status == OrderStatus.ACCEPTED, "annuler");
        status = OrderStatus.CANCELLED;
        cancelledAt = now;
    }

    private void require(boolean ok, String action) {
        if (!ok) {
            throw ApiException.conflict("INVALID_STATE", "Impossible de " + action + " une commande au statut " + status + ".");
        }
    }
}
