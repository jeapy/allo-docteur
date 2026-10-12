package dev.jpzconception.allodocteur.appointment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Règles de réservation, ajustables sans recompiler (app.appointments.*). */
@ConfigurationProperties(prefix = "app.appointments")
public record AppointmentProperties(
        /** Fuseau dans lequel sont exprimées les plages de disponibilité des professionnels. */
        @DefaultValue("Africa/Abidjan") String zone,
        @DefaultValue("30") int teleconsultationMinutes,
        @DefaultValue("60") int homeVisitMinutes,
        /** Préavis minimal avant le début d'un créneau réservable. */
        @DefaultValue("60") int minNoticeMinutes,
        /** Réservation possible jusqu'à N jours à l'avance. */
        @DefaultValue("60") int horizonDays,
        /** Un patient ne peut plus annuler un rendez-vous confirmé à moins de N minutes du début. */
        @DefaultValue("120") int patientCancelMinutes,
        /** Le professionnel peut démarrer la consultation N minutes avant l'heure prévue. */
        @DefaultValue("15") int startEarlyMinutes,
        /** Absence du patient déclarable N minutes après l'heure prévue. */
        @DefaultValue("15") int noShowAfterMinutes) {}
