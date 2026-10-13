package dev.jpzconception.allodocteur.medicalrecord.api;

import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.AcceptConsentRequest;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.ConsentHistoryDto;
import dev.jpzconception.allodocteur.medicalrecord.api.MedicalRecordDtos.ConsentStatusDto;
import dev.jpzconception.allodocteur.medicalrecord.application.ConsentService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consents")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Consentement")
public class ConsentController {

    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    /** Texte courant du consentement et état de mon consentement. */
    @GetMapping("/current")
    public ConsentStatusDto current(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.status(me.id());
    }

    @PostMapping
    public ConsentStatusDto accept(@AuthenticationPrincipal AuthenticatedUser me,
                                   @Valid @RequestBody AcceptConsentRequest request) {
        return service.accept(me.id(), request.version());
    }

    /** Retire le consentement : les professionnels n'ont plus accès au dossier. */
    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthenticatedUser me) {
        service.revoke(me.id());
    }

    @GetMapping("/history")
    public List<ConsentHistoryDto> history(@AuthenticationPrincipal AuthenticatedUser me) {
        return service.history(me.id());
    }
}
