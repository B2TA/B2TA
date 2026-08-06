package com.b2ta.api.repository;

import com.b2ta.common.entity.GradingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradingSessionRepository extends JpaRepository<GradingSession, UUID> {

    List<GradingSession> findAllByTaIdOrderByCreatedAtDesc(UUID taId);

    Optional<GradingSession> findByIdAndTaId(UUID id, UUID taId);

    void deleteByIdAndTaId(UUID id, UUID taId);
}
