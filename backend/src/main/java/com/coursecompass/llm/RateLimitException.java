package com.coursecompass.llm;

import java.time.Duration;

public class RateLimitException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
