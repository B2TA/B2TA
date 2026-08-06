package com.b2ta.common.entity;

import com.b2ta.common.entity.converter.PersistableEnumConverters;
import com.b2ta.common.entity.enums.DiscardReason;
import com.b2ta.common.entity.enums.MatchState;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suggested_match")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedMatch {

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

    @Column(name = "passage_start", nullable = false)
    private Integer passageStart;

    @Column(name = "passage_end", nullable = false)
    private Integer passageEnd;

    @Column(name = "rationale", nullable = false, length = 300)
    private String rationale;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Convert(converter = PersistableEnumConverters.MatchStateConverter.class)
    @Column(name = "match_state", nullable = false, length = 20)
    private MatchState matchState;

    @Column(name = "is_stale", nullable = false)
    private Boolean isStale;

    @Convert(converter = PersistableEnumConverters.DiscardReasonConverter.class)
    @Column(name = "discard_reason", length = 200)
    private DiscardReason discardReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isStale == null) {
            isStale = false;
        }
        if (matchState == null) {
            matchState = MatchState.PENDING;
        }
    }
}
