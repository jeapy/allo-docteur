package dev.jpzconception.allodocteur.notification.infrastructure;

import dev.jpzconception.allodocteur.notification.application.EmailSender;
import dev.jpzconception.allodocteur.notification.application.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Canaux simulés : push et SMS journalisent, l'e-mail passe par le port {@link EmailSender} existant. */
@Configuration
public class FakeNotificationChannels {

    private static final Logger log = LoggerFactory.getLogger(FakeNotificationChannels.class);

    @Bean
    NotificationChannel fakePushChannel() {
        return new NotificationChannel() {
            @Override public String code() { return "PUSH"; }
            @Override public boolean canReach(Recipient r) { return true; }
            @Override public void send(Recipient r, String title, String body) {
                log.info("[FAKE PUSH] user={} title={}", r.userId(), title);
            }
        };
    }

    @Bean
    NotificationChannel fakeSmsChannel() {
        return new NotificationChannel() {
            @Override public String code() { return "SMS"; }
            @Override public boolean canReach(Recipient r) { return r.phone() != null && !r.phone().isBlank(); }
            @Override public void send(Recipient r, String title, String body) {
                log.info("[FAKE SMS] to={} {}", r.phone(), title);
            }
        };
    }

    @Bean
    NotificationChannel emailNotificationChannel(EmailSender email) {
        return new NotificationChannel() {
            @Override public String code() { return "EMAIL"; }
            @Override public boolean canReach(Recipient r) { return r.email() != null && !r.email().isBlank(); }
            @Override public void send(Recipient r, String title, String body) {
                email.send(r.email(), title, body);
            }
        };
    }
}
