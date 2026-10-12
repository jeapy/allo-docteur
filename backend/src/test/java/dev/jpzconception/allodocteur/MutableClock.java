package dev.jpzconception.allodocteur;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Horloge de test : temps réel décalé d'un offset réglable, pour « voyager » jusqu'à l'heure d'un rendez-vous. */
public class MutableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;

    public void travelTo(Instant target) {
        offset = Duration.between(Instant.now(), target);
    }

    public void reset() {
        offset = Duration.ZERO;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(offset);
    }
}
