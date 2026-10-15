package dev.jpzconception.allodocteur.professional.application;

import dev.jpzconception.allodocteur.professional.domain.LicenseCheckStatus;
import dev.jpzconception.allodocteur.reference.domain.ProfessionalType;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Vérification automatique d'un numéro d'ordre : format (selon le type de professionnel), puis registre officiel
 * s'il y en a un. L'unicité est contrôlée à part (base de données). Une confirmation manuelle reste possible.
 */
@Component
public class LicenseVerifier {

    public enum Result { INVALID_FORMAT, FORMAT_VALID, REGISTRY_CONFIRMED, REGISTRY_NOT_FOUND }

    private final LicenseRegistry registry;
    private final Pattern defaultPattern;
    private final Map<String, Pattern> byType;

    public LicenseVerifier(LicenseRegistry registry, ProfessionalProperties props) {
        this.registry = registry;
        this.defaultPattern = Pattern.compile(props.licenseDefaultPattern());
        this.byType = props.licensePatterns().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toUpperCase(Locale.ROOT), e -> Pattern.compile(e.getValue())));
    }

    /** Espaces superflus retirés ; la casse est conservée (l'unicité, elle, l'ignore). */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    public Result check(ProfessionalType type, String rawNumber) {
        String number = normalize(rawNumber);
        Pattern pattern = byType.getOrDefault(type.getCode().toUpperCase(Locale.ROOT), defaultPattern);
        if (!pattern.matcher(number).matches()) return Result.INVALID_FORMAT;
        return switch (registry.lookup(type.getCode(), number)) {
            case FOUND -> Result.REGISTRY_CONFIRMED;
            case NOT_FOUND -> Result.REGISTRY_NOT_FOUND;
            case UNAVAILABLE -> Result.FORMAT_VALID;
        };
    }

    /** Statut à enregistrer sur le profil pour un résultat accepté. */
    public static LicenseCheckStatus statusOf(Result result) {
        return switch (result) {
            case REGISTRY_CONFIRMED -> LicenseCheckStatus.CONFIRMED;
            case REGISTRY_NOT_FOUND -> LicenseCheckStatus.NOT_FOUND;
            case FORMAT_VALID -> LicenseCheckStatus.FORMAT_VALID;
            case INVALID_FORMAT -> throw new IllegalArgumentException("Un format invalide n'est jamais enregistré");
        };
    }
}
