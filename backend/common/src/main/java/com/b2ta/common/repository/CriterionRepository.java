package com.b2ta.common.repository;

import com.b2ta.common.entity.Criterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CriterionRepository extends JpaRepository<Criterion, UUID> {

    @Query("SELECT c FROM Criterion c WHERE c.rubric.id = :rubricId ORDER BY c.position ASC")
    List<Criterion> findByRubricIdOrderByPosition(@Param("rubricId") UUID rubricId);

    /**
     * Loads a session's criteria with their performance levels in one query.
     *
     * <p>The rubric panel, the score calculator, the review screen, and both exporters all need
     * criteria plus levels; a join fetch keeps that at one round trip instead of N+1.
     */
    @Query("SELECT DISTINCT c FROM Criterion c "
            + "LEFT JOIN FETCH c.performanceLevels "
            + "WHERE c.rubric.session.id = :sessionId "
            + "ORDER BY c.position ASC")
    List<Criterion> findBySessionIdWithLevels(@Param("sessionId") UUID sessionId);

    /** Tenant-scoped single-criterion lookup for the re-analyze and manual-match endpoints. */
    @Query("SELECT c FROM Criterion c WHERE c.id = :criterionId "
            + "AND c.rubric.session.id = :sessionId AND c.rubric.session.ta.id = :taId")
    Optional<Criterion> findByIdAndSessionIdAndTaId(@Param("criterionId") UUID criterionId,
                                                    @Param("sessionId") UUID sessionId,
                                                    @Param("taId") UUID taId);
}
