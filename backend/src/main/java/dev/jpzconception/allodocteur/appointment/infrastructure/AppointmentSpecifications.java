package dev.jpzconception.allodocteur.appointment.infrastructure;

import dev.jpzconception.allodocteur.appointment.domain.Appointment;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class AppointmentSpecifications {

    private AppointmentSpecifications() {}

    /** Tous les paramètres sont facultatifs. {@code from} inclus, {@code to} exclu (sur l'heure de début). */
    public static Specification<Appointment> filter(UUID patientId, UUID professionalId,
                                                    Collection<AppointmentStatus> statuses, AppointmentType type,
                                                    Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (patientId != null) predicates.add(cb.equal(root.get("patientId"), patientId));
            if (professionalId != null) predicates.add(cb.equal(root.get("professionalId"), professionalId));
            if (statuses != null && !statuses.isEmpty()) predicates.add(root.get("status").in(statuses));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("startAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.<Instant>get("startAt"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
