package com.miadvisor.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** Wire format of the FastAPI ML service (snake_case JSON). */
public final class MlDtos {

    private MlDtos() {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MlPredictRequest(
            Double attendanceRate,
            Integer missedDeadlines,
            Double onTimeSubmissionRate,
            Double avgPracticeQuizScore,
            Double midtermScore,
            Double avgWeeklyStudyHours,
            Integer flashcardsReviewed,
            Double avgDaysStartedBeforeExam,
            Double lateNightStudyPct,
            Double avgSleepHours,
            Integer studySessionsLogged) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MlPredictResponse(
            int atRisk,
            double riskProbability,
            List<TopFeature> topFeatures,
            List<PathStep> decisionPath,
            String explanation,
            List<String> imputedFeatures,
            String disclaimer,
            String modelVersion) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TopFeature(String name, double importance) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PathStep(String feature, double value, double threshold, String direction) {}
}
