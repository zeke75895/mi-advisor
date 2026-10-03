package com.coursecompass.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AiRateLimiterTest {

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T12:00:00Z");

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
            return now;
        }
    }

    @Test
    void capsEachUserWithinTheWindowThenRecovers() {
        MutableClock clock = new MutableClock();
        AiRateLimiter limiter = new AiRateLimiter(2, Duration.ofMinutes(10), 100, clock);

        limiter.acquire(1L);
        clock.now = clock.now.plusSeconds(60);
        limiter.acquire(1L);
        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("2 AI generations in the last 10 minutes")
                .hasMessageContaining("9 minutes")
                .satisfies(e -> assertThat(((RateLimitException) e).getRetryAfter()).isEqualTo(Duration.ofMinutes(9)));

        limiter.acquire(2L); // other users are unaffected

        clock.now = clock.now.plus(Duration.ofMinutes(9));
        limiter.acquire(1L); // the first call has left the window
    }

    @Test
    void globalLimitCoversAllUsersAndBackgroundCalls() {
        MutableClock clock = new MutableClock();
        AiRateLimiter limiter = new AiRateLimiter(100, Duration.ofMinutes(10), 2, clock);

        limiter.acquire(1L);
        assertThat(limiter.tryAcquireGlobal()).isTrue();
        assertThat(limiter.tryAcquireGlobal()).isFalse();
        assertThatThrownBy(() -> limiter.acquire(2L)).hasMessageContaining("busy");

        clock.now = clock.now.plusSeconds(61);
        assertThat(limiter.tryAcquireGlobal()).isTrue();
    }
}
