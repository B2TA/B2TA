package com.b2ta.api.service;

import com.b2ta.common.ai.MatchAnalysisService;
import com.b2ta.common.entity.enums.JobType;
import com.b2ta.common.job.JobMessage;
import com.b2ta.common.job.JobService;
import com.b2ta.common.job.LocalJobExecutor;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs analysis jobs inside the API process when no SQS queue is configured.
 *
 * <p>Exists so the frontend's full flow — request analysis, poll the job, render the matches — can be
 * exercised with just the API service running, instead of requiring a queue and a second process
 * before anything is visible.
 *
 * <p>The bean is always registered, but {@link com.b2ta.common.job.JobDispatcher} only reaches it when
 * {@code aws.sqs.queue-url} is unset. Gating the bean on the property instead is not expressible with
 * {@code @ConditionalOnProperty}, which cannot match "empty", and the dispatcher's own check is the
 * behaviour that actually matters: with a queue configured, nothing here ever runs.
 *
 * <p>It is not a substitute for the Worker service. The pool is small, and unbounded work here would
 * compete with request threads for the database connection pool.
 */
@Slf4j
@Component
public class InProcessJobExecutor implements LocalJobExecutor {

    private final MatchAnalysisService analysisService;
    private final JobService jobService;
    private final ExecutorService pool;

    public InProcessJobExecutor(MatchAnalysisService analysisService, JobService jobService) {
        this.analysisService = analysisService;
        this.jobService = jobService;
        this.pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "local-job-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void execute(JobMessage message) {
        // Returns immediately: the caller is an HTTP request thread that has to answer with a job id.
        pool.submit(() -> run(message));
    }

    private void run(JobMessage message) {
        MDC.put("jobId", String.valueOf(message.getJobId()));
        try {
            if (message.getJobType() != JobType.MATCH_ANALYSIS) {
                jobService.markFailed(message.getJobId(),
                        "In-process execution supports match analysis only");
                return;
            }
            if (message.getCriterionId() != null) {
                jobService.markInProgress(message.getJobId(), 1);
                analysisService.reanalyzeCriterion(message.getSubmissionId(),
                        message.getCriterionId());
                jobService.reportProgress(message.getJobId(), 1);
            } else {
                jobService.markInProgress(message.getJobId(), -1);
                analysisService.analyzeSubmission(message.getSubmissionId(), message.isForce(),
                        completed -> jobService.reportProgress(message.getJobId(), completed));
            }
            jobService.markComplete(message.getJobId());
        } catch (RuntimeException e) {
            log.error("In-process job {} failed", message.getJobId(), e);
            jobService.markFailed(message.getJobId(), "Analysis could not be completed");
        } finally {
            MDC.clear();
        }
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }
}
