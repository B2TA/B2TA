package com.b2ta.common.entity;

import com.b2ta.common.entity.converter.PersistableEnumConverters;
import com.b2ta.common.entity.enums.AnalysisState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Match_Engine analysis state for one (submission, criterion) pair.
 *
 * <p>Without this row the API cannot tell "Bedrock analysed this criterion and found no relevant
 * passage" (Req 6.6 no-evidence-found) from "every Bedrock attempt failed" (Req 6.7-6.8
 * analysis-unavailable) — both look like zero suggested matches. It also records the character
 * count that was analysed so the frontend can mark matches stale after a re-extraction.
 */
@Entity
@Table(name = "criterion_analysis", uniqueConstraints = {
        @UniqueConstraint(name = "uq_criterion_analysis", columnNames = {"submission_id", "criterion_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private Criterion criterion;

    @Convert(converter = PersistableEnumConverters.AnalysisStateConverter.class)
    @Column(name = "state", nullable = false, length = 20)
    private AnalysisState state;

    /** Consecutive Bedrock failures; at 4 the pair becomes {@link AnalysisState#UNAVAILABLE}. */
    @Column(name = "failure_count", nullable = false, columnDefinition = "SMALLINT")
    private Short failureCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Length of the extracted text that was analysed, used for staleness detection. */
    @Column(name = "analyzed_char_count")
    private Integer analyzedCharCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (state == null) {
            state = AnalysisState.PENDING;
        }
        if (failureCount == null) {
            failureCount = (short) 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
