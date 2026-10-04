package com.coursecompass.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.coursecompass.recommendation.FusionService.Inputs;
import com.coursecompass.recommendation.FusionService.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class FusionServiceTest {

    private final FusionService fusion = new FusionService();

    /** Inputs where the needed score and best-possible grade follow from what's been earned so far. */
    private static Inputs inputs(double risk, boolean atRisk, double projected, double remaining,
            double earnedPoints, List<Integer> ratings) {
        Double required = remaining > 0 ? (70 - earnedPoints) / remaining : null;
        double graded = 1 - remaining;
        Double current = graded > 0 ? earnedPoints / graded : null;
        return new Inputs(risk, atRisk, projected, remaining, current, required, earnedPoints + remaining * 100, ratings);
    }

    @Test
    void theOldContradictionNowReadsAsOneStory() {
        // Projected 51, 45% left, high risk, 3 low ratings. 55% graded at a 50% average = 27.5 points,
        // so a C needs (70 - 27.5) / 0.45 = 94.4% on what's left.
        Result r = fusion.compute(inputs(0.91, true, 51, 0.45, 27.5, List.of(2, 3, 4)));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.STRONG_WITHDRAW);
        assertThat(r.reasoning())
                .contains("To finish with a C, you'd need an average of 95% on the remaining 45% of your grade."
                        + " That's a steep climb from the 50% you've averaged so far.")
                .noneMatch(s -> s.contains("recovery is possible"));
    }

    @Test
    void allSignalsBadIsStrongWithdraw() {
        // 80% graded for 52 points: a C needs 90% on the last 20% (steep)
        Result r = fusion.compute(inputs(0.9, true, 60, 0.2, 52, List.of(2, 3, 8)));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.STRONG_WITHDRAW);
        assertThat(r.withdrawScore()).isEqualTo(0.96);
        assertThat(r.actions()).anyMatch(a -> a.contains("advisor"));
    }

    @Test
    void reachableTargetWithMostOfTheGradeAheadCapsAtLeanStayAndSaysWithinReach() {
        // 40% graded for 30 points: a C needs 66.7% on the remaining 60%
        Result r = fusion.compute(inputs(0.9, true, 60, 0.6, 30, List.of()));

        assertThat(r.withdrawScore()).isEqualTo(0.61);
        assertThat(r.recommendation()).isEqualTo(RecommendationType.LEAN_STAY);
        assertThat(r.appliedRule()).isEqualTo("stay");
        assertThat(r.reasoning()).anyMatch(s -> s.contains("need an average of 67%") && s.endsWith("That's within reach."));
        assertThat(r.actions()).first().isEqualTo("Aim for at least 67% on your remaining work to finish with a C");
    }

    @Test
    void steepClimbWithMostOfTheGradeAheadStillStaysButSaysSo() {
        // 45% graded for 20 points: a C needs 90.9% on the remaining 55%
        Result r = fusion.compute(inputs(0.9, true, 55, 0.55, 20, List.of()));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.LEAN_STAY);
        assertThat(r.reasoning()).anyMatch(s -> s.contains("91%") && s.contains("staying only works with a serious plan"));
    }

    @Test
    void reachableButBelowPassingWithModelFlagIsLeanWithdrawWithHonestWording() {
        // 65% graded for 41.75 points: a C needs 80.7% on the last 35% (not steep), current average 64%
        Result r = fusion.compute(inputs(0.9078, true, 52, 0.35, 41.75, List.of(3, 4)));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.LEAN_WITHDRAW);
        assertThat(r.withdrawScore()).isEqualTo(0.61);
        assertThat(r.reasoning()).anyMatch(s -> s.contains("81%") && s.contains("possible, but a big step up from the 64%"));
        assertThat(r.actions()).anyMatch(a -> a.contains("whether 81% on the rest of the course is realistic"));
    }

    @Test
    void aCThatIsNoLongerReachableIsAtLeastLeanWithdrawAndNeverStay() {
        // 60% graded for 28 points: even 100% on the last 40% only reaches 68.
        // Low model risk: 0.02 + 0.25 + 0.20 = 0.47 would be "uncertain"; the impossible rule raises it.
        Result r = fusion.compute(inputs(0.05, false, 60, 0.4, 28, List.of()));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.LEAN_WITHDRAW);
        assertThat(r.appliedRule()).isEqualTo("withdraw");
        assertThat(r.reasoning())
                .contains("Even a perfect score on the remaining 40% of your grade would leave you at 68, below a C.")
                .anyMatch(s -> s.contains("no longer reachable"));

        // With 60% of the grade left the stay rule would normally apply, but not when a C is impossible
        Result early = fusion.compute(inputs(0.05, false, 60, 0.6, 8, List.of()));
        assertThat(early.recommendation().isWithdraw()).isTrue();
    }

    @Test
    void securedCAndLowRiskIsStrongStay() {
        Result r = fusion.compute(inputs(0.06, false, 85, 0.5, 75, List.of(8, 9)));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.STRONG_STAY);
        assertThat(r.reasoning()).anyMatch(s -> s.contains("already earned enough points for a C"));
        assertThat(r.reasoning()).anyMatch(s -> s.contains("low risk"));
    }

    @Test
    void finishedCourseBelowPassingCountsAsNoRoomToRecover() {
        Result r = fusion.compute(inputs(0.9, true, 62, 0.0, 62, List.of()));

        assertThat(r.withdrawScore()).isEqualTo(0.81); // 0.36 + 0.25 + 0.20
        assertThat(r.reasoning()).contains("All of your graded work is in, so your final grade is about 62.");
    }

    @Test
    void fewerThanThreeRatingsNeverCountAsDistress() {
        Result one = fusion.compute(inputs(0.9, true, 58, 0.35, 41.75, List.of(4)));
        Result two = fusion.compute(inputs(0.9, true, 58, 0.35, 41.75, List.of(2, 3)));

        assertThat(one.distressRatio()).isEqualTo(1.0);
        assertThat(one.withdrawScore()).isEqualTo(0.61);
        assertThat(one.reasoning()).noneMatch(s -> s.contains("low-confidence"));
        assertThat(two.withdrawScore()).isEqualTo(0.61);
    }

    @Test
    void threeMostlyLowRatingsCountAsDistress() {
        Result r = fusion.compute(inputs(0.9, true, 58, 0.35, 41.75, List.of(2, 3, 9)));

        assertThat(r.withdrawScore()).isEqualTo(0.76);
        assertThat(r.recommendation()).isEqualTo(RecommendationType.STRONG_WITHDRAW);
        assertThat(r.reasoning()).anyMatch(s -> s.contains("2 items as low-confidence"));
    }

    @Test
    void lowRatingsCountAsDistressOnlyAboveHalf() {
        Result half = fusion.compute(inputs(0.1, false, 80, 0.5, 40, List.of(2, 9, 3, 8)));
        Result most = fusion.compute(inputs(0.1, false, 80, 0.5, 40, List.of(2, 3, 9)));

        assertThat(half.distressRatio()).isEqualTo(0.5);
        assertThat(half.withdrawScore()).isEqualTo(0.04);
        assertThat(most.withdrawScore()).isEqualTo(0.19);
        assertThat(most.lowRatings()).isEqualTo(2);
    }

    @Test
    void reasoningNeverContradictsTheHeadlineAcrossTheBoard() {
        // Sweep remaining share and earned points: withdraw results never call the climb easy,
        // and stay results never say a C is out of reach.
        for (double remaining = 0.05; remaining <= 0.95; remaining += 0.05) {
            for (double earned = 0; earned <= (1 - remaining) * 100; earned += 5) {
                for (double risk : new double[] {0.1, 0.55, 0.9}) {
                    Result r = fusion.compute(inputs(risk, risk > 0.5, 55, remaining, earned, List.of(3, 4, 5)));
                    String recovery = r.reasoning().get(2);
                    if (r.recommendation().isWithdraw()) {
                        assertThat(recovery).doesNotContain("within reach");
                    } else {
                        assertThat(recovery).doesNotContain("below a C");
                    }
                }
            }
        }
    }
}
