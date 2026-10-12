package dev.jpzconception.allodocteur.teleconsultation.application;

import dev.jpzconception.allodocteur.teleconsultation.domain.MediaMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Port vers le fournisseur audio/vidéo (Twilio, Agora, Jitsi…). Implémentation factice par défaut. */
public interface VideoProvider {

    /** Nom stocké avec la session, pour savoir quel fournisseur gère la salle. */
    String name();

    /** Crée la salle et renvoie son identifiant côté fournisseur. */
    String createRoom(UUID appointmentId, MediaMode media);

    /** Jeton d'accès personnel et de courte durée pour rejoindre la salle. */
    JoinInfo issueJoinInfo(String roomId, UUID userId, Duration ttl);

    void closeRoom(String roomId);

    record JoinInfo(String url, String token, Instant expiresAt) {}
}
