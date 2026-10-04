package com.miadvisor.notes;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseNotesRepository extends JpaRepository<CourseNotes, Long> {

    Optional<CourseNotes> findByCourseId(Long courseId);
}
