package dev.jpzconception.allodocteur.professional.api;

import dev.jpzconception.allodocteur.professional.api.ProfessionalDtos.ProfileRequest;
import dev.jpzconception.allodocteur.professional.domain.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Contrats des endpoints publics (sans authentification) de candidature professionnelle. */
public final class PublicProfessionalDtos {

    private PublicProfessionalDtos() {}

    public record AccountRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Format E.164 attendu (ex. +2250700000000)")
            String phone) {}

    public record ApplicationRequest(
            @NotNull @Valid AccountRequest account,
            @NotNull @Valid ProfileRequest profile,
            @AssertTrue(message = "Les conditions d'utilisation doivent être acceptées.") boolean acceptTerms) {}

    public record ApplicationReceipt(UUID reference, String email, VerificationStatus status) {}

    /** Résultat d'un contrôle à blanc du numéro d'ordre (aucune donnée n'est enregistrée). */
    public enum LicenseCheckOutcome { INVALID_FORMAT, ALREADY_REGISTERED, FORMAT_VALID, REGISTRY_CONFIRMED, REGISTRY_NOT_FOUND }

    public record LicenseCheckResponse(LicenseCheckOutcome status, String message) {}
}
