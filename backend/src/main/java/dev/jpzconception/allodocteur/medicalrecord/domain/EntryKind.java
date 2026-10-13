package dev.jpzconception.allodocteur.medicalrecord.domain;

import java.util.EnumSet;
import java.util.Set;

/** Types d'entrées du dossier / carnet de santé, avec qui peut les créer. */
public enum EntryKind {
    CONSULTATION_NOTE, DIAGNOSIS, VACCINATION, MEASUREMENT, LAB_RESULT, HOSPITALIZATION, PERSONAL_NOTE;

    private static final Set<EntryKind> BY_PROFESSIONAL =
            EnumSet.of(CONSULTATION_NOTE, DIAGNOSIS, VACCINATION, MEASUREMENT, LAB_RESULT, HOSPITALIZATION);
    private static final Set<EntryKind> BY_PATIENT =
            EnumSet.of(VACCINATION, MEASUREMENT, LAB_RESULT, HOSPITALIZATION, PERSONAL_NOTE);

    public boolean allowedForProfessional() {
        return BY_PROFESSIONAL.contains(this);
    }

    public boolean allowedForPatient() {
        return BY_PATIENT.contains(this);
    }
}
