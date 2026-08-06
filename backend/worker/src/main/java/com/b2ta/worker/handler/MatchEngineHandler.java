package com.b2ta.worker.handler;

import com.b2ta.common.ai.MatchAnalysisService;
import com.b2ta.common.job.JobMessage;
import com.b2ta.common.job.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Handles {@code match_analysis} messages (tasks 5.4, 5.5).
 *
 * <p>Thin by design: chunking, Bedrock invocation, offset remapping, deduplication, and persistence
 * all live in the shared {@code com.b2ta.common.ai} package. What this class owns is the job
 * lifecycle — marking the job in progress, reporting per-criterion progress so the browser's progress
 * bar advances, and recording a terminal status.
 *
 * <p>The handler never throws on an analysis failure. A criterion Bedrock cannot handle is recorded as
 * analysis-unavailable inside {@link MatchAnalysisService} and the job still completes; letting the
 * exception escape would make SQS redeliver the message and repeat the whole submission for the sake
 * of one criterion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEngineHandler {

    private final MatchAnalysisService analysisService;
    private final JobService jobService;

    public void handle(JobMessage message) {
        MDC.put("jobId", String.valueOf(message.getJobId()));
        MDC.put("sessionId", String.valueOf(message.getSessionId()));
        MDC.put("submissionId", String.valueOf(message.getSubmissionId()));
        try {
            if (message.getSubmissionId() == null) {
                jobService.markFailed(message.getJobId(),
                        "The analysis request did not name a submission");
                return;
            }

            if (message.getCriterionId() != null) {
                handleSingleCriterion(message);
            } else {
                handleWholeSubmission(message);
            }
        } catch (RuntimeException e) {
            log.error("Match analysis job {} failed", message.getJobId(), e);
            jobService.markFailed(message.getJobId(),
                    "Analysis could not be completed. Try re-running it for this submission.");
        } finally {
            MDC.clear();
        }
    }

    private void handleSingleCriterion(JobMessage message) {
        jobService.markInProgress(message.getJobId(), 1);
        MatchAnalysisService.Outcome outcome = analysisService.reanalyzeCriterion(
                message.getSubmissionId(), message.getCriterionId());
        jobService.reportProgress(message.getJobId(), 1);

        if (outcome == MatchAnalysisService.Outcome.UNAVAILABLE) {
            // The job completed: the outcome is recorded against the criterion, and the marking view
            // reads it from there. A failed job status would suggest the request never ran.
            log.info("Re-analysis of criterion {} finished with no available analysis",
                    message.getCriterionId());
        }
        jobService.markComplete(message.getJobId());
    }

    private void handleWholeSubmission(JobMessage message) {
        jobService.markInProgress(message.getJobId(), -1);
        MatchAnalysisService.AnalysisSummary summary = analysisService.analyzeSubmission(
                message.getSubmissionId(),
                message.isForce(),
                completed -> jobService.reportProgress(message.getJobId(), completed));

        log.info("Analysis of submission {} finished: {} analysed, {} reused, {} unavailable",
                message.getSubmissionId(), summary.analyzed(), summary.skipped(),
                summary.unavailable());

        if (summary.total() > 0 && summary.unavailable() == summary.total()) {
            // Every criterion failed, which is a real failure of the request rather than a partial
            // result, so the TA is shown a reason and a retry rather than an empty rubric panel.
            jobService.markFailed(message.getJobId(),
                    "No criterion could be analysed for this submission. "
                            + "The analysis service may be unavailable.");
            return;
        }
        jobService.markComplete(message.getJobId());
    }
}
