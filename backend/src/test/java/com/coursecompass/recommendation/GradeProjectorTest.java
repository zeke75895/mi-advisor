package com.coursecompass.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.coursecompass.item.GradeCategory;
import com.coursecompass.recommendation.GradeProjector.ItemInput;
import com.coursecompass.recommendation.GradeProjector.Projection;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradeProjectorTest {

    @Test
    void mixesActualScoresAndSelfRatingsByCategory() {
        Projection p = GradeProjector.project(List.of(
                new ItemInput(GradeCategory.EXAM, 25, true, 60.0, null), // midterm graded: 60%
                new ItemInput(GradeCategory.EXAM, 35, false, null, 5), // final rated 5/10 -> 50%
                new ItemInput(GradeCategory.HOMEWORK, 40, true, 90.0, null)));

        // EXAM: weight 60, mean(60, 50) = 55. HOMEWORK: weight 40, score 90. (60*55 + 40*90) / 100 = 69
        assertThat(p.projectedFinal()).isCloseTo(69.0, within(1e-9));
        // Graded only: (25*60 + 40*90) / 65
        assertThat(p.currentGrade()).isCloseTo(78.4615, within(1e-3));
        assertThat(p.remainingWeight()).isCloseTo(0.35, within(1e-9));
        assertThat(p.coveredWeight()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void skipsCategoriesWithNoScoresAndRenormalizes() {
        Projection p = GradeProjector.project(List.of(
                new ItemInput(GradeCategory.QUIZ, 20, true, 80.0, null),
                new ItemInput(GradeCategory.PROJECT, 30, false, null, null)));

        assertThat(p.projectedFinal()).isCloseTo(80.0, within(1e-9));
        assertThat(p.coveredWeight()).isCloseTo(0.4, within(1e-9));
        assertThat(p.remainingWeight()).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void returnsNullProjectionWhenNothingIsGradedOrRated() {
        Projection p = GradeProjector.project(
                List.of(new ItemInput(GradeCategory.EXAM, 50, false, null, null)));

        assertThat(p.projectedFinal()).isNull();
        assertThat(p.currentGrade()).isNull();
        assertThat(p.remainingWeight()).isEqualTo(1.0);
    }
}
