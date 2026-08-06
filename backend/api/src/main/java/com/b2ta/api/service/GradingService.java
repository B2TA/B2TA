package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.dto.grading.CriterionScoreDto;
import com.b2ta.common.dto.grading.GradingRecordResponse;
import com.b2ta.common.dto.grading.SaveGradingRecordRequest;
import com.b2ta.common.dto.match.ConfirmedMatchDto;
import com.b2ta.common.entity.ConfirmedMatch;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionScore;
import com.b2ta.common.entity.GradingRecord;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.SuggestedMatch;
import com.b2ta.common.entity.enums.MatchOrigin;
import com.b2ta.common.entity.enums.MatchState;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.repository.ConfirmedMatchRepository;
import com.b2ta.common.repository.CriterionAnalysisRepository;
import com.b2ta.common.repository.CriterionRepository;
import com.b2ta.common.repository.GradingRecordRepository;
import com.b2ta.common.repository.GradingSessionRepository;
import com.b2ta.common.repository.SubmissionRepository;
import com.b2ta.common.repository.SuggestedMatchRepository;
import com.b2ta.common.score.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads and saves grading records (task 5.7, Requirements 14.1-14.6, 14.10-14.12).
 *
 * <p>The save is a single transaction covering the record, all criterion scores, and all confirmed
 * matches. A partial save is the one outcome a grader cannot recover from without re-reading the
 * document: they would see a saved indicator while some of their level selections had been dropped.
 * Either the whole record lands or none of it does.
 *
 * <p>Scores and confirmed matches are replaced wholesale rather than merged. The client always holds
 * the complete state of the open marking view, so a replacement is the only interpretation that makes
 * "unselect a level" and "remove a match" expressible; a merge would make removals impossible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingService {

    /** Longest text range a TA may associate with a criterion by hand (Requirement 10.3, 10.8). */
    public static final int MAX_MANUAL_PASSAGE_LENGTH = 5000;

    /** Rationale recorded for a TA-authored match (Requirement 10.3). */
    private static final String TA_AUTHORED_RATIONALE = "TA-authored";

    private final TenantGuard tenantGuard;
    private final SubmissionRepository submissionRepository;
    private final CriterionRepository criterionRepository;
    private final GradingRecordRepository gradingRecordRepository;
    private final SuggestedMatchRepository suggestedMatchRepository;
    private final ConfirmedMatchRepository confirmedMatchRepository;
    private final CriterionAnalysisRepository analysisRepository;
    private final GradingSessionRepository sessionRepository;
    private final ScoreCalculator scoreCalculator;
    private final GradingMapper mapper;

    // ---------------------------------------------------------------- load

    @Transactional(readOnly = true)
    public GradingRecordResponse load(TaPrincipal ta, UUID sessionId, UUID submissionId) {
        Submission submission = tenantGuard.requireSubmission(ta, sessionId, submissionId);
        List<Criterion> criteria = criterionRepository.findBySessionIdWithLevels(sessionId);

        GradingRecord record = gradingRecordRepository.findBySubmissionId(submissionId).orElse(null);
        List<CriterionScore> scores = record == null ? List.of() : record.getCriterionScores();

        ScoreCalculator.ScoreSummary summary = scoreCalculator.summarize(criteria, scores);

        // Only unconfirmed, non-stale suggestions are returned. A confirmed suggestion is already
        // represented by its confirmed_match row, and a rejected one must not reappear (Req 10.2).
        List<SuggestedMatch> suggestions = suggestedMatchRepository.findBySubmissionId(submissionId)
                .stream()
                .filter(match -> match.getMatchState() == MatchState.PENDING)
                .toList();

        int batchSize = submissionRepository.countBySessionId(sessionId);

        return GradingRecordResponse.builder()
                .id(record == null ? null : record.getId())
                .submissionId(submissionId)
                .studentDisplayName(submission.getStudentDisplayName())
                .overallFeedback(record == null || record.getOverallFeedback() == null
                        ? "" : record.getOverallFeedback())
                .savedAt(record == null ? null : record.getSavedAt())
                .criterionScores(scores.stream().map(mapper::toDto).toList())
                .suggestedMatches(suggestions.stream().map(mapper::toDto).toList())
                .confirmedMatches(confirmedMatchRepository.findBySubmissionId(submissionId).stream()
                        .map(mapper::toDto).toList())
                .criterionAnalysis(analysisRepository.findBySubmissionId(submissionId).stream()
                        .map(mapper::toDto).toList())
                .extractedText(submission.getExtractedText())
                .extractionStatus(submission.getExtractionStatus())
                .extractionFailureReason(submission.getExtractionFailureReason())
                .isOversized(submission.getIsOversized())
                .position(submission.getPosition() == null ? null : submission.getPosition() + 1)
                .batchSize(batchSize)
                .totalScore(summary.total())
                .maxScore(summary.maxTotal())
                .unscoredCriterionCount(summary.unscoredCount())
                .build();
    }

    // ---------------------------------------------------------------- save

    @Transactional
    public GradingRecordResponse save(TaPrincipal ta, UUID sessionId, UUID submissionId,
                                      SaveGradingRecordRequest request) {
        Submission submission = tenantGuard.requireSubmission(ta, sessionId, submissionId);
        Map<UUID, Criterion> criteria = criterionRepository.findBySessionIdWithLevels(sessionId)
                .stream()
                .collect(Collectors.toMap(Criterion::getId, Function.identity()));

        if (criteria.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.RUBRIC_NOT_READY,
                    "This session has no rubric criteria to grade against");
        }

        GradingRecord record = gradingRecordRepository.findBySubmissionId(submissionId)
                .orElseGet(() -> GradingRecord.builder()
                        .submission(submission)
                        .overallFeedback("")
                        .criterionScores(new ArrayList<>())
                        .build());

        record.setOverallFeedback(request.getOverallFeedback() == null
                ? "" : request.getOverallFeedback());

        applyScores(record, criteria, request.getCriterionScores());
        record.setSavedAt(Instant.now());
        GradingRecord saved = gradingRecordRepository.save(record);

        replaceConfirmedMatches(submission, criteria, request.getConfirmedMatches());

        // Captured before the bulk updates below: those clear the persistence context, after which
        // `saved` is detached and its lazy score collection can no longer be read.
        UUID savedId = saved.getId();
        int scoreCount = saved.getCriterionScores().size();

        // Any stored grade changed, so a prior review confirmation no longer describes what would be
        // exported and has to be earned again (Requirement 15.11).
        sessionRepository.clearReviewConfirmation(sessionId, Instant.now());
        sessionRepository.touch(sessionId, Instant.now());

        log.info("Saved grading record {} for submission {} ({} criterion scores)",
                savedId, submissionId, scoreCount);

        return load(ta, sessionId, submissionId);
    }

    /**
     * Replaces the criterion scores of a record in place.
     *
     * <p>The existing collection is mutated rather than swapped so {@code orphanRemoval} deletes the
     * rows that are gone; assigning a new list would leave Hibernate unable to track the removals.
     */
    private void applyScores(GradingRecord record, Map<UUID, Criterion> criteria,
                             List<CriterionScoreDto> requested) {
        Map<UUID, CriterionScore> existing = record.getCriterionScores().stream()
                .filter(score -> score.getCriterion() != null)
                .collect(Collectors.toMap(score -> score.getCriterion().getId(),
                        Function.identity(), (first, second) -> first, HashMap::new));

        List<CriterionScore> resolved = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (CriterionScoreDto dto : requested == null ? List.<CriterionScoreDto>of() : requested) {
            Criterion criterion = criteria.get(dto.getCriterionId());
            if (criterion == null) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Criterion " + dto.getCriterionId() + " does not belong to this session")
                        .with("criterionId", dto.getCriterionId());
            }
            if (!seen.add(criterion.getId())) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Criterion " + criterion.getId() + " appears more than once");
            }

            PerformanceLevel level = resolveLevel(criterion, dto.getSelectedLevelId());

            String overrideProblem = scoreCalculator.validateOverride(criterion,
                    dto.getOverridePoints());
            if (overrideProblem != null) {
                throw ApiException.badRequest(ErrorCode.INVALID_OVERRIDE, overrideProblem)
                        .with("criterionId", criterion.getId())
                        .with("maxPoints", criterion.getMaxPoints());
            }

            CriterionScore score = existing.get(criterion.getId());
            if (score == null) {
                score = CriterionScore.builder()
                        .gradingRecord(record)
                        .criterion(criterion)
                        .build();
            }
            score.setSelectedLevel(level);
            score.setOverridePoints(dto.getOverridePoints());
            score.setCriterionFeedback(dto.getCriterionFeedback() == null
                    ? "" : dto.getCriterionFeedback());
            resolved.add(score);
        }

        record.getCriterionScores().clear();
        record.getCriterionScores().addAll(resolved);
    }

    /** Resolves and validates a selected level against its own criterion. */
    private PerformanceLevel resolveLevel(Criterion criterion, UUID levelId) {
        if (levelId == null) {
            return null;
        }
        return criterion.getPerformanceLevels().stream()
                .filter(level -> level.getId().equals(levelId))
                .findFirst()
                // A level id from a different criterion would otherwise award that criterion's
                // points here, silently producing a score the rubric does not allow.
                .orElseThrow(() -> ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                                "Performance level " + levelId + " does not belong to criterion "
                                        + criterion.getId())
                        .with("criterionId", criterion.getId()));
    }

    /**
     * Replaces the confirmed matches of a submission.
     *
     * <p>A confirmed match that came from a suggestion and is absent from the new set counts as a
     * rejection of that suggestion, so it is not offered again (Requirement 10.4). A TA-authored match
     * that is absent is simply deleted, because there is no suggestion to remember.
     */
    private void replaceConfirmedMatches(Submission submission, Map<UUID, Criterion> criteria,
                                         List<ConfirmedMatchDto> requested) {
        if (requested == null) {
            // Absent field means "not managed by this request"; the dedicated match endpoints own
            // the set in that case. An empty list, by contrast, does clear it.
            return;
        }

        List<ConfirmedMatch> existing = confirmedMatchRepository
                .findBySubmissionId(submission.getId());
        Map<String, ConfirmedMatch> existingByKey = existing.stream()
                .collect(Collectors.toMap(this::passageKey, Function.identity(),
                        (first, second) -> first, HashMap::new));

        Set<String> keptKeys = new HashSet<>();
        List<ConfirmedMatch> toSave = new ArrayList<>();

        for (ConfirmedMatchDto dto : requested) {
            Criterion criterion = criteria.get(dto.getCriterionId());
            if (criterion == null) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Criterion " + dto.getCriterionId() + " does not belong to this session");
            }
            validatePassageRange(submission, dto.getPassageStart(), dto.getPassageEnd());

            String key = passageKey(criterion.getId(), dto.getPassageStart(), dto.getPassageEnd());
            if (!keptKeys.add(key)) {
                // Requirement 10.5: at most one confirmed match per criterion per passage.
                continue;
            }
            ConfirmedMatch match = existingByKey.get(key);
            if (match == null) {
                match = ConfirmedMatch.builder()
                        .submission(submission)
                        .criterion(criterion)
                        .passageStart(dto.getPassageStart())
                        .passageEnd(dto.getPassageEnd())
                        .rationale(rationaleOrDefault(dto))
                        .confidence(dto.getConfidence())
                        .origin(dto.getSourceMatchId() == null
                                ? MatchOrigin.MANUAL : MatchOrigin.AI_SUGGESTED)
                        .sourceMatchId(dto.getSourceMatchId())
                        .build();
                toSave.add(match);
            }
        }

        List<ConfirmedMatch> removed = existing.stream()
                .filter(match -> !keptKeys.contains(passageKey(match)))
                .toList();

        for (ConfirmedMatch match : removed) {
            if (match.getSourceMatchId() != null) {
                markSuggestionRejected(match.getSourceMatchId());
            }
        }
        confirmedMatchRepository.deleteAll(removed);
        confirmedMatchRepository.saveAll(toSave);

        // Confirming a suggestion in this payload also updates the suggestion's own state, so it is
        // not presented again on reopen.
        for (ConfirmedMatch match : toSave) {
            if (match.getSourceMatchId() != null) {
                markSuggestionState(match.getSourceMatchId(), MatchState.CONFIRMED);
            }
        }
    }

    private String rationaleOrDefault(ConfirmedMatchDto dto) {
        if (dto.getRationale() == null || dto.getRationale().isBlank()) {
            return TA_AUTHORED_RATIONALE;
        }
        return dto.getRationale().length() > 300
                ? dto.getRationale().substring(0, 300)
                : dto.getRationale();
    }

    // ---------------------------------------------------------------- matches

    /** Confirms a suggested match (Requirement 10.1). */
    @Transactional
    public ConfirmedMatchDto confirmMatch(TaPrincipal ta, UUID sessionId, UUID submissionId,
                                          UUID matchId) {
        tenantGuard.requireSubmission(ta, sessionId, submissionId);
        SuggestedMatch suggestion = suggestedMatchRepository
                .findByIdAndSubmissionIdAndTaId(matchId, submissionId, ta.taId())
                .orElseThrow(() -> ApiException.notFound("Suggested match " + matchId + " was not found"));

        suggestion.setMatchState(MatchState.CONFIRMED);
        suggestedMatchRepository.save(suggestion);

        ConfirmedMatch existing = confirmedMatchRepository.findByPassage(submissionId,
                suggestion.getCriterion().getId(),
                suggestion.getPassageStart(), suggestion.getPassageEnd()).orElse(null);
        if (existing != null) {
            // Idempotent: confirming twice yields the same confirmed match rather than a duplicate
            // that would violate uq_confirmed_match_passage.
            return mapper.toDto(existing);
        }

        ConfirmedMatch confirmed = confirmedMatchRepository.save(ConfirmedMatch.builder()
                .submission(suggestion.getSubmission())
                .criterion(suggestion.getCriterion())
                .passageStart(suggestion.getPassageStart())
                .passageEnd(suggestion.getPassageEnd())
                .rationale(suggestion.getRationale())
                .confidence(suggestion.getConfidence())
                .origin(MatchOrigin.AI_SUGGESTED)
                .sourceMatchId(suggestion.getId())
                .build());

        sessionRepository.clearReviewConfirmation(sessionId, Instant.now());
        return mapper.toDto(confirmed);
    }

    /** Rejects a suggested match (Requirement 10.2). */
    @Transactional
    public void rejectMatch(TaPrincipal ta, UUID sessionId, UUID submissionId, UUID matchId) {
        tenantGuard.requireSubmission(ta, sessionId, submissionId);
        SuggestedMatch suggestion = suggestedMatchRepository
                .findByIdAndSubmissionIdAndTaId(matchId, submissionId, ta.taId())
                .orElseThrow(() -> ApiException.notFound("Suggested match " + matchId + " was not found"));

        suggestion.setMatchState(MatchState.REJECTED);
        suggestedMatchRepository.save(suggestion);

        // A previously confirmed match derived from this suggestion goes away with it, so the
        // highlight disappears and the rejection is the state that survives (Requirement 10.7).
        confirmedMatchRepository.findByPassage(submissionId, suggestion.getCriterion().getId(),
                        suggestion.getPassageStart(), suggestion.getPassageEnd())
                .ifPresent(confirmedMatchRepository::delete);

        sessionRepository.clearReviewConfirmation(sessionId, Instant.now());
    }

    /** Creates a TA-authored match from a selected text range (Requirement 10.3). */
    @Transactional
    public ConfirmedMatchDto createManualMatch(TaPrincipal ta, UUID sessionId, UUID submissionId,
                                               UUID criterionId, Integer start, Integer end,
                                               String rationale) {
        Submission submission = tenantGuard.requireSubmission(ta, sessionId, submissionId);
        Criterion criterion = tenantGuard.requireCriterion(ta, sessionId, criterionId);

        validatePassageRange(submission, start, end);
        validateManualPassageContent(submission, start, end);

        confirmedMatchRepository.findByPassage(submissionId, criterionId, start, end)
                .ifPresent(existing -> {
                    throw ApiException.conflict(ErrorCode.PASSAGE_ALREADY_ASSOCIATED,
                            "That passage is already associated with '" + criterion.getTitle() + "'");
                });

        ConfirmedMatch created = confirmedMatchRepository.save(ConfirmedMatch.builder()
                .submission(submission)
                .criterion(criterion)
                .passageStart(start)
                .passageEnd(end)
                .rationale(rationale == null || rationale.isBlank()
                        ? TA_AUTHORED_RATIONALE
                        : rationale.substring(0, Math.min(rationale.length(), 300)))
                // Confidence stays null: "not applicable" for a TA-authored match (Req 10.3).
                .confidence(null)
                .origin(MatchOrigin.MANUAL)
                .sourceMatchId(null)
                .build());

        sessionRepository.clearReviewConfirmation(sessionId, Instant.now());
        return mapper.toDto(created);
    }

    /** Removes a confirmed match (Requirement 10.4). */
    @Transactional
    public void deleteConfirmedMatch(TaPrincipal ta, UUID sessionId, UUID submissionId,
                                     UUID confirmedMatchId) {
        tenantGuard.requireSubmission(ta, sessionId, submissionId);
        ConfirmedMatch match = confirmedMatchRepository
                .findByIdAndSubmissionIdAndTaId(confirmedMatchId, submissionId, ta.taId())
                .orElseThrow(() -> ApiException.notFound(
                        "Confirmed match " + confirmedMatchId + " was not found"));

        if (match.getSourceMatchId() != null) {
            // Derived from a suggestion: removing it is a rejection, so the suggestion is not
            // offered again on reopen (Requirement 10.4).
            markSuggestionRejected(match.getSourceMatchId());
        }
        confirmedMatchRepository.delete(match);
        sessionRepository.clearReviewConfirmation(sessionId, Instant.now());
    }

    private void markSuggestionRejected(UUID suggestionId) {
        markSuggestionState(suggestionId, MatchState.REJECTED);
    }

    private void markSuggestionState(UUID suggestionId, MatchState state) {
        suggestedMatchRepository.findById(suggestionId).ifPresent(suggestion -> {
            suggestion.setMatchState(state);
            suggestedMatchRepository.save(suggestion);
        });
    }

    // ---------------------------------------------------------------- validation

    /** Offsets must fall inside the extracted text (Requirement 10.10). */
    private void validatePassageRange(Submission submission, Integer start, Integer end) {
        String text = submission.getExtractedText();
        if (text == null || text.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.NO_EXTRACTED_TEXT,
                    "No extracted text is available for this submission, so passages cannot be "
                            + "associated with criteria");
        }
        if (start == null || end == null || start < 0 || end <= start || end > text.length()) {
            throw ApiException.badRequest(ErrorCode.INVALID_PASSAGE_RANGE,
                            "Passage offsets must satisfy 0 <= start < end <= " + text.length())
                    .with("start", start)
                    .with("end", end)
                    .with("textLength", text.length());
        }
    }

    /** A manual selection must be 1-5000 characters and hold non-whitespace (Requirement 10.8). */
    private void validateManualPassageContent(Submission submission, Integer start, Integer end) {
        int length = end - start;
        if (length > MAX_MANUAL_PASSAGE_LENGTH) {
            throw ApiException.badRequest(ErrorCode.INVALID_PASSAGE_RANGE,
                            "A selection must be between 1 and " + MAX_MANUAL_PASSAGE_LENGTH
                                    + " characters")
                    .with("selectedLength", length)
                    .with("maxLength", MAX_MANUAL_PASSAGE_LENGTH);
        }
        String selected = submission.getExtractedText().substring(start, end);
        if (selected.isBlank()) {
            throw ApiException.badRequest(ErrorCode.INVALID_PASSAGE_RANGE,
                    "A selection must contain at least one non-whitespace character");
        }
    }

    private String passageKey(ConfirmedMatch match) {
        return passageKey(match.getCriterion().getId(), match.getPassageStart(),
                match.getPassageEnd());
    }

    private String passageKey(UUID criterionId, Integer start, Integer end) {
        return criterionId + ":" + start + ":" + end;
    }
}
