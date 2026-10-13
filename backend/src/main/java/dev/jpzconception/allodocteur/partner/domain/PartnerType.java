package dev.jpzconception.allodocteur.partner.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PartnerType {
    CLINIC, HEALTH_CENTER, HOSPITAL, PHARMACY, LAB, AMBULANCE, INSURANCE, MUTUAL;

    /** Types auprès desquels un patient peut enregistrer une police. */
    public static final Set<PartnerType> COVERAGE = EnumSet.of(INSURANCE, MUTUAL);
}
