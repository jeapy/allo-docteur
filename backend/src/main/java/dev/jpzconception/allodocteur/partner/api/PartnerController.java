package dev.jpzconception.allodocteur.partner.api;

import dev.jpzconception.allodocteur.partner.application.PartnerService;
import dev.jpzconception.allodocteur.partner.application.PartnerService.PartnerView;
import dev.jpzconception.allodocteur.partner.domain.PartnerType;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Annuaire des partenaires actifs (cliniques, laboratoires, assureurs…). */
@RestController
@RequestMapping("/api/v1/partners")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Partenaires")
public class PartnerController {

    private final PartnerService partners;

    public PartnerController(PartnerService partners) {
        this.partners = partners;
    }

    @GetMapping
    public PageResponse<PartnerView> list(@RequestParam(required = false) PartnerType type,
                                          @RequestParam(required = false) Integer cityId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(partners.directory(type, cityId, page, size));
    }
}
