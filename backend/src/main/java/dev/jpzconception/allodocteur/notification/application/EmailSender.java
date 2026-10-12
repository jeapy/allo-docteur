package dev.jpzconception.allodocteur.notification.application;

/** Port d'envoi d'e-mails. L'implémentation par défaut est un stub (journalisation). */
public interface EmailSender {
    void send(String to, String subject, String body);
}
