package com.miadvisor.recommendation;

import com.miadvisor.course.CourseService;
import com.miadvisor.item.GradedItem;
import com.miadvisor.item.GradedItemRepository;
import com.miadvisor.llm.ExplanationService;
import com.miadvisor.llm.ExplanationService.Explanation;
import com.miadvisor.llm.ExplanationService.ExplanationInput;
import com.miadvisor.rating.SelfRatingService;
import com.miadvisor.recommendation.GradeProjector.ItemInput;
import com.miadvisor.recommendation.GradeProjector.Projection;
import com.miadvisor.recommendation.RecommendationDtos.ProjectionResponse;
import com.miadvisor.recommendation.RecommendationDtos.RecommendationResponse;
import com.miadvisor.recommendation.RecommendationDtos.Signals;
import com.miadvisor.risk.RiskScore;
import com.miadvisor.risk.RiskService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                oneDecimal(p.requiredScore()),
                Math.round(p.maxPossible() * 10) / 10.0,
                courseItems.size(),
                ratings.size());
    }

    /**
     * Empty until the course has a risk prediction, graded items, and at least one graded or rated item:
     * that's a normal "not ready yet" state for a new course, not an error.
     *
     * <p>Not @Transactional: each read runs in its own short transaction so the Gemini call below
     * doesn't hold a database connection open.
     */
    public Optional<RecommendationResponse> recommend(Long userId, Long courseId) {
        courseService.getOwned(userId, courseId);

        Optional<RiskScore> latestRisk = riskService.latest(courseId);
        List<GradedItem> courseItems = items.findByCourseIdOrderByDueDateAscIdAsc(courseId);
        if (latestRisk.isEmpty() || courseItems.isEmpty()) {
            return Optional.empty();
        }
        RiskScore risk = latestRisk.get();
        Map<Long, Integer> ratings = ratingService.latestRatingsForCourse(courseId);
        Projection projection = project(courseItems, ratings);
        if (projection.projectedFinal() == null) {
            return Optional.empty();
        }

        FusionService.Result result = fusion.compute(new FusionService.Inputs(
                risk.getRiskProbability(),
                risk.isAtRisk(),
                projection.projectedFinal(),
                projection.remainingWeight(),
                projection.currentGrade(),
                projection.requiredScore(),
                projection.maxPossible(),
                ratings.values()));

        Explanation explanation = explanationService.explain(new ExplanationInput(
                result.recommendation().json(),
                result.headline(),
                FusionService.riskLevel(risk.getRiskProbability()),
                Math.round(projection.projectedFinal()),
                projection.currentGrade() == null ? null : Math.round(projection.currentGrade()),
                Math.round(projection.remainingWeight() * 100),
                requiredText(projection),
                result.lowRatings(),
                ratings.size(),
                result.reasoning(),
                result.actions()));

        return Optional.of(new RecommendationResponse(
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
                        oneDecimal(projection.requiredScore()),
                        Math.round(projection.maxPossible() * 10) / 10.0,
                        result.distressRatio(),
                        result.lowRatings(),
                        ratings.size()),
                result.appliedRule(),
                result.recommendation().isWithdraw() ? ADVISOR_NOTE : null,
                DISCLAIMER,
                risk.getModelVersion(),
                risk.getComputedAt()));
    }

    private static Projection project(List<GradedItem> courseItems, Map<Long, Integer> ratings) {
        return GradeProjector.project(courseItems.stream()
                .map(i -> new ItemInput(i.getWeight(), i.isGraded(), i.percentScore(), ratings.get(i.getId())))
                .toList());
    }

    /** The "score needed for a C" in the words the Gemini prompt uses. */
    static String requiredText(Projection p) {
        if (p.requiredScore() == null) {
            return "nothing is left to grade";
        }
        if (p.requiredScore() <= 0) {
            return "a C is already secured";
        }
        if (p.requiredScore() > 100) {
            return "a C is no longer reachable (best possible final grade: " + (long) Math.floor(p.maxPossible()) + ")";
        }
        return (long) Math.ceil(p.requiredScore()) + "% average on the remaining work";
    }

    private static Double oneDecimal(Double value) {
        return value == null ? null : Math.round(value * 10) / 10.0;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
