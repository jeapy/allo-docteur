package dev.jpzconception.allodocteur.sos.api;

import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import dev.jpzconception.allodocteur.sos.api.SosDtos.AlertDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.CreateRequest;
import dev.jpzconception.allodocteur.sos.api.SosDtos.EmergencyAccessDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.OpenAlertDto;
import dev.jpzconception.allodocteur.sos.api.SosDtos.OptionalReasonRequest;
import dev.jpzconception.allodocteur.sos.api.SosDtos.ReasonRequest;
import dev.jpzconception.allodocteur.sos.api.SosDtos.ResolveRequest;
import dev.jpzconception.allodocteur.sos.application.SosService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sos")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "SOS")
public class SosController {

    private final SosService service;

    public SosController(SosService service) {
        this.service = service;
    }

    /** Déclenche une alerte géolocalisée. Renvoie aussi les structures de santé les plus proches. */
    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDto create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody CreateRequest request) {
        return service.create(me, request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public PageResponse<AlertDto> mine(@AuthenticationPrincipal AuthenticatedUser me,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.mine(me, page, size));
    }

    /** Alertes ouvertes de ma ville (professionnels approuvés proposant la visite à domicile). */
    @GetMapping("/open")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public PageResponse<OpenAlertDto> open(@AuthenticationPrincipal AuthenticatedUser me,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.open(me, page, size));
    }

    /** Propriétaire de l'alerte ou professionnel assigné. */
    @GetMapping("/{id}")
    public AlertDto get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public AlertDto accept(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.accept(me, id);
    }

    /** « Bris de glace » : accès motivé au dossier du patient pendant l'urgence, sans son consentement. */
    @PostMapping("/{id}/emergency-access")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public EmergencyAccessDto emergencyAccess(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                              @Valid @RequestBody ReasonRequest request) {
        return service.emergencyAccess(me, id, request.reason());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public AlertDto resolve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                            @Valid @RequestBody(required = false) ResolveRequest request) {
        return service.resolve(me, id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('PATIENT')")
    public AlertDto cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                           @Valid @RequestBody(required = false) OptionalReasonRequest request) {
        return service.cancelByPatient(me, id, request == null ? null : request.reason());
    }
}
