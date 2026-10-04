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
 * recommendation. Port of fusion_logic.py, with these changes: self-rating distress needs at least 3
 * rated items, and "room to recover" is the average score needed on the remaining work to finish with a
 * C (above 85% = steep), instead of how much of the grade is left. The CourseCompass rules apply on top:
 *
 * <ul>
 *   <li>projected final >= 70 OR more than 50% of the grade remains (and a C is still possible): never
 *       worse than lean_stay
 *   <li>projected final < 70 AND (model predicts at_risk with 50% or less remaining, OR a C is no longer
 *       possible): at least lean_withdraw
 * </ul>
 *
 * The reasoning is written after the decision so every reason agrees with the headline.
 */
@Service
public class FusionService {

    static final double PASSING_GRADE = GradeProjector.PASSING_GRADE;
    /** Needing more than this average on the remaining work to reach a C counts as a steep climb. */
    static final double STEEP_REQUIRED_SCORE = 85.0;
    static final double STAY_RULE_REMAINING = 0.50;
    static final int LOW_RATING = 4;
    // One or two ratings are too few to call a pattern, so distress only counts from 3 rated items up
    static final int MIN_RATINGS_FOR_DISTRESS = 3;

    // Model gets the highest weight (it's the rubric)
    static final double W_MODEL = 0.40;
    static final double W_GRADE = 0.25;
    static final double W_RECOVERY = 0.20;
    static final double W_DISTRESS = 0.15;

    /**
     * @param currentGrade average on graded work so far, or null if nothing is graded
     * @param requiredScore average % needed on the remaining work to finish at 70 (null if nothing remains)
     * @param maxPossible best final grade still possible
     */
    public record Inputs(
            double modelRisk,
            boolean modelAtRisk,
            double projectedFinal,
            double remainingWeight,
            Double currentGrade,
            Double requiredScore,
            double maxPossible,
            Collection<Integer> selfRatings) {

        boolean nothingRemains() {
            return requiredScore == null;
        }

        boolean passingSecured() {
            return requiredScore != null && requiredScore <= 0;
        }

        boolean passingImpossible() {
            return requiredScore != null && requiredScore > 100;
        }

        boolean steepClimb() {
            return requiredScore != null && requiredScore > STEEP_REQUIRED_SCORE;
        }
    }

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
        // Little room to recover: a steep (or impossible) climb to a C, or a final grade already below it
        boolean littleRoom = in.steepClimb() || (in.nothingRemains() && in.projectedFinal() < PASSING_GRADE);
        double recoverySignal = littleRoom ? 1.0 : 0.0;
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
        boolean stayRule = !in.passingImpossible()
                && (in.projectedFinal() >= PASSING_GRADE || in.remainingWeight() > STAY_RULE_REMAINING);
        boolean withdrawRule = in.projectedFinal() < PASSING_GRADE
                && ((in.modelAtRisk() && in.remainingWeight() <= STAY_RULE_REMAINING) || in.passingImpossible());
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

        List<String> reasoning = reasoning(in, rec, distressed, lowRatings, appliedRule);
        return new Result(
                rec,
                round(confidence, 2),
                round(score, 2),
                headline(rec),
                reasoning,
                actions(rec, in),
                round(distressRatio, 2),
                lowRatings,
                appliedRule);
    }

    private static List<String> reasoning(
            Inputs in, RecommendationType rec, boolean distressed, int lowRatings, String appliedRule) {
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

        reasons.add(recoveryReason(in, rec));

        if (distressed) {
            reasons.add("You rated " + lowRatings + (lowRatings == 1 ? " item" : " items")
                    + " as low-confidence. That signal matters too.");
        }

        if ("stay".equals(appliedRule)) {
            reasons.add(in.projectedFinal() >= PASSING_GRADE
                    ? "Because your projected grade is passing, staying with a plan is recommended."
                    : "More than half of your grade is still ahead, so it's too early to give up on this course.");
        } else if ("withdraw".equals(appliedRule)) {
            reasons.add(in.passingImpossible()
                    ? "Because a C is no longer reachable, withdrawing is worth discussing with your advisor."
                    : "A projected grade below 70, a model flag, and half or less of the grade remaining"
                            + " together are a withdraw signal.");
        }
        return reasons;
    }

    /**
     * "You'd need X% on what's left" in words that fit the final recommendation, so a withdraw result never
     * says recovery is easy and a stay result never says it's hopeless.
     */
    static String recoveryReason(Inputs in, RecommendationType rec) {
        long remainingPct = Math.round(in.remainingWeight() * 100);
        if (in.nothingRemains()) {
            return in.currentGrade() == null
                    ? "None of your grade is still ahead."
                    : "All of your graded work is in, so your final grade is about " + Math.round(in.currentGrade()) + ".";
        }
        if (in.passingSecured()) {
            return "You've already earned enough points for a C, even if the remaining " + remainingPct + "% goes badly.";
        }
        if (in.passingImpossible()) {
            return "Even a perfect score on the remaining " + remainingPct + "% of your grade would leave you at "
                    + (long) Math.floor(in.maxPossible()) + ", below a C.";
        }
        long needed = (long) Math.ceil(in.requiredScore());
        String need = "To finish with a C, you'd need an average of " + needed + "% on the remaining "
                + remainingPct + "% of your grade.";
        String soFar = in.currentGrade() == null ? "" : " from the " + Math.round(in.currentGrade()) + "% you've averaged so far";
        if (rec.isWithdraw()) {
            if (in.steepClimb()) {
                return need + " That's a steep climb" + soFar + ".";
            }
            boolean stepUp = in.currentGrade() != null && in.currentGrade() < in.requiredScore();
            return need + (stepUp
                    ? " That's possible, but a big step up" + soFar + "."
                    : " That's possible, but your confidence ratings suggest it will be hard.");
        }
        if (rec == RecommendationType.UNCERTAIN) {
            return need + (in.steepClimb() ? " That's a steep climb." : " That's within reach, but it's close.");
        }
        return need + (in.steepClimb()
                ? " That's a steep climb, so staying only works with a serious plan."
                : " That's within reach.");
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

    private static List<String> actions(RecommendationType rec, Inputs in) {
        boolean hasTarget = in.requiredScore() != null && in.requiredScore() > 0 && in.requiredScore() <= 100;
        String target = hasTarget ? (long) Math.ceil(in.requiredScore()) + "%" : null;
        return switch (rec) {
            case STRONG_WITHDRAW -> List.of(
                    "Talk to your academic advisor this week",
                    "Check the withdrawal deadline and grade impact",
                    "If you stay: focus only on remaining high-weight items");
            case LEAN_WITHDRAW -> List.of(
                    "Meet with your professor to discuss recovery options",
                    hasTarget
                            ? "Ask your professor whether " + target + " on the rest of the course is realistic for you"
                            : "Ask your professor about any options to recover points",
                    "Talk to your advisor before the drop deadline");
            case UNCERTAIN -> List.of(
                    "Take a practice quiz to get an objective signal",
                    "Talk to your professor about where you stand",
                    "Rate your confidence on upcoming items");
            case LEAN_STAY -> List.of(
                    hasTarget
                            ? "Aim for at least " + target + " on your remaining work to finish with a C"
                            : "Focus on the " + Math.round(in.remainingWeight() * 100) + "% of your grade still ahead",
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
