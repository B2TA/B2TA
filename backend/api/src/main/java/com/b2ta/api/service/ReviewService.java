package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.dto.review.ReviewFlag;
import com.b2ta.common.dto.review.ReviewResponse;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionScore;
import com.b2ta.common.entity.GradingRecord;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.enums.ExtractionStatus;
import com.b2ta.common.entity.enums.IdentityStatus;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.repository.CriterionRepository;
import com.b2ta.common.repository.GradingRecordRepository;
import com.b2ta.common.repository.GradingSessionRepository;
import com.b2ta.common.repository.SubmissionRepository;
import com.b2ta.common.score.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pre-export review (task 5.10, Requirements 15.1-15.12).
 *
 * <p>The screen exists to make every grade inspectable in one place before anything leaves the system,
 * so its job is to surface the things a grader would otherwise have to open 150 submissions to notice:
 * unscored criteria, failed extractions, unverified identities, manual overrides.
 *
 * <p>Confirmation is recorded against the session and cleared by any subsequent change to a grading
 * record (Requirement 15.11), which is what stops a confirmation from vouching for numbers that have
 * since moved. The clearing happens in {@link GradingService}, at the point the change is written.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final TenantGuard tenantGuard;
    private final GradingSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;
    private final CriterionRepository criterionRepository;
    private final GradingRecordRepository gradingRecordRepository;
    private final ScoreCalculator scoreCalculator;

    @Transactional(readOnly = true)
    public ReviewResponse buildReview(TaPrincipal ta, UUID sessionId) {
        GradingSession session = tenantGuard.requireSession(ta, sessionId);
        List<Submission> submissions = submissionRepository.findBySessionIdOrderByPosition(sessionId);
        List<Criterion> criteria = criterionRepository.findBySessionIdWithLevels(sessionId);

        // One query for every record and its scores. Reading them per submission would be 150 extra
        // round trips and would not fit the 3-second render budget of Requirement 15.10.
        Map<UUID, GradingRecord> recordsBySubmission = gradingRecordRepository
                .findBySessionIdWithScores(sessionId).stream()
                .collect(Collectors.toMap(record -> record.getSubmission().getId(),
                        Function.identity(), (first, second) -> first));

        Map<UUID, Criterion> criteriaById = criteria.stream()
                .collect(Collectors.toMap(Criterion::getId, Function.identity()));

        List<ReviewResponse.SubmissionSummaryDto> rows = new ArrayList<>(submissions.size());
        int flagged = 0;

        for (Submission submission : submissions) {
            GradingRecord record = recordsBySubmission.get(submission.getId());
            List<CriterionScore> scores = record == null ? List.of() : record.getCriterionScores();
            ScoreCalculator.ScoreSummary summary = scoreCalculator.summarize(criteria, scores);

            Set<ReviewFlag> flags = determineFlags(submission, summary);
            if (!flags.isEmpty()) {
                flagged++;
            }

            rows.add(ReviewResponse.SubmissionSummaryDto.builder()
                    .submissionId(submission.getId())
                    .studentDisplayName(submission.getStudentDisplayName())
                    .position(submission.getPosition() == null ? 0 : submission.getPosition() + 1)
                    .totalPoints(summary.total())
                    .maxPoints(summary.maxTotal())
                    .unscoredCriterionCount(summary.unscoredCount())
                    .overrideCount(summary.overrideCount())
                    .criterionScores(buildScoreSummaries(criteria, criteriaById, scores, summary))
                    .flags(List.copyOf(flags))
                    .build());
        }

        return ReviewResponse.builder()
                .sessionId(sessionId)
                .reviewConfirmedAt(session.getReviewConfirmedAt())
                .totalSubmissions(submissions.size())
                .flaggedCount(flagged)
                .unflaggedCount(submissions.size() - flagged)
                .criteria(criteria.stream()
                        .map(criterion -> ReviewResponse.CriterionHeader.builder()
                                .criterionId(criterion.getId())
                                .title(criterion.getTitle())
                                .maxPoints(criterion.getMaxPoints())
                                .position(criterion.getPosition())
                                .build())
                        .toList())
                .submissions(rows)
                .build();
    }

    private List<ReviewResponse.CriterionScoreSummary> buildScoreSummaries(
            List<Criterion> criteria,
            Map<UUID, Criterion> criteriaById,
            List<CriterionScore> scores,
            ScoreCalculator.ScoreSummary summary) {

        Map<UUID, CriterionScore> byCriterion = scores.stream()
                .filter(score -> score.getCriterion() != null)
                .collect(Collectors.toMap(score -> score.getCriterion().getId(),
                        Function.identity(), (first, second) -> first));

        List<ReviewResponse.CriterionScoreSummary> result = new ArrayList<>(criteria.size());
        for (Criterion criterion : criteria) {
            CriterionScore score = byCriterion.get(criterion.getId());
            result.add(ReviewResponse.CriterionScoreSummary.builder()
                    .criterionId(criterion.getId())
                    .criterionTitle(criteriaById.get(criterion.getId()).getTitle())
                    .points(summary.awardedByCriterion().get(criterion.getId()))
                    .selectedLevelLabel(score == null || score.getSelectedLevel() == null
                            ? null : score.getSelectedLevel().getLabel())
                    .overridden(score != null && score.getOverridePoints() != null)
                    .build());
        }
        return result;
    }

    private Set<ReviewFlag> determineFlags(Submission submission,
                                           ScoreCalculator.ScoreSummary summary) {
        Set<ReviewFlag> flags = EnumSet.noneOf(ReviewFlag.class);

        if (summary.unscoredCount() > 0) {
            flags.add(ReviewFlag.INCOMPLETE_GRADING);
        }
        if (summary.overrideCount() > 0) {
            flags.add(ReviewFlag.MANUAL_OVERRIDES);
        }
        if (submission.getExtractionStatus() == ExtractionStatus.FAILED) {
            flags.add(ReviewFlag.EXTRACTION_FAILED);
        }
        if (Boolean.TRUE.equals(submission.getIsOversized())
                || submission.getExtractionStatus() == ExtractionStatus.OVERSIZED) {
            flags.add(ReviewFlag.OVERSIZED);
        }
        if (submission.getIdentityStatus() == IdentityStatus.UNVERIFIED) {
            flags.add(ReviewFlag.UNVERIFIED_IDENTITY);
        }
        if (submission.getIdentityStatus() == IdentityStatus.DISAMBIGUATION_REQUIRED) {
            flags.add(ReviewFlag.DISAMBIGUATION_REQUIRED);
        }
        return flags;
    }

    /** Records the TA's confirmation of this review (Requirement 15.9). */
    @Transactional
    public ReviewResponse confirm(TaPrincipal ta, UUID sessionId) {
        tenantGuard.requireSession(ta, sessionId);

        if (submissionRepository.countBySessionId(sessionId) == 0) {
            // Requirement 15.12: nothing to review, so there is nothing to confirm and the export
            // stays blocked.
            throw ApiException.badRequest(ErrorCode.EMPTY_SESSION,
                    "This session holds zero submissions, so there is nothing to review");
        }

        Instant confirmedAt = Instant.now();
        // Set on the managed entity rather than through a bulk update: the response is built by
        // re-reading the session below, and a bulk update would leave the copy in the persistence
        // context showing the old value, so the caller would be told the review is still
        // unconfirmed.
        GradingSession session = tenantGuard.requireSession(ta, sessionId);
        session.setReviewConfirmedAt(confirmedAt);
        session.setUpdatedAt(confirmedAt);
        sessionRepository.save(session);

        log.info("Review confirmed for session {} at {}", sessionId, confirmedAt);
        return buildReview(ta, sessionId);
    }

    /**
     * Fails unless the session's review has been confirmed (Requirement 15.1, 16.10).
     *
     * <p>Called by the export service so the gate lives in one place: an export path that forgot to
     * check would let unreviewed grades reach a gradebook.
     */
    @Transactional(readOnly = true)
    public void requireConfirmedReview(TaPrincipal ta, UUID sessionId) {
        GradingSession session = tenantGuard.requireSession(ta, sessionId);

        if (submissionRepository.countBySessionId(sessionId) == 0) {
            throw ApiException.badRequest(ErrorCode.EMPTY_SESSION,
                    "At least one submission is required to export");
        }
        if (session.getReviewConfirmedAt() == null) {
            throw ApiException.conflict(ErrorCode.REVIEW_NOT_CONFIRMED,
                    "Open and confirm the review screen before exporting");
        }
    }

    /** Total awarded points for a submission, used by the export service. */
    BigDecimal totalFor(List<Criterion> criteria, List<CriterionScore> scores) {
        return scoreCalculator.summarize(criteria, scores).total();
    }
}
