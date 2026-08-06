package com.b2ta.common.job;

import com.b2ta.common.entity.AsyncJob;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.common.entity.enums.JobType;
import com.b2ta.common.repository.AsyncJobRepository;
import com.b2ta.common.repository.GradingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates and updates {@code async_job} rows (Requirement 19.8).
 *
 * <p>Long-running work is never held open on an HTTP request. The API creates a job, returns its id,
 * and the browser polls {@code GET /api/jobs/{id}} — so no request outlives the 30-second budget of
 * Requirement 19.8 regardless of how long ingestion or analysis takes.
 *
 * <p>Progress updates commit in their own transaction so a poll sees them while the work is still
 * running. Joining the worker's transaction would make progress invisible until the job finished,
 * which defeats the purpose of reporting it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final AsyncJobRepository jobRepository;
    private final GradingSessionRepository sessionRepository;

    @Transactional
    public AsyncJob create(UUID sessionId, JobType type, int total) {
        GradingSession session = sessionRepository.getReferenceById(sessionId);
        AsyncJob job = jobRepository.save(AsyncJob.builder()
                .session(session)
                .jobType(type)
                .status(JobStatus.QUEUED)
                .progressCurrent(0)
                .progressTotal(total)
                .build());
        log.info("Created {} job {} for session {} with {} items",
                type.getDbValue(), job.getId(), sessionId, total);
        return job;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInProgress(UUID jobId, int total) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.IN_PROGRESS);
            if (total >= 0) {
                job.setProgressTotal(total);
            }
            jobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reportProgress(UUID jobId, int current) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setProgressCurrent(current);
            if (job.getStatus() == JobStatus.QUEUED) {
                job.setStatus(JobStatus.IN_PROGRESS);
            }
            jobRepository.save(job);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markComplete(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.COMPLETED);
            job.setProgressCurrent(job.getProgressTotal());
            jobRepository.save(job);
        });
    }

    /**
     * Records a terminal failure.
     *
     * <p>The reason is shown to the TA, so it is written by the caller as a sentence a grader can act
     * on, not as an exception message.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String reason) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setFailureReason(reason == null ? "Job failed"
                    : reason.substring(0, Math.min(reason.length(), 500)));
            jobRepository.save(job);
        });
    }
}
