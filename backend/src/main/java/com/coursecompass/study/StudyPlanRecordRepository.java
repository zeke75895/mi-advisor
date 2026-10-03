package com.coursecompass.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyPlanRecordRepository extends JpaRepository<StudyPlanRecord, Long> {

    Optional<StudyPlanRecord> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
