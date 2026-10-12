package dev.jpzconception.allodocteur.teleconsultation.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        /** La conversation reste ouverte N jours après la fin du rendez-vous (questions de suivi). */
        @DefaultValue("7") int postCompletionDays) {}
