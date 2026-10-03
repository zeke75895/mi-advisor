package com.coursecompass.rating;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelfRatingRepository extends JpaRepository<SelfRating, Long> {

    List<SelfRating> findByGradedItemCourseIdOrderByCreatedAtAscIdAsc(Long courseId);
}
