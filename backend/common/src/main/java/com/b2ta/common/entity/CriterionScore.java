package com.b2ta.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "criterion_score", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"grading_record_id", "criterion_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grading_record_id", nullable = false)
    private GradingRecord gradingRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private Criterion criterion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_level_id")
    private PerformanceLevel selectedLevel;

    @Column(name = "override_points", precision = 7, scale = 2)
    private BigDecimal overridePoints;

    @Column(name = "criterion_feedback", columnDefinition = "TEXT")
    private String criterionFeedback;
}
