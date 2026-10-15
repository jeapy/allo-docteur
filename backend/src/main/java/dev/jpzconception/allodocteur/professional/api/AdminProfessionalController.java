package dev.jpzconception.allodocteur.professional.api;

import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.AdminProfessionalRow;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.LicenseCheckRequest;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfessionalDetail;
import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ReasonRequest;
import dev.jpzconception.allodocteur.professional.application.VerificationService;
import dev.jpzconception.allodocteur.professional.domain.VerificationStatus;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/professionals")
@Tag(name = "Admin – validation des professionnels")
public class AdminProfessionalController {

    private static final String CAN_REVIEW = "hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')";

    private final VerificationService service;

    public AdminProfessionalController(VerificationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AdminProfessionalRow> list(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) Integer typeId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return PageResponse.of(service.list(status, typeId, q, page, size, sort));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfessionalDetail detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_REVIEW)
    public ProfessionalDetail approve(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        return service.approve(admin.id(), id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(CAN_REVIEW)
    public ProfessionalDetail reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                     @Valid @RequestBody ReasonRequest request) {
        return service.reject(admin.id(), id, request.reason());
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize(CAN_REVIEW)
    public ProfessionalDetail suspend(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                      @Valid @RequestBody ReasonRequest request) {
        return service.suspend(admin.id(), id, request.reason());
    }

    @PostMapping("/{id}/license-check")
    @PreAuthorize(CAN_REVIEW)
    public ProfessionalDetail licenseCheck(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                           @Valid @RequestBody LicenseCheckRequest request) {
        return service.confirmLicense(admin.id(), id, request.result(), request.note());
    }
}
