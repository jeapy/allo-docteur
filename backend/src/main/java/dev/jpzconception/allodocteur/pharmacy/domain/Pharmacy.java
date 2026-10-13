package dev.jpzconception.allodocteur.pharmacy.domain;

import dev.jpzconception.allodocteur.shared.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pharmacies")
@Getter
@Setter
@NoArgsConstructor
public class Pharmacy extends AuditableEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Column(name = "city_id", nullable = false)
    private Integer cityId;

    @Column(name = "district_id")
    private Integer districtId;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "opening_hours")
    private String openingHours;

    @Column(name = "on_duty", nullable = false)
    private boolean onDuty;

    @Column(name = "offers_delivery", nullable = false)
    private boolean offersDelivery;

    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(nullable = false)
    private String currency = "XOF";

    @Column(nullable = false)
    private boolean active = true;
}
