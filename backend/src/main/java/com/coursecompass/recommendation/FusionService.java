package com.coursecompass.recommendation;

import static com.coursecompass.recommendation.RecommendationType.LEAN_STAY;
import static com.coursecompass.recommendation.RecommendationType.LEAN_WITHDRAW;
import static com.coursecompass.recommendation.RecommendationType.STRONG_STAY;
import static com.coursecompass.recommendation.RecommendationType.STRONG_WITHDRAW;
import static com.coursecompass.recommendation.RecommendationType.UNCERTAIN;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Combines the model's risk with the grade projection and self-ratings into a stay/withdraw
 * recommendation. Port of fusion_logic.py (except that self-rating distress needs at least 3 rated
 * items), with the two CourseCompass decision rules applied on top:
 *
 * <ul>
 *   <li>projected final >= 70 OR more than 50% of the grade remains: never worse than lean_stay
 *   <li>projected final < 70 AND model predicts at_risk AND 50% or less remains: at least lean_withdraw
 * </ul>
 */
@Service
public class FusionService {

    static final double PASSING_GRADE = 70.0;
    static final double LOW_RECOVERY_ROOM = 0.30;
    static final double STAY_RULE_REMAINING = 0.50;
    static final int LOW_RATING = 4;
    // One or two ratings are too few to call a pattern, so distress only counts from 3 rated items up
    static final int MIN_RATINGS_FOR_DISTRESS = 3;

    // Model gets the highest weight (it's the rubric)
    static final double W_MODEL = 0.40;
    static final double W_GRADE = 0.25;
    static final double W_RECOVERY = 0.20;
    static final double W_DISTRESS = 0.15;

    public record Inputs(
            double modelRisk,
            boolean modelAtRisk,
            double projectedFinal,
            double remainingWeight,
            Collection<Integer> selfRatings) {}

    public record Result(
            RecommendationType recommendation,
            double confidence,
            double withdrawScore,
            String headline,
            List<String> reasoning,
            List<String> actions,
            double distressRatio,
            int lowRatings,
            String appliedRule) {}

    public Result compute(Inputs in) {
        double gradeSignal = in.projectedFinal() < PASSING_GRADE ? 1.0 : 0.0;
        double recoverySignal = in.remainingWeight() < LOW_RECOVERY_ROOM ? 1.0 : 0.0;
        int lowRatings = (int) in.selfRatings().stream().filter(r -> r <= LOW_RATING).count();
        double distressRatio = in.selfRatings().isEmpty() ? 0.0 : (double) lowRatings / in.selfRatings().size();
        boolean distressed = in.selfRatings().size() >= MIN_RATINGS_FOR_DISTRESS && distressRatio > 0.5;
        double distressSignal = distressed ? 1.0 : 0.0;

        double score = W_MODEL * in.modelRisk()
                + W_GRADE * gradeSignal
                + W_RECOVERY * recoverySignal
                + W_DISTRESS * distressSignal;

        RecommendationType rec;
        if (score >= 0.75) {
            rec = STRONG_WITHDRAW;
        } else if (score >= 0.55) {
            rec = LEAN_WITHDRAW;
        } else if (score <= 0.25) {
            rec = STRONG_STAY;
        } else if (score <= 0.45) {
            rec = LEAN_STAY;
        } else {
            rec = UNCERTAIN;
        }

        String appliedRule = null;
        boolean stayRule = in.projectedFinal() >= PASSING_GRADE || in.remainingWeight() > STAY_RULE_REMAINING;
        boolean withdrawRule = in.projectedFinal() < PASSING_GRADE
                && in.modelAtRisk()
                && in.remainingWeight() <= STAY_RULE_REMAINING;
        if (stayRule && (rec == UNCERTAIN || rec.isWithdraw())) {
            rec = LEAN_STAY;
            appliedRule = "stay";
        } else if (withdrawRule && !rec.isWithdraw()) {
            rec = LEAN_WITHDRAW;
            appliedRule = "withdraw";
        }

        double confidence = switch (rec) {
            case STRONG_WITHDRAW, LEAN_WITHDRAW -> score;
            case STRONG_STAY, LEAN_STAY -> 1 - score;
            case UNCERTAIN -> 0.5;
        };

        List<String> reasoning = reasoning(in, distressed, lowRatings, appliedRule);
        return new Result(
                rec,
                round(confidence, 2),
                round(score, 2),
                headline(rec),
                reasoning,
                actions(rec, in.remainingWeight()),
                round(distressRatio, 2),
                lowRatings,
                appliedRule);
    }

    private static List<String> reasoning(Inputs in, boolean distressed, int lowRatings, String appliedRule) {
        List<String> reasons = new ArrayList<>();
        // The tree's probability isn't calibrated, so describe it as a level rather than a percentage
        String level = riskLevel(in.modelRisk());
        reasons.add("high".equals(level)
                ? "The prediction model rates this course as high risk based on your study patterns."
                : "The prediction model rates this course as " + level + " risk.");

        long projected = Math.round(in.projectedFinal());
        if (in.projectedFinal() < PASSING_GRADE) {
            reasons.add("Your projected final grade is " + projected + ", below the 70 needed for a C.");
        } else {
            reasons.add("Your projected final grade is " + projected + ", which is passing.");
        }

        long remainingPct = Math.round(in.remainingWeight() * 100);
        if (in.remainingWeight() < LOW_RECOVERY_ROOM) {
            reasons.add("Only " + remainingPct + "% of your grade is still ahead, so there's limited room to recover.");
        } else {
            reasons.add(remainingPct + "% of your grade is still ahead, so recovery is possible.");
        }

        if (distressed) {
            reasons.add("You rated " + lowRatings + (lowRatings == 1 ? " item" : " items")
                    + " as low-confidence. That signal matters too.");
        }

        if ("stay".equals(appliedRule)) {
            reasons.add(in.projectedFinal() >= PASSING_GRADE
                    ? "Because your projected grade is passing, staying with a plan is recommended."
                    : "More than half of your grade is still ahead, so staying with a plan is recommended.");
        } else if ("withdraw".equals(appliedRule)) {
            reasons.add("A projected grade below 70, a model flag, and half or less of the grade remaining"
                    + " together are a withdraw signal.");
        }
        return reasons;
    }

    /** "high", "moderate" or "low": the tree's probability isn't calibrated, so we only show a level. */
    public static String riskLevel(double modelRisk) {
        if (modelRisk > 0.7) {
            return "high";
        }
        return modelRisk > 0.4 ? "moderate" : "low";
    }

    private static String headline(RecommendationType rec) {
        return switch (rec) {
            case STRONG_WITHDRAW -> "Strong signal: consider withdrawing";
            case LEAN_WITHDRAW -> "Leaning toward withdraw, but it's close";
            case UNCERTAIN -> "Too close to call: gather more information";
            case LEAN_STAY -> "Stay, but tighten up";
            case STRONG_STAY -> "You're on track: keep going";
        };
    }

    private static List<String> actions(RecommendationType rec, double remainingWeight) {
        return switch (rec) {
            case STRONG_WITHDRAW -> List.of(
                    "Talk to your academic advisor this week",
                    "Check the withdrawal deadline and grade impact",
                    "If you stay: focus only on remaining high-weight items");
            case LEAN_WITHDRAW -> List.of(
                    "Meet with your professor to discuss recovery options",
                    "Calculate what you'd need on remaining work to pass",
                    "Talk to your advisor before the drop deadline");
            case UNCERTAIN -> List.of(
                    "Take a practice quiz to get an objective signal",
                    "Talk to your professor about where you stand",
                    "Rate your confidence on upcoming items");
            case LEAN_STAY -> List.of(
                    "Focus on the " + Math.round(remainingWeight * 100) + "% of your grade still ahead",
                    "Use the generated study plan for your weak topics",
                    "Log study sessions to build the habit");
            case STRONG_STAY -> List.of(
                    "Maintain your current study rhythm",
                    "Review flashcards for the next exam",
                    "Check in again after the next graded item");
        };
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}
