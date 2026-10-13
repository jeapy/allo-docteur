package dev.jpzconception.allodocteur.partner.infrastructure;

import dev.jpzconception.allodocteur.partner.domain.Partner;
import dev.jpzconception.allodocteur.partner.domain.PartnerStatus;
import dev.jpzconception.allodocteur.partner.domain.PartnerType;
import org.springframework.data.jpa.domain.Specification;

public final class PartnerSpecifications {
    private PartnerSpecifications() {}

    public static Specification<Partner> of(PartnerStatus status, PartnerType type, Integer cityId) {
        Specification<Partner> spec = (root, q, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), type));
        }
        if (cityId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("cityId"), cityId));
        }
        return spec;
    }
}
