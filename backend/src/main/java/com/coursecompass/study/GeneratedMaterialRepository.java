package com.coursecompass.study;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedMaterialRepository extends JpaRepository<GeneratedMaterial, Long> {

    Optional<GeneratedMaterial> findFirstByCourseIdAndKindOrderByCreatedAtDescIdDesc(
            Long courseId, GeneratedMaterial.Kind kind);
}
