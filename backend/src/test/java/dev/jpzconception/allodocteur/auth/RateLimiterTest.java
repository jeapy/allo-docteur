package dev.jpzconception.allodocteur.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jpzconception.allodocteur.auth.infrastructure.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void blocksAfterLimitAndResetsNextWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(3, clock);
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        assertThat(limiter.tryAcquire("other")).isTrue();
        clock.now = clock.now.plus(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire("ip")).isTrue();
    }
}
