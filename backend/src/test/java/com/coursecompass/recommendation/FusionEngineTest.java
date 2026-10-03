package com.coursecompass.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.coursecompass.recommendation.FusionEngine.Inputs;
import com.coursecompass.recommendation.FusionEngine.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class FusionEngineTest {

    @Test
    void allSignalsBadIsStrongWithdraw() {
        // 0.4*0.9 + 0.25 + 0.20 + 0.15 = 0.96
        Result r = FusionEngine.compute(new Inputs(0.9, true, 60, 0.2, List.of(2, 3, 8)));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.STRONG_WITHDRAW);
        assertThat(r.withdrawScore()).isEqualTo(0.96);
        assertThat(r.appliedRule()).isNull();
        assertThat(r.actions()).anyMatch(a -> a.contains("advisor"));
    }

    @Test
    void moreThanHalfRemainingCapsAtLeanStay() {
        // Score 0.61 would be lean_withdraw, but 60% of the grade is still ahead
        Result r = FusionEngine.compute(new Inputs(0.9, true, 60, 0.6, List.of()));

        assertThat(r.withdrawScore()).isEqualTo(0.61);
        assertThat(r.recommendation()).isEqualTo(RecommendationType.LEAN_STAY);
        assertThat(r.appliedRule()).isEqualTo("stay");
        assertThat(r.confidence()).isEqualTo(0.39);
    }

    @Test
    void failingProjectionWithModelFlagIsAtLeastLeanWithdraw() {
        // 0.4*0.55 + 0.25 = 0.47 -> uncertain, raised by the withdraw rule
        Result r = FusionEngine.compute(new Inputs(0.55, true, 65, 0.4, List.of(6, 7)));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.LEAN_WITHDRAW);
        assertThat(r.appliedRule()).isEqualTo("withdraw");
        assertThat(r.confidence()).isEqualTo(0.47);
    }

    @Test
    void lowRiskPassingStudentIsStrongStay() {
        Result r = FusionEngine.compute(new Inputs(0.06, false, 85, 0.5, List.of(8, 9)));

        assertThat(r.recommendation()).isEqualTo(RecommendationType.STRONG_STAY);
        assertThat(r.reasoning()).anyMatch(s -> s.contains("low risk"));
        assertThat(r.reasoning()).noneMatch(s -> s.contains("%") && s.contains("model"));
    }

    @Test
    void lowRatingsCountAsDistressOnlyAboveHalf() {
        Result half = FusionEngine.compute(new Inputs(0.1, false, 80, 0.5, List.of(2, 9)));
        Result most = FusionEngine.compute(new Inputs(0.1, false, 80, 0.5, List.of(2, 3, 9)));

        assertThat(half.distressRatio()).isEqualTo(0.5);
        assertThat(half.withdrawScore()).isEqualTo(0.04);
        assertThat(most.withdrawScore()).isEqualTo(0.19);
        assertThat(most.lowRatings()).isEqualTo(2);
    }
}
