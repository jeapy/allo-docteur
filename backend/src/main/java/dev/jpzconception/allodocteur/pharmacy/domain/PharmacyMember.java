package dev.jpzconception.allodocteur.pharmacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Pharmacien habilité à traiter les commandes d'une pharmacie. */
@Entity
@Table(name = "pharmacy_members")
@IdClass(PharmacyMemberId.class)
@Getter
@NoArgsConstructor
public class PharmacyMember {

    @Id
    @Column(name = "pharmacy_id")
    private UUID pharmacyId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public PharmacyMember(UUID pharmacyId, UUID userId, Instant addedAt) {
        this.pharmacyId = pharmacyId;
        this.userId = userId;
        this.addedAt = addedAt;
    }
}
