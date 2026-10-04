package com.miadvisor.risk;

import com.miadvisor.common.BadRequestException;
import com.miadvisor.course.Course;
import com.miadvisor.course.CourseService;
import com.miadvisor.item.GradeCategory;
import com.miadvisor.item.GradedItem;
import com.miadvisor.item.GradedItemRepository;
import com.miadvisor.ml.MlClient;
import com.miadvisor.ml.MlDtos.MlPredictRequest;
import com.miadvisor.ml.MlDtos.MlPredictResponse;
import com.miadvisor.risk.RiskDtos.CheckInResponse;
import com.miadvisor.risk.RiskDtos.PredictRequest;
import com.miadvisor.risk.RiskDtos.PredictResponse;
import com.miadvisor.risk.RiskDtos.RiskDetails;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
    private final CheckInRepository checkIns;
    private final ObjectMapper json;

    public RiskService(
            CourseService courseService,
            GradedItemRepository items,
            RiskScoreRepository riskScores,
            MlClient mlClient,
            CheckInRepository checkIns,
            ObjectMapper json) {
        this.courseService = courseService;
        this.items = items;
        this.riskScores = riskScores;
        this.mlClient = mlClient;
        this.checkIns = checkIns;
        this.json = json;
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

        RiskDetails details = new RiskDetails(
                ml.topFeatures(), ml.decisionPath(), ml.explanation(), ml.imputedFeatures(), midtermSource, ml.disclaimer());
        RiskScore saved = riskScores.save(new RiskScore(
                course, ml.riskProbability(), ml.atRisk() == 1, ml.modelVersion(), toJson(details)));
        checkIns.save(new CheckIn(course, request));
        return toResponse(saved, details);
    }

    /** Latest risk check with its explanation, for the risk card on any device. */
    @Transactional(readOnly = true)
    public Optional<PredictResponse> latestDetails(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);
        return latest(courseId).map(score -> toResponse(score, fromJson(score.getDetailsJson())));
    }

    @Transactional(readOnly = true)
    public Optional<CheckInResponse> latestCheckIn(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);
        return checkIns.findFirstByCourseIdOrderByCreatedAtDescIdDesc(courseId).map(CheckIn::toResponse);
    }

    private PredictResponse toResponse(RiskScore score, RiskDetails d) {
        return new PredictResponse(
                score.getId(),
                score.getCourse().getId(),
                score.isAtRisk(),
                score.getRiskProbability(),
                d.topFeatures(),
                d.decisionPath(),
                d.explanation(),
                d.imputedFeatures(),
                d.midtermSource(),
                d.disclaimer(),
                score.getModelVersion(),
                score.getComputedAt());
    }

    private String toJson(RiskDetails details) {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private RiskDetails fromJson(String raw) {
        if (raw == null) {
            return new RiskDetails(List.of(), List.of(), null, List.of(), null, null);
        }
        try {
            return json.readValue(raw, RiskDetails.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored risk details are unreadable", e);
        }
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
