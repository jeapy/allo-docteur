package dev.jpzconception.allodocteur.complaint.api;

import dev.jpzconception.allodocteur.complaint.api.ComplaintDtos.MessageRequest;
import dev.jpzconception.allodocteur.complaint.api.ComplaintDtos.ResolutionRequest;
import dev.jpzconception.allodocteur.complaint.application.ComplaintService;
import dev.jpzconception.allodocteur.complaint.application.ComplaintService.ComplaintDetail;
import dev.jpzconception.allodocteur.complaint.application.ComplaintService.ComplaintSummary;
import dev.jpzconception.allodocteur.complaint.domain.ComplaintStatus;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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

/** Traitement des réclamations (support, super-administrateur). */
@RestController
@RequestMapping("/api/v1/admin/complaints")
@PreAuthorize("hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
@Tag(name = "Administration – Réclamations")
public class AdminComplaintController {

    private final ComplaintService complaints;

    public AdminComplaintController(ComplaintService complaints) {
        this.complaints = complaints;
    }

    @GetMapping
    public PageResponse<ComplaintSummary> list(@RequestParam(required = false) ComplaintStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(complaints.adminList(status, page, size));
    }

    @GetMapping("/summary")
    public Map<ComplaintStatus, Long> summary() {
        return complaints.summary();
    }

    @GetMapping("/{id}")
    public ComplaintDetail get(@PathVariable UUID id) {
        return complaints.adminGet(id);
    }

    @PostMapping("/{id}/take")
    public ComplaintDetail take(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return complaints.take(me, id);
    }

    @PostMapping("/{id}/messages")
    public ComplaintDetail reply(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                 @Valid @RequestBody MessageRequest req) {
        return complaints.adminReply(me, id, req.body());
    }

    @PostMapping("/{id}/resolve")
    public ComplaintDetail resolve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                   @Valid @RequestBody ResolutionRequest req) {
        return complaints.resolve(me, id, req.text());
    }

    @PostMapping("/{id}/reject")
    public ComplaintDetail reject(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                  @Valid @RequestBody ResolutionRequest req) {
        return complaints.reject(me, id, req.text());
    }
}
