package com.b2ta.api.repository;

import com.b2ta.common.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findAllBySessionIdOrderByPositionAsc(UUID sessionId);

    Optional<Submission> findByIdAndSessionId(UUID id, UUID sessionId);

    long countBySessionId(UUID sessionId);
}
