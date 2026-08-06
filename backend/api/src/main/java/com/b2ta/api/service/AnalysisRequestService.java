package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.dto.job.JobCreatedResponse;
import com.b2ta.common.entity.AsyncJob;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.enums.JobType;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.job.JobDispatcher;
import com.b2ta.common.job.JobMessage;
import com.b2ta.common.job.JobService;
import com.b2ta.common.repository.CriterionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns a TA's request for match analysis into a tracked, queued job.
 *
 * <p>Analysis runs in the Worker service, so the API's job here is to validate that the request makes
 * sense, create the {@code async_job} row the browser will poll, and publish the message. Validation
 * happens before the job is created so an impossible request fails synchronously with a reason rather
 * than producing a job that immediately fails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisRequestService {

    private final TenantGuard tenantGuard;
    private final CriterionRepository criterionRepository;
    private final JobService jobService;
    private final JobDispatcher jobDispatcher;

    @Transactional
    public JobCreatedResponse requestSubmissionAnalysis(TaPrincipal ta, UUID sessionId,
                                                        UUID submissionId, boolean force) {
        Submission submission = tenantGuard.requireSubmission(ta, sessionId, submissionId);
        requireAnalyzableText(submission);

        int criterionCount = criterionRepository.findBySessionIdWithLevels(sessionId).size();
        if (criterionCount == 0) {
            throw ApiException.badRequest(ErrorCode.RUBRIC_NOT_READY,
                    "This session has no rubric criteria to analyse against");
        }

        AsyncJob job = jobService.create(sessionId, JobType.MATCH_ANALYSIS, criterionCount);
        dispatch(JobMessage.builder()
                .jobId(job.getId())
                .jobType(JobType.MATCH_ANALYSIS)
                .sessionId(sessionId)
                .submissionId(submissionId)
                .force(force)
                .build());
        return JobCreatedResponse.builder().jobId(job.getId()).build();
    }

    @Transactional
    public JobCreatedResponse requestCriterionReanalysis(TaPrincipal ta, UUID sessionId,
                                                          UUID submissionId, UUID criterionId) {
        Submission submission = tenantGuard.requireSubmission(ta, sessionId, submissionId);
        tenantGuard.requireCriterion(ta, sessionId, criterionId);
        requireAnalyzableText(submission);

        AsyncJob job = jobService.create(sessionId, JobType.MATCH_ANALYSIS, 1);
        dispatch(JobMessage.builder()
                .jobId(job.getId())
                .jobType(JobType.MATCH_ANALYSIS)
                .sessionId(sessionId)
                .submissionId(submissionId)
                .criterionId(criterionId)
                // A re-analysis always discards the previous generation, otherwise the request would
                // be a no-op for a criterion that already completed.
                .force(true)
                .build());
        return JobCreatedResponse.builder().jobId(job.getId()).build();
    }

    private void dispatch(JobMessage message) {
        if (!jobDispatcher.dispatch(message)) {
            jobService.markFailed(message.getJobId(),
                    "The analysis queue is unavailable. Try again in a moment.");
            throw ApiException.unprocessable(ErrorCode.ANALYSIS_UNAVAILABLE,
                    "Analysis could not be queued because the job queue is unavailable");
        }
    }

    /**
     * Rejects analysis of a submission with no text.
     *
     * <p>Checked here rather than in the worker so the TA gets an immediate, specific reason instead
     * of a job that fails a few seconds later (Requirement 6.10, 10.10).
     */
    private void requireAnalyzableText(Submission submission) {
        String text = submission.getExtractedText();
        if (text == null || text.isBlank()) {
            throw ApiException.unprocessable(ErrorCode.NO_EXTRACTED_TEXT,
                    "This submission has no extracted text, so it cannot be analysed");
        }
    }
}
