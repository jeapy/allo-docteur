package dev.jpzconception.allodocteur.sos.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Réglages SOS (app.sos.*). */
@ConfigurationProperties(prefix = "app.sos")
public record SosProperties(
        /** Durée de validité d'un accès d'urgence au dossier, en heures (il cesse aussi à la clôture de l'alerte). */
        @DefaultValue("24") int emergencyAccessHours,
        /** Nombre de structures de santé proposées au patient. */
        @DefaultValue("3") int nearestFacilities) {}
