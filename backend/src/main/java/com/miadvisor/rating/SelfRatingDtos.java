package com.miadvisor.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class SelfRatingDtos {

    private SelfRatingDtos() {}

    public record RatingRequest(@NotNull @Min(1) @Max(10) Integer rating) {}

    public record RatingResponse(Long id, Long gradedItemId, Integer rating, Instant createdAt) {}
}
