package com.coursecompass.recommendation;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum RecommendationType {
    STRONG_STAY,
    LEAN_STAY,
    UNCERTAIN,
    LEAN_WITHDRAW,
    STRONG_WITHDRAW;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isWithdraw() {
        return this == LEAN_WITHDRAW || this == STRONG_WITHDRAW;
    }
}
