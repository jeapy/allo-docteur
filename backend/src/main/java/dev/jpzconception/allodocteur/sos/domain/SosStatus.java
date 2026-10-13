package dev.jpzconception.allodocteur.sos.domain;

import java.util.EnumSet;
import java.util.Set;

public enum SosStatus {
    OPEN, ACCEPTED, RESOLVED, CANCELLED;

    /** Alertes « en cours » : encore ouvertes ou prises en charge. */
    public static final Set<SosStatus> ACTIVE = EnumSet.of(OPEN, ACCEPTED);
}
