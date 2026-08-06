package com.b2ta.common.ai;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionAnalysis;
import com.b2ta.common.entity.enums.AnalysisState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Orchestrates Match_Engine analysis for a submission and reuses stored results
 * (Requirements 6.7, 6.8, 6.11, 6.14).
 *
 * <p>Analysis is a one-time cost per (submission, criterion): reopening a submission reads the stored
 * suggestions instead of paying for another Bedrock call. Re-analysis is therefore always explicit —
 * either the TA asked for it, or the extracted text changed.
 *
 * <p>The loop here is deliberately failure-tolerant. Each criterion is analysed and committed
 * independently through {@link MatchPersistenceService}, so one criterion that Bedrock cannot handle
 * is recorded as unavailable and the remaining criteria still produce evidence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchAnalysisService {

    /**
     * Characters of an oversized submission that are analysed (Requirement 6.10).
     *
     * <p>The full text is still stored and displayed; only this prefix is sent to Bedrock, and the
     * analysed length is recorded so the frontend can state what was covered rather than implying
     * the whole document was read.
     */
    public static final int MAX_ANALYZED_CHARS = 100_000;

    private final MatchPersistenceService persistence;
    private final MatchEngine matchEngine;

    /** Result of analysing one submission. */
    public record AnalysisSummary(int analyzed, int skipped, int unavailable) {

        public int total() {
            return analyzed + skipped + unavailable;
        }
    }

    /** Result of analysing one criterion. */
    public enum Outcome {
        /** Bedrock was called and matches (possibly zero) were stored. */
        ANALYZED,
        /** A completed analysis already existed and was reused (Requirement 6.11). */
        SKIPPED,
        /** Analysis is not available for this pair (Requirement 6.7, 6.8). */
        UNAVAILABLE
    }

    /**
     * Analyses every criterion of the submission's rubric.
     *
     * @param submissionId    submission to analyse
     * @param force           re-analyse criteria that already completed, marking prior suggestions
     *                        stale
     * @param progressCounter called with the running count of completed criteria, for job progress
     */
    public AnalysisSummary analyzeSubmission(UUID submissionId, boolean force,
                                             IntConsumer progressCounter) {
        MatchPersistenceService.AnalysisContext context =
                persistence.loadContext(submissionId, MAX_ANALYZED_CHARS).orElse(null);
        if (context == null) {
            return new AnalysisSummary(0, 0, 0);
        }

        if (context.text() == null) {
            // No extracted text at all. Every criterion is recorded as unavailable rather than left
            // empty, so the marking view shows the extraction problem instead of the misleading
            // "no evidence found for this criterion".
            context.criteria().forEach(criterion -> persistence.markUnavailable(
                    submissionId, criterion.getId(),
                    "No extracted text is available for this submission"));
            return new AnalysisSummary(0, 0, context.criteria().size());
        }

        int analyzed = 0;
        int skipped = 0;
        int unavailable = 0;
        int done = 0;

        for (Criterion criterion : context.criteria()) {
            MDC.put("criterionId", criterion.getId().toString());
            try {
                switch (analyzeOne(submissionId, criterion, context.text(), force)) {
                    case ANALYZED -> analyzed++;
                    case SKIPPED -> skipped++;
                    case UNAVAILABLE -> unavailable++;
                }
            } finally {
                MDC.remove("criterionId");
            }
            progressCounter.accept(++done);
        }
        return new AnalysisSummary(analyzed, skipped, unavailable);
    }

    /**
     * Re-analyses a single criterion, discarding the previous generation (Requirement 6.14).
     *
     * @return the outcome for that criterion
     */
    public Outcome reanalyzeCriterion(UUID submissionId, UUID criterionId) {
        MatchPersistenceService.AnalysisContext context =
                persistence.loadContext(submissionId, MAX_ANALYZED_CHARS).orElse(null);
        if (context == null) {
            return Outcome.UNAVAILABLE;
        }
        Criterion criterion = context.criteria().stream()
                .filter(c -> c.getId().equals(criterionId))
                .findFirst()
                .orElse(null);
        if (criterion == null) {
            return Outcome.UNAVAILABLE;
        }
        if (context.text() == null) {
            persistence.markUnavailable(submissionId, criterionId,
                    "No extracted text is available for this submission");
            return Outcome.UNAVAILABLE;
        }
        return analyzeOne(submissionId, criterion, context.text(), true);
    }

    private Outcome analyzeOne(UUID submissionId, Criterion criterion, String text, boolean force) {
        UUID criterionId = criterion.getId();

        if (!force) {
            CriterionAnalysis existing = persistence.findState(submissionId, criterionId).orElse(null);
            if (existing != null && existing.getState() == AnalysisState.COMPLETE) {
                return Outcome.SKIPPED;
            }
            if (existing != null && existing.getState() == AnalysisState.UNAVAILABLE) {
                // This pair already exhausted its retry budget. Only an explicit re-analysis tries
                // again, so a batch job does not keep paying for a criterion that reliably fails.
                return Outcome.UNAVAILABLE;
            }
        }

        persistence.markInProgress(submissionId, criterionId, text.length());

        try {
            List<CandidateMatch> matches = matchEngine.findMatches(criterion, text);
            persistence.storeMatches(submissionId, criterionId, matches, text.length());
            return Outcome.ANALYZED;
        } catch (BedrockUnavailableException e) {
            // BedrockJsonClient already exhausted its attempts, so this is terminal for this
            // criterion until the TA asks for a re-analysis.
            log.warn("Criterion {} of submission {} marked analysis-unavailable: {}",
                    criterionId, submissionId, e.getMessage());
            persistence.markUnavailable(submissionId, criterionId, e.getMessage());
            return Outcome.UNAVAILABLE;
        } catch (RuntimeException e) {
            log.error("Unexpected failure analysing criterion {} of submission {}",
                    criterionId, submissionId, e);
            persistence.markUnavailable(submissionId, criterionId, "Analysis failed unexpectedly");
            return Outcome.UNAVAILABLE;
        }
    }

}
