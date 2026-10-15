package dev.jpzconception.allodocteur.professional.application;

/**
 * Port vers un registre officiel d'un ordre professionnel (ordre des médecins, des pharmaciens…).
 * Aucune implémentation réelle n'est branchée : voir {@link NoopLicenseRegistry}.
 */
public interface LicenseRegistry {

    enum Answer { FOUND, NOT_FOUND, UNAVAILABLE }

    /** @param typeCode code du type de professionnel (ex. {@code PHARMACIST}) */
    Answer lookup(String typeCode, String licenseNumber);
}
