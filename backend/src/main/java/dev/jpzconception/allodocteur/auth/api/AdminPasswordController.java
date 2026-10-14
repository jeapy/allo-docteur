package dev.jpzconception.allodocteur.auth.api;

import dev.jpzconception.allodocteur.auth.application.AuthService;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin – utilisateurs")
public class AdminPasswordController {

    private final AuthService service;

    public AdminPasswordController(AuthService service) {
        this.service = service;
    }

    /** Envoie à l'utilisateur un lien de réinitialisation de mot de passe (valable 1 heure). */
    @PostMapping("/{id}/password-reset")
    @PreAuthorize("hasRole('ADMIN') and hasAnyAuthority('LEVEL_SUPER', 'LEVEL_SUPPORT')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendResetLink(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        service.adminTriggerPasswordReset(actor.id(), id);
    }
}
