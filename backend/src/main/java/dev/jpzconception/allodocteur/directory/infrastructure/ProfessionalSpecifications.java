package dev.jpzconception.allodocteur.directory.infrastructure;

import dev.jpzconception.allodocteur.directory.application.SearchCriteria;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityException;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityRule;
import dev.jpzconception.allodocteur.professional.domain.CoverageZone;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.professional.domain.VerificationStatus;
import dev.jpzconception.allodocteur.reference.domain.Specialty;
import dev.jpzconception.allodocteur.user.domain.UserStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class ProfessionalSpecifications {

    private ProfessionalSpecifications() {}

    /** Seuls les professionnels approuvés et dont le compte est actif apparaissent dans le répertoire. */
    public static Specification<Professional> search(SearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            Join<Professional, ?> user = root.join("user");
            ps.add(cb.equal(root.get("verificationStatus"), VerificationStatus.APPROVED));
            ps.add(cb.equal(user.get("status"), UserStatus.ACTIVE));

            if (c.q() != null && !c.q().isBlank()) {
                String like = "%" + c.q().trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(user.<String>get("firstName"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(user.<String>get("lastName"), "")), like, '\\'),
                        cb.like(cb.lower(root.get("qualification")), like, '\\')));
            }
            if (c.typeId() != null) {
                ps.add(cb.equal(root.get("type").get("id"), c.typeId()));
            }
            if (c.specialtyIds() != null && !c.specialtyIds().isEmpty()) {
                Join<Professional, Specialty> sp = root.join("specialties");
                ps.add(sp.get("id").in(c.specialtyIds()));
                query.distinct(true);
            }
            if (c.cityId() != null) {
                ps.add(cb.or(cb.equal(root.get("city").get("id"), c.cityId()), covers(root, query, cb, c.cityId(), null)));
            }
            if (c.districtId() != null) {
                ps.add(cb.or(cb.equal(root.get("district").get("id"), c.districtId()),
                        covers(root, query, cb, null, c.districtId())));
            }
            if (Boolean.TRUE.equals(c.teleconsultation())) {
                ps.add(cb.isTrue(root.get("offersTeleconsultation")));
            }
            if (Boolean.TRUE.equals(c.homeVisit())) {
                ps.add(cb.isTrue(root.get("offersHomeVisit")));
            }
            // Le tarif filtré est celui du service demandé (visite à domicile seule => tarif de visite).
            String feeField = Boolean.TRUE.equals(c.homeVisit()) && !Boolean.TRUE.equals(c.teleconsultation())
                    ? "homeVisitFee" : "consultationFee";
            if (c.minFee() != null) ps.add(cb.greaterThanOrEqualTo(root.<java.math.BigDecimal>get(feeField), c.minFee()));
            if (c.maxFee() != null) ps.add(cb.lessThanOrEqualTo(root.<java.math.BigDecimal>get(feeField), c.maxFee()));
            if (c.minRating() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<java.math.BigDecimal>get("ratingAvg"), c.minRating()));
            }
            Integer day = c.effectiveDay();
            if (day != null) {
                ps.add(cb.exists(availableOnDay(root, query, cb, day, c)));
                if (c.date() != null) {
                    ps.add(cb.not(cb.exists(fullDayOff(root, query, cb, c.date()))));
                }
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static jakarta.persistence.criteria.Expression<Boolean> covers(
            Root<Professional> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb, Integer cityId, Integer districtId) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<CoverageZone> z = sq.from(CoverageZone.class);
        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.equal(z.get("professional"), root));
        if (cityId != null) ps.add(cb.equal(z.get("city").get("id"), cityId));
        if (districtId != null) ps.add(cb.equal(z.get("district").get("id"), districtId));
        sq.select(cb.literal(1)).where(ps.toArray(new Predicate[0]));
        return cb.exists(sq);
    }

    private static Subquery<Integer> availableOnDay(
            Root<Professional> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb, int day, SearchCriteria c) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<AvailabilityRule> r = sq.from(AvailabilityRule.class);
        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.equal(r.get("professionalId"), root.get("id")));
        ps.add(cb.equal(r.get("dayOfWeek"), day));
        if (Boolean.TRUE.equals(c.teleconsultation())) ps.add(cb.isTrue(r.get("teleconsultation")));
        if (Boolean.TRUE.equals(c.homeVisit())) ps.add(cb.isTrue(r.get("homeVisit")));
        sq.select(cb.literal(1)).where(ps.toArray(new Predicate[0]));
        return sq;
    }

    private static Subquery<Integer> fullDayOff(
            Root<Professional> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb, java.time.LocalDate date) {
        Subquery<Integer> sq = query.subquery(Integer.class);
        Root<AvailabilityException> e = sq.from(AvailabilityException.class);
        sq.select(cb.literal(1)).where(
                cb.equal(e.get("professionalId"), root.get("id")),
                cb.equal(e.get("date"), date),
                cb.isNull(e.get("startTime")));
        return sq;
    }
}
