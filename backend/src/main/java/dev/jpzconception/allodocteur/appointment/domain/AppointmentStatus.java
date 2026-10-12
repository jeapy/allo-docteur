package dev.jpzconception.allodocteur.appointment.domain;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW;

    /** Statuts qui occupent le créneau du professionnel. */
    public static final Set<AppointmentStatus> BLOCKING = EnumSet.of(REQUESTED, CONFIRMED, IN_PROGRESS);
}
