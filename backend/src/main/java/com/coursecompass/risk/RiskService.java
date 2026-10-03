package com.coursecompass.risk;

import com.coursecompass.common.BadRequestException;
import com.coursecompass.course.Course;
import com.coursecompass.course.CourseService;
import com.coursecompass.item.GradeCategory;
import com.coursecompass.item.GradedItem;
import com.coursecompass.item.GradedItemRepository;
import com.coursecompass.ml.MlClient;
import com.coursecompass.ml.MlDtos.MlPredictRequest;
import com.coursecompass.ml.MlDtos.MlPredictResponse;
import com.coursecompass.risk.RiskDtos.PredictRequest;
import com.coursecompass.risk.RiskDtos.PredictResponse;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskService {

    private final CourseService courseService;
    private final GradedItemRepository items;
    private final RiskScoreRepository riskScores;
    private final MlClient mlClient;

    public RiskService(
            CourseService courseService,
            GradedItemRepository items,
            RiskScoreRepository riskScores,
            MlClient mlClient) {
        this.courseService = courseService;
        this.items = items;
        this.riskScores = riskScores;
        this.mlClient = mlClient;
    }

    @Transactional
    public PredictResponse predict(Long userId, Long courseId, PredictRequest request) {
        Course course = courseService.getOwned(userId, courseId);

        Double midterm = request.midtermScore();
        String midtermSource = "request";
        if (midterm == null) {
            midterm = gradedMidterm(courseId).orElseThrow(() -> new BadRequestException(
                    "midtermScore is required: send it, or add a graded EXAM item named 'Midterm' to this course"));
            midtermSource = "graded_item";
        }

        MlPredictResponse ml = mlClient.predict(new MlPredictRequest(
                request.attendanceRate(),
                request.missedDeadlines(),
                request.onTimeSubmissionRate(),
                request.avgPracticeQuizScore(),
                midterm,
                request.avgWeeklyStudyHours(),
                request.flashcardsReviewed(),
                request.avgDaysStartedBeforeExam(),
                request.lateNightStudyPct(),
                request.avgSleepHours(),
                request.studySessionsLogged()));

        RiskScore saved = riskScores.save(
                new RiskScore(course, ml.riskProbability(), ml.atRisk() == 1, ml.modelVersion()));

        return new PredictResponse(
                saved.getId(),
                courseId,
                saved.isAtRisk(),
                saved.getRiskProbability(),
                ml.topFeatures(),
                ml.decisionPath(),
                ml.explanation(),
                ml.imputedFeatures(),
                midtermSource,
                ml.disclaimer(),
                saved.getModelVersion(),
                saved.getComputedAt());
    }

    @Transactional(readOnly = true)
    public Optional<RiskScore> latest(Long courseId) {
        return riskScores.findFirstByCourseIdOrderByComputedAtDescIdDesc(courseId);
    }

    private Optional<Double> gradedMidterm(Long courseId) {
        return items.findByCourseIdOrderByDueDateAscIdAsc(courseId).stream()
                .filter(i -> i.getCategory() == GradeCategory.EXAM)
                .filter(i -> i.getName().toLowerCase(Locale.ROOT).contains("midterm"))
                .map(GradedItem::percentScore)
                .filter(score -> score != null)
                .findFirst()
                .map(score -> Math.min(score, 100.0));
    }
}
