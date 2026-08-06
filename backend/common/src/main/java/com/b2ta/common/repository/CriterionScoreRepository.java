package com.b2ta.common.repository;

import com.b2ta.common.entity.CriterionScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CriterionScoreRepository extends JpaRepository<CriterionScore, UUID> {

    @Query("SELECT s FROM CriterionScore s WHERE s.gradingRecord.id = :gradingRecordId")
    List<CriterionScore> findByGradingRecordId(@Param("gradingRecordId") UUID gradingRecordId);
}
