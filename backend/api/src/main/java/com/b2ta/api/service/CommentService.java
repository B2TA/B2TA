package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.ai.BedrockUnavailableException;
import com.b2ta.common.ai.CommentAssistant;
import com.b2ta.common.dto.comment.CommentSuggestRequest;
import com.b2ta.common.dto.comment.CommentSuggestResponse;
import com.b2ta.common.entity.ConfirmedMatch;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionScore;
import com.b2ta.common.entity.GradingRecord;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.repository.ConfirmedMatchRepository;
import com.b2ta.common.repository.CriterionRepository;
import com.b2ta.common.repository.GradingRecordRepository;
import com.b2ta.common.score.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the Comment_Assistant input from stored grading state and returns snippets (task 5.9).
 *
 * <p>The input is assembled from the database rather than from the request body: the snippets have to
 * reflect what has actually been recorded, and a client-supplied summary of the scores would let an
 * unsaved draft produce feedback that does not match the saved record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final TenantGuard tenantGuard;
    private final CriterionRepository criterionRepository;
    private final GradingRecordRepository gradingRecordRepository;
    private final ConfirmedMatchRepository confirmedMatchRepository;
    private final ScoreCalculator scoreCalculator;
    private final CommentAssistant commentAssistant;

    @Transactional(readOnly = true)
    public CommentSuggestResponse suggest(TaPrincipal ta, UUID sessionId, UUID submissionId,
                                          CommentSuggestRequest request) {
        Submission submission = tenantGuard.requireSubmission(ta, sessionId, submissionId);
        List<Criterion> criteria = criterionRepository.findBySessionIdWithLevels(sessionId);

        GradingRecord record = gradingRecordRepository.findBySubmissionId(submissionId)
                .orElse(null);
        List<CriterionScore> scores = record == null ? List.of() : record.getCriterionScores();

        UUID onlyCriterion = request == null ? null : request.getCriterionId();
        List<CommentAssistant.ScoredCriterion> scored =
                buildScoredCriteria(criteria, scores, onlyCriterion);

        if (scored.isEmpty()) {
            // Requirement 12.8: without at least one level selection there is nothing to base
            // feedback on, so the request is blocked rather than answered with generic text.
            throw ApiException.badRequest(ErrorCode.NO_LEVELS_SELECTED,
                    "Select at least one performance level before requesting comment suggestions");
        }

        List<CommentAssistant.Evidence> evidence = buildEvidence(submission, criteria,
                onlyCriterion);

        try {
            List<String> snippets = commentAssistant.suggest(scored, evidence);
            log.info("Generated {} comment suggestions for submission {}",
                    snippets.size(), submissionId);
            return CommentSuggestResponse.ofAiSnippets(snippets);
        } catch (BedrockUnavailableException e) {
            // The client keeps every character the TA has typed (Requirement 12.6); the API's part is
            // to report a reason and a status the SPA can turn into a retry control.
            log.warn("Comment suggestion failed for submission {}: {}", submissionId, e.getMessage());
            if (e.isTimeout()) {
                throw ApiException.gatewayTimeout(ErrorCode.COMMENT_GENERATION_TIMEOUT,
                        "The comment assistant did not respond in time. Your feedback is unchanged.");
            }
            throw ApiException.unprocessable(ErrorCode.COMMENT_GENERATION_FAILED,
                    "The comment assistant could not generate suggestions. Your feedback is "
                            + "unchanged.");
        }
    }

    /** Criteria that carry a level selection or an override, which is what feedback is grounded in. */
    private List<CommentAssistant.ScoredCriterion> buildScoredCriteria(
            List<Criterion> criteria, List<CriterionScore> scores, UUID onlyCriterion) {

        Map<UUID, CriterionScore> byCriterion = scores.stream()
                .filter(score -> score.getCriterion() != null)
                .collect(Collectors.toMap(score -> score.getCriterion().getId(),
                        Function.identity(), (first, second) -> first));

        List<CommentAssistant.ScoredCriterion> result = new ArrayList<>();
        for (Criterion criterion : criteria) {
            if (onlyCriterion != null && !onlyCriterion.equals(criterion.getId())) {
                continue;
            }
            CriterionScore score = byCriterion.get(criterion.getId());
            BigDecimal awarded = scoreCalculator.awardedPoints(score);
            if (awarded == null) {
                continue;
            }
            result.add(CommentAssistant.ScoredCriterion.builder()
                    .title(criterion.getTitle())
                    .selectedLevelLabel(score.getSelectedLevel() == null
                            ? null : score.getSelectedLevel().getLabel())
                    .selectedLevelDescription(score.getSelectedLevel() == null
                            ? null : score.getSelectedLevel().getDescription())
                    .awardedPoints(awarded)
                    .maxPoints(criterion.getMaxPoints())
                    .build());
        }
        return result;
    }

    /** Confirmed matches, resolved to the passage text they point at. */
    private List<CommentAssistant.Evidence> buildEvidence(Submission submission,
                                                          List<Criterion> criteria,
                                                          UUID onlyCriterion) {
        String text = submission.getExtractedText();
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> titles = criteria.stream()
                .collect(Collectors.toMap(Criterion::getId, Criterion::getTitle));

        List<CommentAssistant.Evidence> evidence = new ArrayList<>();
        for (ConfirmedMatch match : confirmedMatchRepository.findBySubmissionId(submission.getId())) {
            UUID criterionId = match.getCriterion().getId();
            if (onlyCriterion != null && !onlyCriterion.equals(criterionId)) {
                continue;
            }
            // Offsets were validated on write, but the text can have been re-extracted since, so
            // clamping here avoids an out-of-bounds read on a stale range.
            int start = Math.max(0, Math.min(match.getPassageStart(), text.length()));
            int end = Math.max(start, Math.min(match.getPassageEnd(), text.length()));
            if (end == start) {
                continue;
            }
            evidence.add(CommentAssistant.Evidence.builder()
                    .criterionTitle(titles.getOrDefault(criterionId, "Criterion"))
                    .passageText(text.substring(start, end))
                    .rationale(match.getRationale())
                    .build());
        }
        return evidence;
    }
}
