package com.b2ta.common.repository;

import com.b2ta.common.entity.GradingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingRecordRepository extends JpaRepository<GradingRecord, UUID> {

    @Query("SELECT r FROM GradingRecord r WHERE r.submission.id = :submissionId")
    Optional<GradingRecord> findBySubmissionId(@Param("submissionId") UUID submissionId);

    /**
     * Loads every grading record of a session with its criterion scores.
     *
     * <p>The review screen and both exporters need all 150 records at once; without the join
     * fetch this is 1 + 150 queries and blows the 3-second render budget of Requirement 15.10.
     */
    @Query("SELECT DISTINCT r FROM GradingRecord r "
            + "LEFT JOIN FETCH r.criterionScores "
            + "WHERE r.submission.session.id = :sessionId")
    List<GradingRecord> findBySessionIdWithScores(@Param("sessionId") UUID sessionId);
}
