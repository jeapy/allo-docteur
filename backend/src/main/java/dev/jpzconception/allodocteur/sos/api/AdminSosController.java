package dev.jpzconception.allodocteur.sos.api;

import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import dev.jpzconception.allodocteur.sos.api.SosDtos.AdminAlertDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.FacilityDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.FacilityRequest;
import dev.jpzconception.allodocteur.sos.api.SosDtos.ReasonRequest;
import dev.jpzconception.allodocteur.sos.api.SosDtos.SummaryDto;
import dev.jpzconception.allodocteur.sos.application.FacilityService;
import dev.jpzconception.allodocteur.sos.application.SosService;
import dev.jpzconception.allodocteur.sos.domain.SosStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Suivi des urgences et gestion des structures de santé. Aucune donnée médicale n'est exposée. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration – SOS")
public class AdminSosController {

    private final SosService sos;
    private final FacilityService facilities;

    public AdminSosController(SosService sos, FacilityService facilities) {
        this.sos = sos;
        this.facilities = facilities;
    }

    @GetMapping("/sos")
    public PageResponse<AdminAlertDto> list(@RequestParam(required = false) List<SosStatus> status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(sos.adminList(status, page, size));
    }

    /** Urgences en cours : alertes ouvertes (sans professionnel) et prises en charge. */
    @GetMapping("/sos/summary")
    public SummaryDto summary() {
        return sos.summary();
    }

    @PostMapping("/sos/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                       @Valid @RequestBody ReasonRequest request) {
        sos.adminCancel(me, id, request.reason());
    }

    @GetMapping("/health-facilities")
    public PageResponse<FacilityDto> facilities(@RequestParam(required = false) Integer cityId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(facilities.list(cityId, false, page, size));
    }

    @PostMapping("/health-facilities")
    @PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityDto createFacility(@AuthenticationPrincipal AuthenticatedUser me,
                                      @Valid @RequestBody FacilityRequest request) {
        return facilities.create(me.id(), request);
    }

    @PutMapping("/health-facilities/{id}")
    @PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
    public FacilityDto updateFacility(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                      @Valid @RequestBody FacilityRequest request) {
        return facilities.update(me.id(), id, request);
    }

    @PostMapping("/health-facilities/{id}/activate")
    @PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
    public FacilityDto activate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return facilities.setActive(me.id(), id, true);
    }

    @PostMapping("/health-facilities/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
    public FacilityDto deactivate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return facilities.setActive(me.id(), id, false);
    }
}
