package com.b2ta.worker.messaging;

import com.b2ta.common.entity.AsyncJob;
import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.worker.repository.AsyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles async job status updates in the database.
 * Extracted as a separate component so that Spring's @Transactional proxy works correctly
 * (self-invocation within SqsListener would bypass the proxy).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatusUpdater {

    private final AsyncJobRepository asyncJobRepository;

    @Transactional
    public void updateStatus(UUID jobId, JobStatus status, String failureReason) {
        asyncJobRepository.findById(jobId).ifPresentOrElse(
                job -> {
                    job.setStatus(status);
                    if (failureReason != null) {
                        job.setFailureReason(failureReason);
                    }
                    asyncJobRepository.save(job);
                    log.debug("Updated job id={} to status={}", jobId, status);
                },
                () -> log.warn("Job id={} not found in database when updating status to {}", jobId, status)
        );
    }

    @Transactional
    public void updateProgress(UUID jobId, int current, int total) {
        asyncJobRepository.findById(jobId).ifPresent(job -> {
            job.setProgressCurrent(current);
            job.setProgressTotal(total);
            asyncJobRepository.save(job);
        });
    }
}
