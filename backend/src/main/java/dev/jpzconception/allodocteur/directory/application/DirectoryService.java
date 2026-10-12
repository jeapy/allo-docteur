package dev.jpzconception.allodocteur.directory.application;

import dev.jpzconception.allodocteur.directory.infrastructure.ProfessionalSpecifications;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfessionalSummary;
import dev.jpzconception.allodocteur.professional.application.ProfessionalAssembler;
import dev.jpzconception.allodocteur.professional.infrastructure.ProfessionalRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DirectoryService {

    private final ProfessionalRepository professionals;
    private final ProfessionalAssembler assembler;

    public DirectoryService(ProfessionalRepository professionals, ProfessionalAssembler assembler) {
        this.professionals = professionals;
        this.assembler = assembler;
    }

    public Page<ProfessionalSummary> search(SearchCriteria criteria, int page, int size, String sort) {
        validate(criteria);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), sortFor(sort));
        return professionals.findAll(ProfessionalSpecifications.search(criteria), pageable).map(assembler::toSummary);
    }

    /** Tri par liste blanche ; l'identifiant départage les ex aequo pour une pagination stable. */
    static Sort sortFor(String sort) {
        Sort primary = switch (sort == null ? "rating" : sort) {
            case "rating" -> Sort.by(Sort.Order.desc("ratingAvg"), Sort.Order.desc("ratingCount"));
            case "fee_asc" -> Sort.by(Sort.Order.asc("consultationFee"));
            case "fee_desc" -> Sort.by(Sort.Order.desc("consultationFee"));
            case "experience" -> Sort.by(Sort.Order.desc("yearsExperience"));
            default -> throw ApiException.badRequest("INVALID_SORT", "Tri non supporté : " + sort);
        };
        return primary.and(Sort.by("id"));
    }

    private static void validate(SearchCriteria c) {
        if (c.dayOfWeek() != null && (c.dayOfWeek() < 1 || c.dayOfWeek() > 7)) {
            throw ApiException.badRequest("INVALID_DAY", "Le jour doit être compris entre 1 (lundi) et 7 (dimanche).");
        }
        if (c.minFee() != null && c.maxFee() != null && c.minFee().compareTo(c.maxFee()) > 0) {
            throw ApiException.badRequest("INVALID_FEE_RANGE", "Le tarif minimum dépasse le tarif maximum.");
        }
        if (c.minRating() != null && (c.minRating().compareTo(BigDecimal.ZERO) < 0 || c.minRating().compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw ApiException.badRequest("INVALID_RATING", "La note doit être comprise entre 0 et 5.");
        }
    }
}
