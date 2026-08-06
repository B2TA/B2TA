package com.b2ta.worker.repository;

import com.b2ta.common.entity.GradingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GradingSessionRepository extends JpaRepository<GradingSession, UUID> {
}
