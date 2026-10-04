package com.miadvisor.risk;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    Optional<RiskScore> findFirstByCourseIdOrderByComputedAtDescIdDesc(Long courseId);
}
