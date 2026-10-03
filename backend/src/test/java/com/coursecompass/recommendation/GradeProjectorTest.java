package com.coursecompass.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.coursecompass.recommendation.GradeProjector.ItemInput;
import com.coursecompass.recommendation.GradeProjector.Projection;
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
                new ItemInput(30, false, null, null)));

        assertThat(p.projectedFinal()).isCloseTo(80.0, within(1e-9));
        assertThat(p.coveredWeight()).isCloseTo(0.4, within(1e-9));
        assertThat(p.remainingWeight()).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void returnsNullProjectionWhenNothingIsGradedOrRated() {
        Projection p = GradeProjector.project(List.of(new ItemInput(50, false, null, null)));

        assertThat(p.projectedFinal()).isNull();
        assertThat(p.currentGrade()).isNull();
        assertThat(p.remainingWeight()).isEqualTo(1.0);
    }
}
