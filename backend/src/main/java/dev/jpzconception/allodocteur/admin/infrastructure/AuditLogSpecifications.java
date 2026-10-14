package dev.jpzconception.allodocteur.admin.infrastructure;

import dev.jpzconception.allodocteur.admin.domain.AuditLog;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> filter(String action, String targetType, String targetId, UUID actorId,
                                                 Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) p.add(cb.equal(root.get("action"), action.trim()));
            if (targetType != null && !targetType.isBlank()) p.add(cb.equal(root.get("targetType"), targetType.trim()));
            if (targetId != null && !targetId.isBlank()) p.add(cb.equal(root.get("targetId"), targetId.trim()));
            if (actorId != null) p.add(cb.equal(root.get("actorId"), actorId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
