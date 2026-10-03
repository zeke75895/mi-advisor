package com.coursecompass.recommendation;

import com.coursecompass.common.ConflictException;
import com.coursecompass.course.CourseService;
import com.coursecompass.item.GradedItem;
import com.coursecompass.item.GradedItemRepository;
import com.coursecompass.rating.SelfRatingService;
import com.coursecompass.recommendation.GradeProjector.ItemInput;
import com.coursecompass.recommendation.GradeProjector.Projection;
import com.coursecompass.recommendation.RecommendationDtos.RecommendationResponse;
import com.coursecompass.recommendation.RecommendationDtos.Signals;
import com.coursecompass.risk.RiskScore;
import com.coursecompass.risk.RiskService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    static final String DISCLAIMER = "This is guidance, not a verdict. It combines a model trained on synthetic"
            + " data with your own ratings, and it can be wrong.";
    static final String ADVISOR_NOTE = "Talk to your advisor before withdrawing.";

    private final CourseService courseService;
    private final GradedItemRepository items;
    private final SelfRatingService ratingService;
    private final RiskService riskService;

    public RecommendationService(
            CourseService courseService,
            GradedItemRepository items,
            SelfRatingService ratingService,
            RiskService riskService) {
        this.courseService = courseService;
        this.items = items;
        this.ratingService = ratingService;
        this.riskService = riskService;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);

        RiskScore risk = riskService.latest(courseId).orElseThrow(() -> new ConflictException(
                "No risk prediction yet. Call POST /api/courses/" + courseId + "/predict first."));

        List<GradedItem> courseItems = items.findByCourseIdOrderByDueDateAscIdAsc(courseId);
        if (courseItems.isEmpty()) {
            throw new ConflictException("Add the course's graded items from the syllabus first.");
        }
        Map<Long, Integer> ratings = ratingService.latestRatingsForCourse(courseId);
        Projection projection = GradeProjector.project(courseItems.stream()
                .map(i -> new ItemInput(
                        i.getCategory(), i.getWeight(), i.isGraded(), i.percentScore(), ratings.get(i.getId())))
                .toList());
        if (projection.projectedFinal() == null) {
            throw new ConflictException("Grade or self-rate at least one item to project a final grade.");
        }

        FusionEngine.Result result = FusionEngine.compute(new FusionEngine.Inputs(
                risk.getRiskProbability(),
                risk.isAtRisk(),
                projection.projectedFinal(),
                projection.remainingWeight(),
                ratings.values()));

        return new RecommendationResponse(
                courseId,
                result.recommendation(),
                result.confidence(),
                result.withdrawScore(),
                result.headline(),
                result.reasoning(),
                result.actions(),
                new Signals(
                        round(risk.getRiskProbability()),
                        risk.isAtRisk(),
                        Math.round(projection.projectedFinal() * 10) / 10.0,
                        projection.currentGrade() == null ? null : Math.round(projection.currentGrade() * 10) / 10.0,
                        round(projection.remainingWeight()),
                        result.distressRatio(),
                        result.lowRatings(),
                        ratings.size()),
                result.appliedRule(),
                result.recommendation().isWithdraw() ? ADVISOR_NOTE : null,
                DISCLAIMER,
                risk.getModelVersion(),
                risk.getComputedAt());
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
