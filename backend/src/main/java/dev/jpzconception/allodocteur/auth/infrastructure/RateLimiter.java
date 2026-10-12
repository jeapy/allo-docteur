package dev.jpzconception.allodocteur.auth.infrastructure;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limiteur à fenêtre fixe d'une minute, en mémoire (instance unique). Passer à Redis pour du multi-instance. */
public class RateLimiter {

    private static final long WINDOW_MS = 60_000;

    private record Window(long start, int count) {}

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        long now = clock.millis();
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MS);
        }
        boolean[] allowed = {false};
        windows.compute(key, (k, w) -> {
            if (w == null || now - w.start() >= WINDOW_MS) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (w.count() < limit) {
                allowed[0] = true;
                return new Window(w.start(), w.count() + 1);
            }
            return w;
        });
        return allowed[0];
    }
}
