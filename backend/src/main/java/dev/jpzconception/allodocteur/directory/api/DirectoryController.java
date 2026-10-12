package dev.jpzconception.allodocteur.directory.api;

import dev.jpzconception.allodocteur.directory.application.DirectoryService;
import dev.jpzconception.allodocteur.directory.application.SearchCriteria;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfessionalDetail;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfessionalSummary;
import dev.jpzconception.allodocteur.professional.application.ProfessionalService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Répertoire des professionnels et recherche avancée (spécialité, ville, quartier, disponibilité, tarif, mode). */
@RestController
@RequestMapping("/api/v1/professionals")
@Tag(name = "Répertoire")
public class DirectoryController {

    private final DirectoryService directory;
    private final ProfessionalService professionals;

    public DirectoryController(DirectoryService directory, ProfessionalService professionals) {
        this.directory = directory;
        this.professionals = professionals;
    }

    @GetMapping
    public PageResponse<ProfessionalSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer typeId,
            @RequestParam(required = false) List<Integer> specialtyId,
            @RequestParam(required = false) Integer cityId,
            @RequestParam(required = false) Integer districtId,
            @RequestParam(required = false) BigDecimal minFee,
            @RequestParam(required = false) BigDecimal maxFee,
            @RequestParam(required = false) Boolean teleconsultation,
            @RequestParam(required = false) Boolean homeVisit,
            @Parameter(description = "Jour ISO 1 (lundi) à 7 (dimanche)") @RequestParam(required = false) Integer day,
            @Parameter(description = "Date précise (ignore « day »), exclut les journées d'absence")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BigDecimal minRating,
            @Parameter(description = "rating (défaut) | fee_asc | fee_desc | experience")
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        SearchCriteria criteria = new SearchCriteria(q, typeId, specialtyId, cityId, districtId, minFee, maxFee,
                teleconsultation, homeVisit, day, date, minRating);
        return PageResponse.of(directory.search(criteria, page, size, sort));
    }

    @GetMapping("/{id}")
    public ProfessionalDetail get(@AuthenticationPrincipal AuthenticatedUser viewer, @PathVariable UUID id) {
        return professionals.getVisibleTo(id, viewer);
    }
}
