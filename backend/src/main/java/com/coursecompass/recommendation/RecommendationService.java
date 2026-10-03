package com.coursecompass.recommendation;

import com.coursecompass.common.ConflictException;
import com.coursecompass.course.CourseService;
import com.coursecompass.item.GradedItem;
import com.coursecompass.item.GradedItemRepository;
import com.coursecompass.llm.ExplanationService;
import com.coursecompass.llm.ExplanationService.Explanation;
import com.coursecompass.llm.ExplanationService.ExplanationInput;
import com.coursecompass.rating.SelfRatingService;
import com.coursecompass.recommendation.GradeProjector.ItemInput;
import com.coursecompass.recommendation.GradeProjector.Projection;
import com.coursecompass.recommendation.RecommendationDtos.ProjectionResponse;
import com.coursecompass.recommendation.RecommendationDtos.RecommendationResponse;
import com.coursecompass.recommendation.RecommendationDtos.Signals;
import com.coursecompass.risk.RiskScore;
import com.coursecompass.risk.RiskService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    static final String DISCLAIMER = "This is guidance, not a verdict. It combines a model trained on synthetic"
            + " data with your own ratings, and it can be wrong.";
    static final String ADVISOR_NOTE = "Talk to your advisor before withdrawing.";

    private final CourseService courseService;
    private final GradedItemRepository items;
    private final SelfRatingService ratingService;
    private final RiskService riskService;
    private final FusionService fusion;
    private final ExplanationService explanationService;

    public RecommendationService(
            CourseService courseService,
            GradedItemRepository items,
            SelfRatingService ratingService,
            RiskService riskService,
            FusionService fusion,
            ExplanationService explanationService) {
        this.courseService = courseService;
        this.items = items;
        this.ratingService = ratingService;
        this.riskService = riskService;
        this.fusion = fusion;
        this.explanationService = explanationService;
    }

    public ProjectionResponse projection(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);
        List<GradedItem> courseItems = items.findByCourseIdOrderByDueDateAscIdAsc(courseId);
        Map<Long, Integer> ratings = ratingService.latestRatingsForCourse(courseId);
        Projection p = project(courseItems, ratings);
        return new ProjectionResponse(
                courseId,
                p.projectedFinal() == null ? null : Math.round(p.projectedFinal() * 10) / 10.0,
                p.currentGrade() == null ? null : Math.round(p.currentGrade() * 10) / 10.0,
                round(p.remainingWeight()),
                round(p.coveredWeight()),
                courseItems.size(),
                ratings.size());
    }

    // Not @Transactional: each read runs in its own short transaction so the Gemini call below
    // doesn't hold a database connection open.
    public RecommendationResponse recommend(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);

        RiskScore risk = riskService.latest(courseId).orElseThrow(() -> new ConflictException(
                "No risk prediction yet. Call POST /api/courses/" + courseId + "/predict first."));

        List<GradedItem> courseItems = items.findByCourseIdOrderByDueDateAscIdAsc(courseId);
        if (courseItems.isEmpty()) {
            throw new ConflictException("Add the course's graded items from the syllabus first.");
        }
        Map<Long, Integer> ratings = ratingService.latestRatingsForCourse(courseId);
        Projection projection = project(courseItems, ratings);
        if (projection.projectedFinal() == null) {
            throw new ConflictException("Grade or self-rate at least one item to project a final grade.");
        }

        FusionService.Result result = fusion.compute(new FusionService.Inputs(
                risk.getRiskProbability(),
                risk.isAtRisk(),
                projection.projectedFinal(),
                projection.remainingWeight(),
                ratings.values()));

        Explanation explanation = explanationService.explain(new ExplanationInput(
                result.recommendation().json(),
                result.headline(),
                FusionService.riskLevel(risk.getRiskProbability()),
                Math.round(projection.projectedFinal()),
                projection.currentGrade() == null ? null : Math.round(projection.currentGrade()),
                Math.round(projection.remainingWeight() * 100),
                result.lowRatings(),
                ratings.size(),
                result.reasoning(),
                result.actions()));

        return new RecommendationResponse(
                courseId,
                result.recommendation(),
                result.confidence(),
                result.withdrawScore(),
                result.headline(),
                result.reasoning(),
                result.actions(),
                explanation,
                new Signals(
                        round(risk.getRiskProbability()),
                        risk.isAtRisk(),
                        Math.round(projection.projectedFinal() * 10) / 10.0,
                        projection.currentGrade() == null ? null : Math.round(projection.currentGrade() * 10) / 10.0,
                        round(projection.remainingWeight()),
                        round(projection.coveredWeight()),
                        result.distressRatio(),
                        result.lowRatings(),
                        ratings.size()),
                result.appliedRule(),
                result.recommendation().isWithdraw() ? ADVISOR_NOTE : null,
                DISCLAIMER,
                risk.getModelVersion(),
                risk.getComputedAt());
    }

    private static Projection project(List<GradedItem> courseItems, Map<Long, Integer> ratings) {
        return GradeProjector.project(courseItems.stream()
                .map(i -> new ItemInput(i.getWeight(), i.isGraded(), i.percentScore(), ratings.get(i.getId())))
                .toList());
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
