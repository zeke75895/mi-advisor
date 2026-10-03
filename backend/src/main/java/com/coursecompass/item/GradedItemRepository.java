package com.coursecompass.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradedItemRepository extends JpaRepository<GradedItem, Long> {

    List<GradedItem> findByCourseIdOrderByDueDateAscIdAsc(Long courseId);
}
