package com.b2ta.api.controller;

import com.b2ta.api.service.JobService;
import com.b2ta.common.dto.job.JobStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * Poll the status of an async job.
     * Returns status, progress_current, progress_total, and failure_reason.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable UUID jobId) {
        JobStatusResponse response = jobService.getJobStatus(jobId);
        return ResponseEntity.ok(response);
    }
}
