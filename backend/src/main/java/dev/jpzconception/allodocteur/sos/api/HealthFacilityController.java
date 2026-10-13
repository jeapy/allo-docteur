package dev.jpzconception.allodocteur.sos.api;

import dev.jpzconception.allodocteur.shared.web.PageResponse;
import dev.jpzconception.allodocteur.sos.api.SosDtos.FacilityDto;
import dev.jpzconception.allodocteur.sos.application.FacilityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Orientation vers les structures de santé (patients et professionnels). */
@RestController
@RequestMapping("/api/v1/health-facilities")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Structures de santé")
public class HealthFacilityController {

    private final FacilityService service;

    public HealthFacilityController(FacilityService service) {
        this.service = service;
    }

    /** Structures d'urgence actives, de la plus proche à la plus éloignée. */
    @GetMapping("/nearby")
    public List<FacilityDto> nearby(@RequestParam @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                    @RequestParam @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                                    @RequestParam(defaultValue = "5") int limit) {
        return service.nearest(latitude, longitude, Math.min(Math.max(limit, 1), 20));
    }

    @GetMapping
    public PageResponse<FacilityDto> list(@RequestParam(required = false) Integer cityId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(cityId, true, page, size));
    }
}
