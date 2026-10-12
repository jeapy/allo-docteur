package dev.jpzconception.allodocteur.teleconsultation.infrastructure;

import dev.jpzconception.allodocteur.teleconsultation.application.VideoProvider;
import dev.jpzconception.allodocteur.teleconsultation.domain.MediaMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fournisseur factice : aucune salle réelle, des jetons aléatoires. À remplacer par une vraie intégration. */
@Component
public class FakeVideoProvider implements VideoProvider {

    private static final Logger log = LoggerFactory.getLogger(FakeVideoProvider.class);

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public FakeVideoProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public String createRoom(UUID appointmentId, MediaMode media) {
        String roomId = "fake-" + media.name().toLowerCase() + "-" + appointmentId;
        log.info("[FAKE VIDEO] salle créée : {}", roomId);
        return roomId;
    }

    @Override
    public JoinInfo issueJoinInfo(String roomId, UUID userId, Duration ttl) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new JoinInfo("https://video.fake.local/rooms/" + roomId, token, Instant.now(clock).plus(ttl));
    }

    @Override
    public void closeRoom(String roomId) {
        log.info("[FAKE VIDEO] salle fermée : {}", roomId);
    }
}
