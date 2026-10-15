package dev.jpzconception.allodocteur.professional.application;

import org.springframework.stereotype.Component;

/** Stub : aucun registre officiel n'est consulté, la confirmation reste manuelle (administration). */
@Component
public class NoopLicenseRegistry implements LicenseRegistry {

    @Override
    public Answer lookup(String typeCode, String licenseNumber) {
        return Answer.UNAVAILABLE;
    }
}
