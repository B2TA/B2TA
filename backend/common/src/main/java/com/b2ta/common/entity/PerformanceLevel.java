package com.b2ta.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "performance_level")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criterion_id", nullable = false)
    private Criterion criterion;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "points", precision = 7, scale = 2)
    private BigDecimal points;

    @Column(name = "position", nullable = false, columnDefinition = "SMALLINT")
    private Short position;
}
