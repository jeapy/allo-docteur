package dev.jpzconception.allodocteur.notification.application;

import java.util.UUID;

/**
 * Port d'envoi externe d'une notification (push, SMS, e-mail). Les implémentations par défaut journalisent seulement.
 * Le texte transmis ne contient aucune donnée de santé.
 */
public interface NotificationChannel {

    String code();

    boolean canReach(Recipient recipient);

    void send(Recipient recipient, String title, String body);

    record Recipient(UUID userId, String email, String phone) {}
}
