package com.coursecompass.recommendation;

import com.coursecompass.item.GradeCategory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Projects a final grade from syllabus weights, actual scores and self-ratings.
 *
 * <p>Per item: graded items use their actual percentage; ungraded items use self_rating / 10 * 100.
 * Per category: score = mean of its item scores, weight = sum of its item weights. Projected final =
 * sum(category_weight * category_score) / sum(category_weight), over categories with at least one score.
 */
public final class GradeProjector {

    private GradeProjector() {}

    public record ItemInput(GradeCategory category, double weight, boolean graded, Double percentScore, Integer rating) {

        Double projectedScore() {
            if (graded) {
                return percentScore;
            }
            return rating == null ? null : rating / 10.0 * 100.0;
        }
    }

    /**
     * @param projectedFinal 0-100, or null if no item is graded or rated yet
     * @param currentGrade weighted average of graded items only, or null if nothing is graded
     * @param remainingWeight 0-1 share of the course weight not graded yet
     * @param coveredWeight 0-1 share of the course weight in categories that have a score
     */
    public record Projection(
            Double projectedFinal, Double currentGrade, double remainingWeight, double coveredWeight) {}

    public static Projection project(List<ItemInput> items) {
        double totalWeight = items.stream().mapToDouble(ItemInput::weight).sum();
        if (totalWeight <= 0) {
            return new Projection(null, null, 1.0, 0.0);
        }

        double ungradedWeight = 0;
        double gradedWeight = 0;
        double gradedPoints = 0;
        Map<GradeCategory, Double> categoryWeight = new EnumMap<>(GradeCategory.class);
        Map<GradeCategory, List<Double>> categoryScores = new EnumMap<>(GradeCategory.class);

        for (ItemInput item : items) {
            categoryWeight.merge(item.category(), item.weight(), Double::sum);
            Double score = item.projectedScore();
            if (score != null) {
                categoryScores.computeIfAbsent(item.category(), c -> new ArrayList<>()).add(score);
            }
            if (item.graded() && item.percentScore() != null) {
                gradedWeight += item.weight();
                gradedPoints += item.weight() * item.percentScore();
            } else {
                ungradedWeight += item.weight();
            }
        }

        double weightedSum = 0;
        double scoredWeight = 0;
        for (Map.Entry<GradeCategory, List<Double>> entry : categoryScores.entrySet()) {
            double mean = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double weight = categoryWeight.get(entry.getKey());
            weightedSum += weight * mean;
            scoredWeight += weight;
        }

        Double projectedFinal = scoredWeight > 0 ? weightedSum / scoredWeight : null;
        Double currentGrade = gradedWeight > 0 ? gradedPoints / gradedWeight : null;
        return new Projection(projectedFinal, currentGrade, ungradedWeight / totalWeight, scoredWeight / totalWeight);
    }
}
