package com.miadvisor.item;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class GradedItemDtos {

    private GradedItemDtos() {}

    public record GradedItemRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull GradeCategory category,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double weight,
            @Positive Double pointsPossible,
            @PositiveOrZero Double pointsEarned,
            OffsetDateTime dueDate,
            Boolean isGraded) {

        boolean graded() {
            return Boolean.TRUE.equals(isGraded);
        }

        @JsonIgnore
        @AssertTrue(message = "graded items need pointsPossible and pointsEarned")
        public boolean isScoreConsistent() {
            return !graded() || (pointsPossible != null && pointsEarned != null);
        }
    }

    public record GradedItemResponse(
            Long id,
            Long courseId,
            String name,
            GradeCategory category,
            Double weight,
            Double pointsPossible,
            Double pointsEarned,
            OffsetDateTime dueDate,
            boolean isGraded,
            Integer latestRating) {

        static GradedItemResponse from(GradedItem item, Integer latestRating) {
            return new GradedItemResponse(
                    item.getId(),
                    item.getCourse().getId(),
                    item.getName(),
                    item.getCategory(),
                    item.getWeight(),
                    item.getPointsPossible(),
                    item.getPointsEarned(),
                    item.getDueDate(),
                    item.isGraded(),
                    latestRating);
        }
    }
}
