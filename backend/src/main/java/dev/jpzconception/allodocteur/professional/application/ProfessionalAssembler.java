package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.*;
import dev.jpzconception.allodocteur.professional.domain.AvailabilityRule;
import dev.jpzconception.allodocteur.professional.domain.CoverageZone;
import dev.jpzconception.allodocteur.professional.domain.Professional;
import dev.jpzconception.allodocteur.professional.domain.ProfessionalDocument;
import dev.jpzconception.allodocteur.reference.domain.ProfessionalType;
import dev.jpzconception.allodocteur.reference.domain.Specialty;
import dev.jpzconception.allodocteur.user.domain.User;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Conversion entités -> DTO. À appeler dans une transaction (associations lazy). */
@Component
public class ProfessionalAssembler {

    public ProfessionalSummary toSummary(Professional p) {
        User u = p.getUser();
        return new ProfessionalSummary(p.getId(), u.getFirstName(), u.getLastName(), u.getPhotoUrl(),
                typeRef(p.getType()), p.getQualification(), specialties(p), p.getYearsExperience(),
                p.getCity().getName(), p.getDistrict() == null ? null : p.getDistrict().getName(),
                p.getConsultationFee(), p.getHomeVisitFee(), p.getCurrency(),
                p.isOffersTeleconsultation(), p.isOffersHomeVisit(), p.getRatingAvg(), p.getRatingCount());
    }

    /**
     * @param privileged vrai pour le propriétaire et les admins : expose email, statut et justificatifs
     */
    public ProfessionalDetail toDetail(Professional p, List<AvailabilityRule> rules,
                                       List<ProfessionalDocument> documents, boolean privileged) {
        User u = p.getUser();
        return new ProfessionalDetail(
                p.getId(), u.getFirstName(), u.getLastName(), u.getPhotoUrl(),
                privileged ? u.getEmail() : null,
                typeRef(p.getType()), p.getQualification(), p.getLicenseNumber(), p.getYearsExperience(), p.getBio(),
                new Ref(p.getCity().getId(), p.getCity().getName()),
                p.getDistrict() == null ? null : new Ref(p.getDistrict().getId(), p.getDistrict().getName()),
                p.getConsultationFee(), p.getHomeVisitFee(), p.getCurrency(),
                p.isOffersTeleconsultation(), p.isOffersHomeVisit(), p.getRatingAvg(), p.getRatingCount(),
                specialties(p), zones(p), rules.stream().map(this::rule).toList(),
                privileged ? p.getVerificationStatus() : null,
                privileged ? p.getRejectionReason() : null,
                privileged ? p.getReviewedAt() : null,
                privileged ? documents.stream().map(this::document).toList() : null,
                privileged ? p.getCreatedAt() : null);
    }

    public AdminProfessionalRow toRow(Professional p) {
        User u = p.getUser();
        return new AdminProfessionalRow(p.getId(), u.getFirstName(), u.getLastName(), u.getEmail(),
                typeRef(p.getType()), p.getLicenseNumber(), p.getCity().getName(),
                p.getVerificationStatus(), p.getCreatedAt());
    }

    public RuleDto rule(AvailabilityRule r) {
        return new RuleDto(r.getId(), r.getDayOfWeek(), r.getStartTime(), r.getEndTime(),
                r.isTeleconsultation(), r.isHomeVisit());
    }

    public DocumentDto document(ProfessionalDocument d) {
        return new DocumentDto(d.getId(), d.getType(), d.getOriginalFilename(), d.getContentType(),
                d.getSizeBytes(), d.getUploadedAt());
    }

    private static TypeRef typeRef(ProfessionalType t) {
        return new TypeRef(t.getId(), t.getCode(), t.getLabelFr());
    }

    private static List<Ref> specialties(Professional p) {
        return p.getSpecialties().stream()
                .sorted(Comparator.comparing(Specialty::getNameFr))
                .map(s -> new Ref(s.getId(), s.getNameFr()))
                .toList();
    }

    private static List<ZoneDto> zones(Professional p) {
        return p.getCoverageZones().stream()
                .map((CoverageZone z) -> new ZoneDto(z.getCity().getId(), z.getCity().getName(),
                        z.getDistrict() == null ? null : z.getDistrict().getId(),
                        z.getDistrict() == null ? null : z.getDistrict().getName()))
                .sorted(Comparator.comparing(ZoneDto::city).thenComparing(z -> z.district() == null ? "" : z.district()))
                .toList();
    }
}
