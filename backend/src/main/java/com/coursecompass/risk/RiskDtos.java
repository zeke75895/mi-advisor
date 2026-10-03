package com.coursecompass.risk;

import com.coursecompass.ml.MlDtos.PathStep;
import com.coursecompass.ml.MlDtos.TopFeature;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class RiskDtos {

    private RiskDtos() {}

    /**
     * Study-habit signals the model needs. avgPracticeQuizScore and avgSleepHours may be blank (the ML
     * service imputes them). midtermScore may be blank if the course has a graded exam named "Midterm".
     */
    public record PredictRequest(
            @NotNull @DecimalMin("0") @DecimalMax("1") Double attendanceRate,
            @NotNull @Min(0) Integer missedDeadlines,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double onTimeSubmissionRate,
            @DecimalMin("0") @DecimalMax("100") Double avgPracticeQuizScore,
            @DecimalMin("0") @DecimalMax("100") Double midtermScore,
            @NotNull @DecimalMin("0") Double avgWeeklyStudyHours,
            @NotNull @Min(0) Integer flashcardsReviewed,
            @NotNull @DecimalMin("0") Double avgDaysStartedBeforeExam,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double lateNightStudyPct,
            @DecimalMin("0") @DecimalMax("24") Double avgSleepHours,
            @NotNull @Min(0) Integer studySessionsLogged) {}

    public record PredictResponse(
            Long riskScoreId,
            Long courseId,
            boolean atRisk,
            double riskProbability,
            List<TopFeature> topFeatures,
            List<PathStep> decisionPath,
            String explanation,
            List<String> imputedFeatures,
            String midtermSource,
            String disclaimer,
            String modelVersion,
            Instant computedAt) {}
}
