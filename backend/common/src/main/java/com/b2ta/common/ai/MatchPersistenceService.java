package com.b2ta.common.ai;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionAnalysis;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.SuggestedMatch;
import com.b2ta.common.entity.enums.AnalysisState;
import com.b2ta.common.entity.enums.MatchState;
import com.b2ta.common.repository.CriterionAnalysisRepository;
import com.b2ta.common.repository.CriterionRepository;
import com.b2ta.common.repository.SubmissionRepository;
import com.b2ta.common.repository.SuggestedMatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Write side of match analysis (task 5.5).
 *
 * <p>Separate from {@link MatchAnalysisService} because each criterion's result must commit in its
 * own transaction: a Bedrock failure on criterion 7 must not roll back the matches already stored
 * for criteria 1 to 6 (Requirement 6.7). Spring's transaction proxy only applies to calls that cross
 * a bean boundary, so these methods have to live in a different bean than the loop that calls them —
 * a {@code @Transactional} method invoked from within the same class would silently join the caller's
 * transaction instead of starting a new one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchPersistenceService {

    private final SubmissionRepository submissionRepository;
    private final CriterionRepository criterionRepository;
    private final SuggestedMatchRepository suggestedMatchRepository;
    private final CriterionAnalysisRepository analysisRepository;

    /**
     * Replaces the suggestions for one (submission, criterion) pair.
     *
     * <p>Previous suggestions are marked stale rather than deleted: a TA may already have confirmed
     * or rejected some of them, and those decisions are referenced by {@code confirmed_match} rows
     * through {@code source_match_id}. Marking them stale stops them being served while keeping the
     * history intact (Requirement 6.14).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeMatches(UUID submissionId, UUID criterionId,
                             List<CandidateMatch> matches, int analyzedCharCount) {
        suggestedMatchRepository.markStale(submissionId, criterionId);

        Submission submission = submissionRepository.getReferenceById(submissionId);
        Criterion criterion = criterionRepository.getReferenceById(criterionId);

        List<SuggestedMatch> rows = new ArrayList<>(matches.size());
        for (CandidateMatch match : matches) {
            rows.add(SuggestedMatch.builder()
                    .submission(submission)
                    .criterion(criterion)
                    .passageStart(match.start())
                    .passageEnd(match.end())
                    .rationale(match.rationale())
                    .confidence(match.confidence())
                    .matchState(MatchState.PENDING)
                    .isStale(false)
                    .build());
        }
        suggestedMatchRepository.saveAll(rows);

        upsert(submissionId, criterionId, AnalysisState.COMPLETE, null, analyzedCharCount, false);
        log.info("Stored {} suggested matches for criterion {} of submission {}",
                rows.size(), criterionId, submissionId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInProgress(UUID submissionId, UUID criterionId, int analyzedCharCount) {
        upsert(submissionId, criterionId, AnalysisState.IN_PROGRESS, null, analyzedCharCount, false);
    }

    /** Records that analysis is unavailable for this pair (Requirement 6.7, 6.8). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnavailable(UUID submissionId, UUID criterionId, String reason) {
        upsert(submissionId, criterionId, AnalysisState.UNAVAILABLE, truncate(reason), null, true);
    }

    @Transactional(readOnly = true)
    public Optional<CriterionAnalysis> findState(UUID submissionId, UUID criterionId) {
        return analysisRepository.findBySubmissionIdAndCriterionId(submissionId, criterionId);
    }

    /**
     * Reads the criteria and analysable text for a submission, then commits.
     *
     * <p>Lives here rather than in {@link MatchAnalysisService} because the read needs a transaction
     * (the submission's session and the criteria's levels are lazy) but must not stay open: the caller
     * then makes one Bedrock call per chunk per criterion, and holding a database connection across
     * minutes of model latency would exhaust the pool under a batch.
     *
     * @return the loaded context, or empty when the submission or the rubric is absent
     */
    @Transactional(readOnly = true)
    public Optional<AnalysisContext> loadContext(UUID submissionId, int maxAnalyzedChars) {
        Submission submission = submissionRepository.findById(submissionId).orElse(null);
        if (submission == null) {
            log.warn("Analysis requested for unknown submission {}", submissionId);
            return Optional.empty();
        }
        UUID sessionId = submission.getSession().getId();
        List<Criterion> criteria = criterionRepository.findBySessionIdWithLevels(sessionId);
        if (criteria.isEmpty()) {
            log.warn("Session {} has no criteria; nothing to analyse", sessionId);
            return Optional.empty();
        }

        String text = submission.getExtractedText();
        if (text != null && !text.isBlank() && text.length() > maxAnalyzedChars) {
            log.info("Submission {} is {} characters; analysing the first {}",
                    submissionId, text.length(), maxAnalyzedChars);
            text = text.substring(0, maxAnalyzedChars);
        } else if (text != null && text.isBlank()) {
            text = null;
        }
        return Optional.of(new AnalysisContext(criteria, text));
    }

    /**
     * Criteria plus the analysable text for one submission.
     *
     * @param criteria the rubric's criteria with their performance levels loaded
     * @param text     the text to analyse, or null when the submission has none
     */
    public record AnalysisContext(List<Criterion> criteria, String text) {
    }

    private void upsert(UUID submissionId, UUID criterionId, AnalysisState state,
                        @Nullable String failureReason, @Nullable Integer analyzedCharCount,
                        boolean incrementFailures) {
        CriterionAnalysis analysis = analysisRepository
                .findBySubmissionIdAndCriterionId(submissionId, criterionId)
                .orElseGet(() -> CriterionAnalysis.builder()
                        .submission(submissionRepository.getReferenceById(submissionId))
                        .criterion(criterionRepository.getReferenceById(criterionId))
                        .failureCount((short) 0)
                        .build());

        analysis.setState(state);
        analysis.setFailureReason(failureReason);
        if (analyzedCharCount != null) {
            analysis.setAnalyzedCharCount(analyzedCharCount);
        }
        if (incrementFailures) {
            short current = analysis.getFailureCount() == null ? 0 : analysis.getFailureCount();
            analysis.setFailureCount((short) (current + 1));
        } else if (state == AnalysisState.COMPLETE) {
            analysis.setFailureCount((short) 0);
        }
        analysisRepository.save(analysis);
    }

    private String truncate(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Analysis unavailable";
        }
        return reason.length() > 500 ? reason.substring(0, 500) : reason;
    }
}
