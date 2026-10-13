package dev.jpzconception.allodocteur.payment.infrastructure;

import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.Payment;
import dev.jpzconception.allodocteur.payment.domain.PaymentMethod;
import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
import dev.jpzconception.allodocteur.payment.domain.PayeeType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentSpecifications {

    private PaymentSpecifications() {}

    /** Paiements où l'utilisateur est le payeur ou le bénéficiaire (professionnel). */
    public static Specification<Payment> involving(UUID userId) {
        return (root, q, cb) -> cb.or(
                cb.equal(root.get("payerId"), userId),
                cb.and(cb.equal(root.get("payeeType"), PayeeType.USER), cb.equal(root.get("payeeId"), userId)));
    }

    public static Specification<Payment> filter(UUID payerId, PaymentStatus status, PaymentMethod method,
                                                PayableType type, Instant from, Instant to) {
        return (root, q, cb) -> {
            Predicate p = cb.conjunction();
            if (payerId != null) p = cb.and(p, cb.equal(root.get("payerId"), payerId));
            if (status != null) p = cb.and(p, cb.equal(root.get("status"), status));
            if (method != null) p = cb.and(p, cb.equal(root.get("method"), method));
            if (type != null) p = cb.and(p, cb.equal(root.get("payableType"), type));
            if (from != null) p = cb.and(p, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) p = cb.and(p, cb.lessThan(root.get("createdAt"), to));
            return p;
        };
    }
}
