package com.coursecompass.course;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByUserIdOrderByIdAsc(Long userId);

    Optional<Course> findByIdAndUserId(Long id, Long userId);
}
