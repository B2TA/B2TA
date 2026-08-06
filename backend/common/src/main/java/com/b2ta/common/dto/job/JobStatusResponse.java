package com.b2ta.common.dto.job;

import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.common.entity.enums.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusResponse {

    private UUID id;
    private UUID sessionId;
    private JobType jobType;
    private JobStatus status;
    private Integer progressCurrent;
    private Integer progressTotal;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
}
