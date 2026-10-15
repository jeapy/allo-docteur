package dev.jpzconception.allodocteur.professional.application;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Réglages des professionnels.
 *
 * @param requireConfirmedLicense l'approbation exige un numéro d'ordre confirmé
 * @param licenseDefaultPattern   format accepté pour un numéro d'ordre (expression régulière, sur le numéro normalisé)
 * @param licensePatterns         formats propres à un type de professionnel, par code de type (ex. {@code PHARMACIST})
 */
@ConfigurationProperties(prefix = "app.professionals")
public record ProfessionalProperties(boolean requireConfirmedLicense, String licenseDefaultPattern,
                                     Map<String, String> licensePatterns) {

    public ProfessionalProperties {
        if (licenseDefaultPattern == null || licenseDefaultPattern.isBlank()) {
            licenseDefaultPattern = "[A-Za-z0-9][A-Za-z0-9 ./_-]{3,49}";
        }
        licensePatterns = licensePatterns == null ? Map.of() : licensePatterns;
    }
}
