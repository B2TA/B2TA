package com.b2ta.common.repository;

import com.b2ta.common.entity.PerformanceLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PerformanceLevelRepository extends JpaRepository<PerformanceLevel, UUID> {

    @Query("SELECT l FROM PerformanceLevel l WHERE l.criterion.id = :criterionId ORDER BY l.position ASC")
    List<PerformanceLevel> findByCriterionIdOrderByPosition(@Param("criterionId") UUID criterionId);

    @Query("SELECT l FROM PerformanceLevel l WHERE l.criterion.rubric.session.id = :sessionId")
    List<PerformanceLevel> findBySessionId(@Param("sessionId") UUID sessionId);
}
