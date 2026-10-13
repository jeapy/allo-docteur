package dev.jpzconception.allodocteur.notification.application;

import dev.jpzconception.allodocteur.notification.domain.NotificationType;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Réglages des notifications (app.notifications.*). */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        /** Types doublés d'un envoi externe (push, SMS, e-mail) ; les autres restent dans l'application. */
        @DefaultValue({"APPOINTMENT_REMINDER", "SOS", "PRESCRIPTION", "COMPLAINT_UPDATE"}) List<NotificationType> externalTypes,
        /** Rappel envoyé quand un rendez-vous confirmé commence dans moins de N minutes. */
        @DefaultValue("60") int reminderMinutes) {}
