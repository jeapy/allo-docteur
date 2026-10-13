package dev.jpzconception.allodocteur.pharmacy.infrastructure;

import dev.jpzconception.allodocteur.pharmacy.domain.Pharmacy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class PharmacySpecifications {

    private PharmacySpecifications() {}

    /** Recherche multicritère ; {@code activeOnly} masque les pharmacies désactivées (vue publique). */
    public static Specification<Pharmacy> filter(Integer cityId, Integer districtId, Boolean onDuty, Boolean delivery,
                                                 String name, boolean activeOnly) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            if (activeOnly) p.add(cb.isTrue(root.get("active")));
            if (cityId != null) p.add(cb.equal(root.get("cityId"), cityId));
            if (districtId != null) p.add(cb.equal(root.get("districtId"), districtId));
            if (onDuty != null) p.add(cb.equal(root.get("onDuty"), onDuty));
            if (delivery != null) p.add(cb.equal(root.get("offersDelivery"), delivery));
            if (name != null && !name.isBlank()) {
                String like = "%" + name.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.like(cb.lower(root.get("name")), like, '\\'));
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
