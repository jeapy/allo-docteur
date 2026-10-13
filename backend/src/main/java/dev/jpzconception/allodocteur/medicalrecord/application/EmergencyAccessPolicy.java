package dev.jpzconception.allodocteur.medicalrecord.application;

import java.util.UUID;

/**
 * Port : un accès d'urgence (« bris de glace ») est-il en cours pour ce professionnel et cette personne ?
 * Implémenté par le module SOS ; le dossier médical n'en connaît pas les détails.
 */
public interface EmergencyAccessPolicy {

    boolean allows(UUID professionalId, UUID accountId, UUID dependentId);
}
