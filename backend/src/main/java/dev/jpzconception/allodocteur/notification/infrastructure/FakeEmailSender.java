package dev.jpzconception.allodocteur.notification.infrastructure;

import dev.jpzconception.allodocteur.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Stub : n'envoie rien, journalise le message. À remplacer par un vrai fournisseur plus tard. */
@Component
public class FakeEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(FakeEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[FAKE EMAIL] to={} subject={}\n{}", to, subject, body);
    }
}
