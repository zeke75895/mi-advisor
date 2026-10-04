package com.miadvisor.llm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sliding-window caps on Gemini calls: a per-user limit for the generate buttons and a global
 * per-minute limit that protects the shared free-tier quota. In-memory, so limits reset on restart and
 * apply per backend instance (fine for a single Railway instance).
 */
@Component
public class AiRateLimiter {

    private static final Duration GLOBAL_WINDOW = Duration.ofMinutes(1);

    private final int userLimit;
    private final Duration userWindow;
    private final int globalLimit;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> perUser = new HashMap<>();
    private final Deque<Instant> global = new ArrayDeque<>();

    public AiRateLimiter(
            @Value("${app.ai.user-limit}") int userLimit,
            @Value("${app.ai.user-window}") Duration userWindow,
            @Value("${app.ai.global-limit-per-minute}") int globalLimit,
            Clock clock) {
        this.userLimit = userLimit;
        this.userWindow = userWindow;
        this.globalLimit = globalLimit;
        this.clock = clock;
    }

    /** Reserves one AI request for the user, or throws RateLimitException with how long to wait. */
    public synchronized void acquire(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> mine = perUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        prune(mine, now, userWindow);
        prune(global, now, GLOBAL_WINDOW);
        if (mine.size() >= userLimit) {
            Duration wait = Duration.between(now, mine.peekFirst().plus(userWindow));
            throw new RateLimitException("You've used " + userLimit + " AI generations in the last "
                    + userWindow.toMinutes() + " minutes. Try again in " + minutes(wait) + ".", wait);
        }
        if (global.size() >= globalLimit) {
            Duration wait = Duration.between(now, global.peekFirst().plus(GLOBAL_WINDOW));
            throw new RateLimitException("MiAdvisor is busy right now. Try again in a minute.", wait);
        }
        mine.addLast(now);
        global.addLast(now);
    }

    /** For background AI use (recommendation wording): takes a global slot if one is free, never throws. */
    public synchronized boolean tryAcquireGlobal() {
        Instant now = clock.instant();
        prune(global, now, GLOBAL_WINDOW);
        if (global.size() >= globalLimit) {
            return false;
        }
        global.addLast(now);
        return true;
    }

    private static void prune(Deque<Instant> calls, Instant now, Duration window) {
        while (!calls.isEmpty() && !calls.peekFirst().plus(window).isAfter(now)) {
            calls.pollFirst();
        }
    }

    private static String minutes(Duration wait) {
        long mins = Math.max(1, (wait.toSeconds() + 59) / 60);
        return mins == 1 ? "1 minute" : mins + " minutes";
    }
}
