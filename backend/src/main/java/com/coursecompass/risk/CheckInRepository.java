package com.coursecompass.risk;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    Optional<CheckIn> findFirstByCourseIdOrderByCreatedAtDescIdDesc(Long courseId);
}
