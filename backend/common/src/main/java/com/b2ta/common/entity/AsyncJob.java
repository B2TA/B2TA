package com.b2ta.common.entity;

import com.b2ta.common.entity.converter.PersistableEnumConverters;
import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.common.entity.enums.JobType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "async_job")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private GradingSession session;

    @Convert(converter = PersistableEnumConverters.JobTypeConverter.class)
    @Column(name = "job_type", nullable = false, length = 30)
    private JobType jobType;

    @Convert(converter = PersistableEnumConverters.JobStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "progress_current")
    private Integer progressCurrent;

    @Column(name = "progress_total")
    private Integer progressTotal;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = JobStatus.QUEUED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
