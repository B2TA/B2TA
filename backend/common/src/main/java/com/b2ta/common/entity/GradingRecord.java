package com.b2ta.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "grading_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false, unique = true)
    private Submission submission;

    @Column(name = "overall_feedback", columnDefinition = "TEXT")
    private String overallFeedback;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "gradingRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CriterionScore> criterionScores = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
