package com.b2ta.api.repository;

import com.b2ta.common.entity.AsyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AsyncJobRepository extends JpaRepository<AsyncJob, UUID> {

    Optional<AsyncJob> findByIdAndSessionTaId(UUID id, UUID taId);
}
