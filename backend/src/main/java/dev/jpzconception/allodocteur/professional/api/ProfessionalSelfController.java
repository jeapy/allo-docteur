package dev.jpzconception.allodocteur.professional.api;

import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.*;
import dev.jpzconception.allodocteur.professional.application.AvailabilityService;
import dev.jpzconception.allodocteur.professional.application.AvailabilityService.AvailabilityView;
import dev.jpzconception.allodocteur.professional.application.DocumentService;
import dev.jpzconception.allodocteur.professional.application.ProfessionalService;
import dev.jpzconception.allodocteur.professional.domain.DocumentType;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Espace du professionnel connecté : profil, disponibilités, justificatifs. */
@RestController
@RequestMapping("/api/v1/professionals/me")
@PreAuthorize("hasRole('PROFESSIONAL')")
@Tag(name = "Professionnel – mon espace")
public class ProfessionalSelfController {

    private final ProfessionalService profiles;
    private final AvailabilityService availability;
    private final DocumentService documents;

    public ProfessionalSelfController(ProfessionalService profiles, AvailabilityService availability,
                                      DocumentService documents) {
        this.profiles = profiles;
        this.availability = availability;
        this.documents = documents;
    }

    @GetMapping
    public ProfessionalDetail get(@AuthenticationPrincipal AuthenticatedUser me) {
        return profiles.getOwn(me.id());
    }

    /** Crée ou met à jour le profil. Un nouveau profil est « en attente » jusqu'à validation par un administrateur. */
    @PutMapping
    public ProfessionalDetail save(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody ProfileRequest request) {
        return profiles.upsert(me.id(), request);
    }

    @GetMapping("/availability")
    public AvailabilityView availability(@AuthenticationPrincipal AuthenticatedUser me) {
        return availability.view(me.id());
    }

    @PutMapping("/availability/rules")
    public AvailabilityView replaceRules(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody RulesRequest request) {
        return availability.replaceRules(me.id(), request.rules());
    }

    @PostMapping("/availability/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    public ExceptionDto addException(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody ExceptionRequest request) {
        return availability.addException(me.id(), request);
    }

    @DeleteMapping("/availability/exceptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteException(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        availability.deleteException(me.id(), id);
    }

    @GetMapping("/documents")
    public List<DocumentDto> listDocuments(@AuthenticationPrincipal AuthenticatedUser me) {
        return documents.list(me.id());
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDto upload(@AuthenticationPrincipal AuthenticatedUser me,
                              @RequestParam DocumentType type, @RequestParam MultipartFile file) {
        return documents.upload(me.id(), type, file);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        documents.delete(me.id(), id);
    }
}
