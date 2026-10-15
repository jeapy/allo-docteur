package dev.jpzconception.allodocteur.professional.domain;

/** Résultat de la vérification du numéro d'ordre d'un professionnel. */
public enum LicenseCheckStatus {
    UNCHECKED,
    FORMAT_VALID,
    CONFIRMED,
    NOT_FOUND
}
