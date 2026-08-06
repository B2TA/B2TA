package com.b2ta.common.entity;

import com.b2ta.common.entity.converter.PersistableEnumConverters;
import com.b2ta.common.entity.enums.MatchOrigin;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "confirmed_match")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmedMatch {

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

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Convert(converter = PersistableEnumConverters.MatchOriginConverter.class)
    @Column(name = "origin", nullable = false, length = 20)
    private MatchOrigin origin;

    @Column(name = "source_match_id")
    private UUID sourceMatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
