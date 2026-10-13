package dev.jpzconception.allodocteur.prescription.application;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Règles d'émission des ordonnances (app.prescriptions.*). */
@ConfigurationProperties(prefix = "app.prescriptions")
public record PrescriptionProperties(
        /** Durée de validité d'une ordonnance, en jours. */
        @DefaultValue("90") int validityDays,
        /** Une ordonnance peut être émise jusqu'à N jours après la fin de la consultation. */
        @DefaultValue("3") int issueWindowDays,
        /** Codes des types de professionnels habilités à prescrire. */
        @DefaultValue({"GENERAL_PRACTITIONER", "SPECIALIST_DOCTOR", "MIDWIFE"}) List<String> allowedProfessionalTypes) {}
