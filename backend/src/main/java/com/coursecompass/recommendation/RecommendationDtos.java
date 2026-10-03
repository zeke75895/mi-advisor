package com.coursecompass.recommendation;

import java.time.Instant;
import java.util.List;

public final class RecommendationDtos {

    private RecommendationDtos() {}

    public record Signals(
            double modelRisk,
            boolean modelAtRisk,
            double projectedFinal,
            Double currentGrade,
            double remainingWeight,
            double distressRatio,
            int lowRatings,
            int ratedItems) {}

    public record RecommendationResponse(
            Long courseId,
            RecommendationType recommendation,
            double confidence,
            double withdrawScore,
            String headline,
            List<String> reasoning,
            List<String> actions,
            Signals signals,
            String appliedRule,
            String advisorNote,
            String disclaimer,
            String modelVersion,
            Instant riskComputedAt) {}
}
