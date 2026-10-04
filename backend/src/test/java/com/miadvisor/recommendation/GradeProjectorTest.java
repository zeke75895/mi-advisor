package com.miadvisor.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.miadvisor.recommendation.GradeProjector.ItemInput;
import com.miadvisor.recommendation.GradeProjector.Projection;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradeProjectorTest {

    @Test
    void weightsEachItemByItsSyllabusWeight() {
        Projection p = GradeProjector.project(List.of(
                new ItemInput(25, true, 60.0, null), // midterm graded: 60%
                new ItemInput(35, false, null, 5), // final rated 5/10 -> 50
                new ItemInput(40, true, 90.0, null))); // homework graded: 90%

        // (25*60 + 35*50 + 40*90) / 100 = 68.5
        assertThat(p.projectedFinal()).isCloseTo(68.5, within(1e-9));
        // Graded only: (25*60 + 40*90) / 65
        assertThat(p.currentGrade()).isCloseTo(78.4615, within(1e-3));
        assertThat(p.remainingWeight()).isCloseTo(0.35, within(1e-9));
        assertThat(p.coveredWeight()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void heavyItemsOutweighLightOnes() {
        // A 1% homework at 100% barely moves a 30% project rated 4/10
        Projection p = GradeProjector.project(List.of(
                new ItemInput(1, true, 100.0, null),
                new ItemInput(30, false, null, 4)));

        assertThat(p.projectedFinal()).isCloseTo((1 * 100 + 30 * 40) / 31.0, within(1e-9));
    }

    @Test
    void leavesOutUnratedUngradedItems() {
        Projection p = GradeProjector.project(List.of(
                new ItemInput(20, true, 80.0, null),
                new ItemInput(80, false, null, null)));

        assertThat(p.projectedFinal()).isCloseTo(80.0, within(1e-9));
        assertThat(p.coveredWeight()).isCloseTo(0.2, within(1e-9));
        assertThat(p.remainingWeight()).isCloseTo(0.8, within(1e-9));
    }

    @Test
    void weightNotEnteredYetCountsAsStillAhead() {
        // Only the 30% midterm is entered: 70% of the final grade is still ahead, not 0%
        Projection p = GradeProjector.project(List.of(new ItemInput(30, true, 85.0, null)));

        assertThat(p.projectedFinal()).isCloseTo(85.0, within(1e-9));
        assertThat(p.remainingWeight()).isCloseTo(0.7, within(1e-9));
        assertThat(p.coveredWeight()).isCloseTo(0.3, within(1e-9));
    }

    @Test
    void weightsOverOneHundredAreNormalizedByTheirSum() {
        Projection p = GradeProjector.project(List.of(
                new ItemInput(60, true, 90.0, null),
                new ItemInput(60, false, null, 5)));

        assertThat(p.remainingWeight()).isCloseTo(0.5, within(1e-9));
        assertThat(p.coveredWeight()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void neededScoreComesFromActualGradesOnly() {
        Projection p = GradeProjector.project(List.of(
                new ItemInput(25, true, 55.0, null),
                new ItemInput(40, true, 70.0, null),
                new ItemInput(35, false, null, 3))); // the rating doesn't change what's needed

        // Earned: 25*0.55 + 40*0.70 = 41.75 points; need (70 - 41.75) / 0.35 = 80.71% on the last 35%
        assertThat(p.earnedPoints()).isCloseTo(41.75, within(1e-9));
        assertThat(p.requiredScore()).isCloseTo(80.714, within(1e-3));
        assertThat(p.maxPossible()).isCloseTo(76.75, within(1e-9));
    }

    @Test
    void neededScoreFlagsSecuredAndImpossibleAndFinished() {
        assertThat(GradeProjector.project(List.of(new ItemInput(80, true, 95.0, null), new ItemInput(20, false, null, null)))
                .requiredScore()).isLessThanOrEqualTo(0); // 76 points already
        assertThat(GradeProjector.project(List.of(new ItemInput(80, true, 50.0, null), new ItemInput(20, false, null, null)))
                .requiredScore()).isGreaterThan(100); // 40 points, best possible 60
        assertThat(GradeProjector.project(List.of(new ItemInput(100, true, 65.0, null))).requiredScore()).isNull();
    }

    @Test
    void returnsNullProjectionWhenNothingIsGradedOrRated() {
        Projection p = GradeProjector.project(List.of(new ItemInput(50, false, null, null)));

        assertThat(p.projectedFinal()).isNull();
        assertThat(p.currentGrade()).isNull();
        assertThat(p.remainingWeight()).isEqualTo(1.0);
    }
}
