package com.b2ta.common.repository;

import com.b2ta.common.entity.SuggestedMatch;
import com.b2ta.common.entity.enums.MatchState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuggestedMatchRepository extends JpaRepository<SuggestedMatch, UUID> {

    /**
     * All non-stale suggestions for a submission.
     *
     * <p>Rejected suggestions are included so the caller can avoid re-presenting them
     * (Requirement 10.2), and filtered out when building the response.
     */
    @Query("SELECT m FROM SuggestedMatch m WHERE m.submission.id = :submissionId "
            + "AND m.isStale = false ORDER BY m.criterion.position ASC, m.passageStart ASC")
    List<SuggestedMatch> findBySubmissionId(@Param("submissionId") UUID submissionId);

    @Query("SELECT m FROM SuggestedMatch m WHERE m.submission.id = :submissionId "
            + "AND m.criterion.id = :criterionId AND m.isStale = false "
            + "ORDER BY m.passageStart ASC")
    List<SuggestedMatch> findBySubmissionIdAndCriterionId(@Param("submissionId") UUID submissionId,
                                                         @Param("criterionId") UUID criterionId);

    /** Tenant-scoped lookup for the confirm and reject endpoints. */
    @Query("SELECT m FROM SuggestedMatch m WHERE m.id = :matchId "
            + "AND m.submission.id = :submissionId AND m.submission.session.ta.id = :taId")
    Optional<SuggestedMatch> findByIdAndSubmissionIdAndTaId(@Param("matchId") UUID matchId,
                                                            @Param("submissionId") UUID submissionId,
                                                            @Param("taId") UUID taId);

    /**
     * Marks the current suggestions of one (submission, criterion) pair stale.
     *
     * <p>Used by re-analysis (Requirement 6.14): previous suggestions stay in the table as an
     * audit trail but stop being served, so a TA never sees two generations of suggestions at
     * once.
     */
    @Modifying
    @Query("UPDATE SuggestedMatch m SET m.isStale = true "
            + "WHERE m.submission.id = :submissionId AND m.criterion.id = :criterionId "
            + "AND m.isStale = false")
    int markStale(@Param("submissionId") UUID submissionId, @Param("criterionId") UUID criterionId);

    @Query("SELECT COUNT(m) FROM SuggestedMatch m WHERE m.submission.id = :submissionId "
            + "AND m.criterion.id = :criterionId AND m.isStale = false AND m.matchState = :state")
    int countByState(@Param("submissionId") UUID submissionId,
                     @Param("criterionId") UUID criterionId,
                     @Param("state") MatchState state);
}
