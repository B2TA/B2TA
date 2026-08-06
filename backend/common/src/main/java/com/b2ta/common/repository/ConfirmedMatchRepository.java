package com.b2ta.common.repository;

import com.b2ta.common.entity.ConfirmedMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfirmedMatchRepository extends JpaRepository<ConfirmedMatch, UUID> {

    @Query("SELECT m FROM ConfirmedMatch m WHERE m.submission.id = :submissionId "
            + "ORDER BY m.criterion.position ASC, m.passageStart ASC")
    List<ConfirmedMatch> findBySubmissionId(@Param("submissionId") UUID submissionId);

    @Query("SELECT m FROM ConfirmedMatch m WHERE m.submission.session.id = :sessionId")
    List<ConfirmedMatch> findBySessionId(@Param("sessionId") UUID sessionId);

    @Query("SELECT m FROM ConfirmedMatch m WHERE m.id = :matchId "
            + "AND m.submission.id = :submissionId AND m.submission.session.ta.id = :taId")
    Optional<ConfirmedMatch> findByIdAndSubmissionIdAndTaId(@Param("matchId") UUID matchId,
                                                             @Param("submissionId") UUID submissionId,
                                                             @Param("taId") UUID taId);

    /**
     * Duplicate check for Requirement 10.9: the same criterion may not be associated twice with
     * the identical passage range. Backed by {@code uq_confirmed_match_passage}.
     */
    @Query("SELECT m FROM ConfirmedMatch m WHERE m.submission.id = :submissionId "
            + "AND m.criterion.id = :criterionId "
            + "AND m.passageStart = :start AND m.passageEnd = :end")
    Optional<ConfirmedMatch> findByPassage(@Param("submissionId") UUID submissionId,
                                           @Param("criterionId") UUID criterionId,
                                           @Param("start") Integer start,
                                           @Param("end") Integer end);

    void deleteBySubmissionId(UUID submissionId);
}
