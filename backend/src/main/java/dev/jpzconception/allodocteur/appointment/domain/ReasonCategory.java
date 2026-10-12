package dev.jpzconception.allodocteur.appointment.domain;

/** Motifs du cahier des charges (§6.4 et §6.5). */
public enum ReasonCategory {
    CONSULTATION(false),
    TREATMENT_FOLLOWUP(false),
    MEDICAL_CERTIFICATE(false),
    PREGNANCY_FOLLOWUP(false),
    POSTNATAL(false),
    BREASTFEEDING_ADVICE(false),
    INJECTION(true),
    DRESSING(true),
    INFUSION(true),
    BLOOD_PRESSURE(true),
    MEDICAL_SURVEILLANCE(true),
    ELDERLY_ASSISTANCE(true),
    HYGIENE_CARE(true),
    DAILY_COMPANIONSHIP(true);

    private final boolean physicalPresenceRequired;

    ReasonCategory(boolean physicalPresenceRequired) {
        this.physicalPresenceRequired = physicalPresenceRequired;
    }

    /** Les soins qui demandent un geste physique ne peuvent pas se faire à distance. */
    public boolean requiresHomeVisit() {
        return physicalPresenceRequired;
    }
}
