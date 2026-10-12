package dev.jpzconception.allodocteur.appointment.domain;

import java.util.UUID;

/**
 * Événements publiés à chaque changement d'état. Les autres modules (téléconsultation, notifications, paiements…)
 * s'y abonnent via @EventListener sans que le module rendez-vous les connaisse.
 */
public final class AppointmentEvents {

    private AppointmentEvents() {}

    public record Requested(UUID appointmentId, UUID patientId, UUID professionalId) {}

    public record Confirmed(UUID appointmentId, UUID patientId, UUID professionalId) {}

    public record Cancelled(UUID appointmentId, UUID patientId, UUID professionalId, UUID cancelledBy) {}

    public record Started(UUID appointmentId, UUID patientId, UUID professionalId, AppointmentType type) {}

    public record Completed(UUID appointmentId, UUID patientId, UUID professionalId) {}

    public record NoShow(UUID appointmentId, UUID patientId, UUID professionalId) {}
}
