package com.b2ta.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "criterion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Criterion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_id", nullable = false)
    private Rubric rubric;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_points", precision = 7, scale = 2)
    private BigDecimal maxPoints;

    @Column(name = "display_color", nullable = false, length = 7)
    private String displayColor;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "requires_completion", nullable = false)
    private Boolean requiresCompletion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "criterion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<PerformanceLevel> performanceLevels = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (requiresCompletion == null) {
            requiresCompletion = false;
        }
    }
}
