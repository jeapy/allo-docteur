package dev.jpzconception.allodocteur.pharmacy.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clé composite de {@link PharmacyMember} (classe ordinaire : la plus sûre avec Hibernate). */
public class PharmacyMemberId implements Serializable {

    private UUID pharmacyId;
    private UUID userId;

    public PharmacyMemberId() {}

    public PharmacyMemberId(UUID pharmacyId, UUID userId) {
        this.pharmacyId = pharmacyId;
        this.userId = userId;
    }

    public UUID getPharmacyId() {
        return pharmacyId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PharmacyMemberId other
                && Objects.equals(pharmacyId, other.pharmacyId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pharmacyId, userId);
    }
}
