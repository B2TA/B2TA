package com.b2ta.api.repository;

import com.b2ta.common.entity.Criterion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CriterionRepository extends JpaRepository<Criterion, UUID> {

    List<Criterion> findByRubricIdOrderByPositionAsc(UUID rubricId);

    void deleteAllByRubricId(UUID rubricId);
}
