package com.b2ta.common.repository;

import com.b2ta.common.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    @Query("SELECT s FROM Submission s WHERE s.session.id = :sessionId ORDER BY s.position ASC")
    List<Submission> findBySessionIdOrderByPosition(@Param("sessionId") UUID sessionId);

    /**
     * Tenant-scoped lookup joining through session to TA.
     *
     * <p>Both the session id and the TA id are in the predicate: a submission id that exists but
     * belongs to a different session of the same TA is also treated as absent, so a guessed id
     * cannot be used to read across sessions.
     */
    @Query("SELECT s FROM Submission s WHERE s.id = :submissionId "
            + "AND s.session.id = :sessionId AND s.session.ta.id = :taId")
    Optional<Submission> findByIdAndSessionIdAndTaId(@Param("submissionId") UUID submissionId,
                                                     @Param("sessionId") UUID sessionId,
                                                     @Param("taId") UUID taId);

    @Query("SELECT s FROM Submission s WHERE s.id = :submissionId AND s.session.ta.id = :taId")
    Optional<Submission> findByIdAndTaId(@Param("submissionId") UUID submissionId,
                                         @Param("taId") UUID taId);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.session.id = :sessionId")
    int countBySessionId(@Param("sessionId") UUID sessionId);

    @Query("SELECT COALESCE(MAX(s.position), -1) FROM Submission s WHERE s.session.id = :sessionId")
    int findMaxPosition(@Param("sessionId") UUID sessionId);
}
