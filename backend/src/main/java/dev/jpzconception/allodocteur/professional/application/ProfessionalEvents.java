package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.professional.domain.VerificationStatus;
import java.util.UUID;

public final class ProfessionalEvents {

    private ProfessionalEvents() {}

    /** Décision de l'administration sur un profil (validé, rejeté, suspendu). */
    public record VerificationChanged(UUID professionalId, VerificationStatus status) {}
}
