package dev.jpzconception.allodocteur.complaint.api;

import dev.jpzconception.allodocteur.complaint.api.ComplaintDtos.CreateComplaintRequest;
import dev.jpzconception.allodocteur.complaint.api.ComplaintDtos.MessageRequest;
import dev.jpzconception.allodocteur.complaint.application.ComplaintService;
import dev.jpzconception.allodocteur.complaint.application.ComplaintService.ComplaintDetail;
import dev.jpzconception.allodocteur.complaint.application.ComplaintService.ComplaintSummary;
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

/** Réclamations du patient ou du professionnel connecté. */
@RestController
@RequestMapping("/api/v1/complaints")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Réclamations")
public class ComplaintController {

    private final ComplaintService complaints;

    public ComplaintController(ComplaintService complaints) {
        this.complaints = complaints;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintDetail create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody CreateComplaintRequest req) {
        return complaints.create(me, req.subject(), req.description(), req.appointmentId());
    }

    @GetMapping("/mine")
    public PageResponse<ComplaintSummary> mine(@AuthenticationPrincipal AuthenticatedUser me,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(complaints.mine(me.id(), page, size));
    }

    @GetMapping("/{id}")
    public ComplaintDetail get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return complaints.getMine(me.id(), id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintDetail reply(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id,
                                 @Valid @RequestBody MessageRequest req) {
        return complaints.reply(me, id, req.body());
    }
}
