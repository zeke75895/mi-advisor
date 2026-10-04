package com.miadvisor.session;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    List<StudySession> findTop50ByCourseIdOrderByStudiedOnDescIdDesc(Long courseId);

    List<StudySession> findByCourseIdAndStudiedOnBetween(Long courseId, LocalDate from, LocalDate to);

    List<StudySession> findByCourseUserIdAndStudiedOnBetween(Long userId, LocalDate from, LocalDate to);

    long countByCourseId(Long courseId);

    java.util.Optional<StudySession> findFirstByCourseIdOrderByStudiedOnAsc(Long courseId);
}
