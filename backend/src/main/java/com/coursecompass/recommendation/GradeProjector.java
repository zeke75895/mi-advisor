package com.coursecompass.recommendation;

import java.util.List;

/**
 * Projects a final grade from syllabus weights, actual scores and self-ratings.
 *
 * <p>Each item's score is its actual percentage if graded, otherwise self_rating * 10. Projected final =
 * sum(weight * score) / sum(weight) over items that have a score, so a 30% project counts thirty times
 * as much as a 1% homework. Ungraded items with no rating are left out and reported via coveredWeight.
 */
public final class GradeProjector {

    private GradeProjector() {}

    public record ItemInput(double weight, boolean graded, Double percentScore, Integer rating) {

        Double projectedScore() {
            if (graded) {
                return percentScore;
            }
            return rating == null ? null : rating * 10.0;
        }
    }

    /**
     * @param projectedFinal 0-100, or null if no item is graded or rated yet
     * @param currentGrade weighted average of graded items only, or null if nothing is graded
     * @param remainingWeight 0-1 share of the course weight not graded yet
     * @param coveredWeight 0-1 share of the course weight that has a score (actual or self-rated)
     */
    public record Projection(
            Double projectedFinal, Double currentGrade, double remainingWeight, double coveredWeight) {}

    public static Projection project(List<ItemInput> items) {
        double totalWeight = items.stream().mapToDouble(ItemInput::weight).sum();
        if (totalWeight <= 0) {
            return new Projection(null, null, 1.0, 0.0);
        }

        double scoredWeight = 0;
        double scoredPoints = 0;
        double gradedWeight = 0;
        double gradedPoints = 0;
        for (ItemInput item : items) {
            Double score = item.projectedScore();
            if (score != null) {
                scoredWeight += item.weight();
                scoredPoints += item.weight() * score;
            }
            if (item.graded() && item.percentScore() != null) {
                gradedWeight += item.weight();
                gradedPoints += item.weight() * item.percentScore();
            }
        }

        return new Projection(
                scoredWeight > 0 ? scoredPoints / scoredWeight : null,
                gradedWeight > 0 ? gradedPoints / gradedWeight : null,
                (totalWeight - gradedWeight) / totalWeight,
                scoredWeight / totalWeight);
    }
}
