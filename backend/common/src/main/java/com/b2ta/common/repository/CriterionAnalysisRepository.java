package com.b2ta.common.repository;

import com.b2ta.common.entity.CriterionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CriterionAnalysisRepository extends JpaRepository<CriterionAnalysis, UUID> {

    @Query("SELECT a FROM CriterionAnalysis a WHERE a.submission.id = :submissionId")
    List<CriterionAnalysis> findBySubmissionId(@Param("submissionId") UUID submissionId);

    @Query("SELECT a FROM CriterionAnalysis a WHERE a.submission.id = :submissionId "
            + "AND a.criterion.id = :criterionId")
    Optional<CriterionAnalysis> findBySubmissionIdAndCriterionId(
            @Param("submissionId") UUID submissionId, @Param("criterionId") UUID criterionId);
}
