package dev.jpzconception.allodocteur.prescription.api;

import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.CancelRequest;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.DetailDto;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.IssueRequest;
import dev.jpzconception.allodocteur.prescription.api.PrescriptionDtos.SummaryDto;
import dev.jpzconception.allodocteur.prescription.application.PrescriptionService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
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
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Ordonnances")
public class PrescriptionController {

    private final PrescriptionService service;

    public PrescriptionController(PrescriptionService service) {
        this.service = service;
    }

    @PostMapping("/appointments/{appointmentId}/prescriptions")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    @ResponseStatus(HttpStatus.CREATED)
    public DetailDto issue(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID appointmentId,
                           @Valid @RequestBody IssueRequest request) {
        return service.issue(me, appointmentId, request);
    }

    /** Ordonnances reçues (patient) ou émises (professionnel) : résumés sans contenu médical. */
    @GetMapping("/prescriptions")
    public PageResponse<SummaryDto> mine(@AuthenticationPrincipal AuthenticatedUser me,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.mine(me, page, size));
    }

    @GetMapping("/prescriptions/{id}")
    public DetailDto get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/prescriptions/{id}/cancel")
    @PreAuthorize("hasRole('PROFESSIONAL')")
    public DetailDto cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                            @Valid @RequestBody CancelRequest request) {
        return service.cancel(me, id, request);
    }

    @GetMapping("/medical-records/{recordId}/prescriptions")
    public PageResponse<DetailDto> forRecord(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID recordId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.forRecord(me, recordId, page, size));
    }
}
