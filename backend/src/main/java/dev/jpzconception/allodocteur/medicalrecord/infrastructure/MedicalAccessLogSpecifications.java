package dev.jpzconception.allodocteur.medicalrecord.infrastructure;

import dev.jpzconception.allodocteur.medicalrecord.domain.AccessOutcome;
import dev.jpzconception.allodocteur.medicalrecord.domain.MedicalAccessLog;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class MedicalAccessLogSpecifications {

    private MedicalAccessLogSpecifications() {}

    public static Specification<MedicalAccessLog> filter(UUID recordId, UUID actorId, AccessOutcome outcome,
                                                         Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (recordId != null) predicates.add(cb.equal(root.get("recordId"), recordId));
            if (actorId != null) predicates.add(cb.equal(root.get("actorId"), actorId));
            if (outcome != null) predicates.add(cb.equal(root.get("outcome"), outcome));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.<Instant>get("occurredAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
