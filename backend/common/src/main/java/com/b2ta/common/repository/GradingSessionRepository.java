package com.b2ta.common.repository;

import com.b2ta.common.entity.GradingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant root repository.
 *
 * <p>Every finder takes the authenticated TA's id. There is deliberately no {@code findById}
 * convenience wrapper: callers must go through {@link #findByIdAndTaId} so that a session
 * belonging to another TA is indistinguishable from one that does not exist (Requirement 18.5).
 */
public interface GradingSessionRepository extends JpaRepository<GradingSession, UUID> {

    @Query("SELECT s FROM GradingSession s WHERE s.id = :id AND s.ta.id = :taId")
    Optional<GradingSession> findByIdAndTaId(@Param("id") UUID id, @Param("taId") UUID taId);

    @Query("SELECT s FROM GradingSession s WHERE s.ta.id = :taId ORDER BY s.updatedAt DESC")
    List<GradingSession> findAllByTaId(@Param("taId") UUID taId);

    @Query("SELECT COUNT(sub) FROM Submission sub WHERE sub.session.id = :sessionId")
    int countSubmissions(@Param("sessionId") UUID sessionId);

    /**
     * Clears the review confirmation of a session (Requirement 15.11).
     *
     * <p>Expressed as a bulk update rather than a load-mutate-save so that saving a grading
     * record does not have to pull the session aggregate into the persistence context, and so
     * the {@code updated_at} bump happens in the same statement.
     */
    /**
     * {@code flushAutomatically} pushes pending entity changes before the bulk statement runs, and
     * {@code clearAutomatically} evicts the now-stale session entity from the persistence context.
     * Without the clear, code that reads the session again in the same transaction would see the
     * pre-update {@code reviewConfirmedAt} and report a confirmation that no longer holds.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GradingSession s SET s.reviewConfirmedAt = NULL, s.updatedAt = :now "
            + "WHERE s.id = :sessionId AND s.reviewConfirmedAt IS NOT NULL")
    int clearReviewConfirmation(@Param("sessionId") UUID sessionId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GradingSession s SET s.updatedAt = :now WHERE s.id = :sessionId")
    int touch(@Param("sessionId") UUID sessionId, @Param("now") Instant now);
}
