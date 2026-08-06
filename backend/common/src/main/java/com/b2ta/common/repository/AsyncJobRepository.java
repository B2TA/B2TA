package com.b2ta.common.repository;

import com.b2ta.common.entity.AsyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AsyncJobRepository extends JpaRepository<AsyncJob, UUID> {

    /**
     * Tenant-scoped job lookup.
     *
     * <p>Job ids are polled by the browser, so this is an externally reachable identifier and has
     * to be filtered by TA like any other resource (Requirement 18.5).
     */
    @Query("SELECT j FROM AsyncJob j WHERE j.id = :jobId AND j.session.ta.id = :taId")
    Optional<AsyncJob> findByIdAndTaId(@Param("jobId") UUID jobId, @Param("taId") UUID taId);
}
