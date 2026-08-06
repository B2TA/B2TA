package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.common.dto.job.JobStatusResponse;
import com.b2ta.common.entity.AsyncJob;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.repository.AsyncJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Async job status polling (Requirement 19.8).
 *
 * <p>This is how the browser follows work that outlives a request: rubric parsing, submission
 * ingestion, and match analysis all answer with a job id, and the SPA polls this endpoint rather than
 * holding a connection open.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final AsyncJobRepository jobRepository;

    @GetMapping("/{jobId}")
    @Transactional(readOnly = true)
    public JobStatusResponse get(@CurrentTa TaPrincipal ta, @PathVariable UUID jobId) {
        AsyncJob job = jobRepository.findByIdAndTaId(jobId, ta.taId())
                .orElseThrow(() -> ApiException.notFound("Job " + jobId + " was not found"));

        return JobStatusResponse.builder()
                .id(job.getId())
                .sessionId(job.getSession().getId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .progressCurrent(job.getProgressCurrent())
                .progressTotal(job.getProgressTotal())
                .failureReason(job.getFailureReason())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
