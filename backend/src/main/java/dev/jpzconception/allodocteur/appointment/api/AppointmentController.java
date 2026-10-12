package dev.jpzconception.allodocteur.appointment.api;

import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.AppointmentDto;
import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.BookRequest;
import dev.jpzconception.allodocteur.appointment.api.AppointmentDtos.CancelRequest;
import dev.jpzconception.allodocteur.appointment.application.AppointmentService;
import dev.jpzconception.allodocteur.appointment.domain.AppointmentStatus;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/appointments")
@Tag(name = "Rendez-vous")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDto book(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody BookRequest request) {
        return service.book(me, request);
    }

    /** Mes rendez-vous (patient : ceux que j'ai réservés ; professionnel : ceux qui me sont adressés). */
    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
    public PageResponse<AppointmentDto> mine(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) List<AppointmentStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return PageResponse.of(service.listMine(me, status, from, to, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL', 'ADMIN')")
    public AppointmentDto get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public AppointmentDto confirm(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.confirm(me, id);
    }

    /** Patient, professionnel concerné, ou administrateur SUPER/SUPPORT. Motif obligatoire hors patient. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL', 'ADMIN')")
    public AppointmentDto cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                 @Valid @RequestBody(required = false) CancelRequest request) {
        return service.cancel(me, id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public AppointmentDto start(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.start(me, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public AppointmentDto complete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.complete(me, id);
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public AppointmentDto noShow(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.noShow(me, id);
    }
}
