package com.b2ta.api.repository;

import com.b2ta.common.entity.Rubric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RubricRepository extends JpaRepository<Rubric, UUID> {

    Optional<Rubric> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);
}
