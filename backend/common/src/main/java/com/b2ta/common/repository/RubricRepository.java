package com.b2ta.common.repository;

import com.b2ta.common.entity.Rubric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RubricRepository extends JpaRepository<Rubric, UUID> {

    @Query("SELECT r FROM Rubric r WHERE r.session.id = :sessionId")
    Optional<Rubric> findBySessionId(@Param("sessionId") UUID sessionId);

    /** Tenant-scoped variant used when only the rubric is needed, without loading the session. */
    @Query("SELECT r FROM Rubric r WHERE r.session.id = :sessionId AND r.session.ta.id = :taId")
    Optional<Rubric> findBySessionIdAndTaId(@Param("sessionId") UUID sessionId,
                                            @Param("taId") UUID taId);
}
