package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.dto.grading.CriterionScoreDto;
import com.b2ta.common.dto.grading.SaveGradingRecordRequest;
import com.b2ta.common.entity.ConfirmedMatch;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.enums.ExtractionStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 5.7 — the validation rules that protect a grading record from a value the rubric does not
 * allow (Requirements 10.3, 10.8, 10.9, 11.4, 11.5).
 *
 * <p>Every case here asserts that the rejected request changes nothing: a bad override or a bad
 * passage range must leave the stored record exactly as it was, because a partially applied save is
 * worse than a rejected one.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradingValidationTest {

    private static final UUID TA_ID = UUID.randomUUID();
    private static final String TEXT =
            "The argument develops steadily across three sections and closes with a clear claim.";

    @Mock private TenantGuard tenantGuard;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private CriterionRepository criterionRepository;
    @Mock private GradingRecordRepository gradingRecordRepository;
    @Mock private SuggestedMatchRepository suggestedMatchRepository;
    @Mock private ConfirmedMatchRepository confirmedMatchRepository;
    @Mock private CriterionAnalysisRepository analysisRepository;
    @Mock private GradingSessionRepository sessionRepository;

    private GradingService gradingService;
    private TaPrincipal ta;
    private UUID sessionId;
    private UUID submissionId;
    private Criterion criterion;
    private PerformanceLevel fullMarks;
    private Submission submission;

    @BeforeEach
    void setUp() {
        gradingService = new GradingService(
                tenantGuard, submissionRepository, criterionRepository, gradingRecordRepository,
                suggestedMatchRepository, confirmedMatchRepository, analysisRepository,
                sessionRepository, new ScoreCalculator(), new GradingMapper());

        ta = new TaPrincipal(TA_ID, "sub", "ta@example.com");
        sessionId = UUID.randomUUID();
        submissionId = UUID.randomUUID();

        criterion = Criterion.builder()
                .id(UUID.randomUUID())
                .title("Argument")
                .maxPoints(new BigDecimal("10.00"))
                .displayColor("#1F77B4")
                .position((short) 0)
                .performanceLevels(new ArrayList<>())
                .build();
        fullMarks = PerformanceLevel.builder()
                .id(UUID.randomUUID())
                .criterion(criterion)
                .label("Excellent")
                .points(new BigDecimal("10.00"))
                .position((short) 0)
                .build();
        criterion.getPerformanceLevels().add(fullMarks);

        submission = Submission.builder()
                .id(submissionId)
                .extractedText(TEXT)
                .extractionStatus(ExtractionStatus.COMPLETED)
                .position(0)
                .build();

        when(tenantGuard.requireSubmission(any(), any(), any())).thenReturn(submission);
        when(tenantGuard.requireCriterion(any(), any(), any())).thenReturn(criterion);
        when(criterionRepository.findBySessionIdWithLevels(sessionId)).thenReturn(List.of(criterion));
        when(gradingRecordRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());
        when(confirmedMatchRepository.findByPassage(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    // --- Override bounds (Requirement 11.4, 11.5) ---

    @Test
    void rejectsAnOverrideAboveTheCriterionMaximum() {
        ApiException error = saveExpectingFailure(score(new BigDecimal("10.01"), null));

        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_OVERRIDE);
        // The permitted range is reported so the UI can state it rather than guessing.
        assertThat(error.getDetails()).containsEntry("maxPoints", new BigDecimal("10.00"));
        verify(gradingRecordRepository, never()).save(any());
    }

    @Test
    void rejectsANegativeOverride() {
        ApiException error = saveExpectingFailure(score(new BigDecimal("-1.00"), null));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_OVERRIDE);
        verify(gradingRecordRepository, never()).save(any());
    }

    @Test
    void rejectsALevelBelongingToAnotherCriterion() {
        ApiException error = saveExpectingFailure(score(null, UUID.randomUUID()));

        // Accepting it would award this criterion the other criterion's points, producing a score
        // the rubric does not permit.
        assertThat(error.getStatus()).isEqualTo(400);
        assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(gradingRecordRepository, never()).save(any());
    }

    @Test
    void rejectsACriterionFromAnotherSession() {
        CriterionScoreDto foreign = CriterionScoreDto.builder()
                .criterionId(UUID.randomUUID())
                .build();

        ApiException error = saveExpectingFailure(foreign);

        assertThat(error.getStatus()).isEqualTo(400);
        verify(gradingRecordRepository, never()).save(any());
    }

    @Test
    void rejectsTheSameCriterionTwiceInOneRequest() {
        SaveGradingRecordRequest request = SaveGradingRecordRequest.builder()
                .overallFeedback("")
                .criterionScores(List.of(score(null, fullMarks.getId()), score(null, fullMarks.getId())))
                .build();

        ApiException error = catchApiException(
                () -> gradingService.save(ta, sessionId, submissionId, request));

        assertThat(error.getStatus()).isEqualTo(400);
        verify(gradingRecordRepository, never()).save(any());
    }

    @Test
    void rejectsASaveWhenTheSessionHasNoCriteria() {
        when(criterionRepository.findBySessionIdWithLevels(sessionId)).thenReturn(List.of());

        ApiException error = catchApiException(() -> gradingService.save(ta, sessionId, submissionId,
                SaveGradingRecordRequest.builder().overallFeedback("").criterionScores(List.of()).build()));

        assertThat(error.getCode()).isEqualTo(ErrorCode.RUBRIC_NOT_READY);
    }

    // --- Manual match ranges (Requirement 10.3, 10.8, 10.9) ---

    @Test
    void rejectsAPassageRangePastTheEndOfTheText() {
        ApiException error = catchApiException(() -> gradingService.createManualMatch(
                ta, sessionId, submissionId, criterion.getId(), 0, TEXT.length() + 10, null));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PASSAGE_RANGE);
        assertThat(error.getDetails()).containsEntry("textLength", TEXT.length());
        verify(confirmedMatchRepository, never()).save(any());
    }

    @Test
    void rejectsAnInvertedRange() {
        ApiException error = catchApiException(() -> gradingService.createManualMatch(
                ta, sessionId, submissionId, criterion.getId(), 20, 10, null));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PASSAGE_RANGE);
        verify(confirmedMatchRepository, never()).save(any());
    }

    @Test
    void rejectsASelectionOfOnlyWhitespace() {
        Submission spaced = Submission.builder()
                .id(submissionId)
                .extractedText("word     word")
                .extractionStatus(ExtractionStatus.COMPLETED)
                .position(0)
                .build();
        when(tenantGuard.requireSubmission(any(), any(), any())).thenReturn(spaced);

        ApiException error = catchApiException(() -> gradingService.createManualMatch(
                ta, sessionId, submissionId, criterion.getId(), 4, 9, null));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PASSAGE_RANGE);
        verify(confirmedMatchRepository, never()).save(any());
    }

    @Test
    void rejectsASelectionLongerThanTheLimit() {
        String long_text = "a".repeat(GradingService.MAX_MANUAL_PASSAGE_LENGTH + 100);
        Submission big = Submission.builder()
                .id(submissionId)
                .extractedText(long_text)
                .extractionStatus(ExtractionStatus.COMPLETED)
                .position(0)
                .build();
        when(tenantGuard.requireSubmission(any(), any(), any())).thenReturn(big);

        ApiException error = catchApiException(() -> gradingService.createManualMatch(
                ta, sessionId, submissionId, criterion.getId(), 0,
                GradingService.MAX_MANUAL_PASSAGE_LENGTH + 1, null));

        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PASSAGE_RANGE);
        assertThat(error.getDetails()).containsEntry("maxLength", GradingService.MAX_MANUAL_PASSAGE_LENGTH);
    }

    @Test
    void rejectsAPassageAlreadyAssociatedWithTheSameCriterion() {
        when(confirmedMatchRepository.findByPassage(submissionId, criterion.getId(), 4, 12))
                .thenReturn(Optional.of(new ConfirmedMatch()));

        ApiException error = catchApiException(() -> gradingService.createManualMatch(
                ta, sessionId, submissionId, criterion.getId(), 4, 12, null));

        assertThat(error.getStatus()).isEqualTo(409);
        assertThat(error.getCode()).isEqualTo(ErrorCode.PASSAGE_ALREADY_ASSOCIATED);
        verify(confirmedMatchRepository, never()).save(any());
    }

    @Test
    void rejectsMatchCreationWhenThereIsNoExtractedText() {
        Submission failed = Submission.builder()
                .id(submissionId)
                .extractedText(null)
                .extractionStatus(ExtractionStatus.FAILED)
                .position(0)
                .build();
        when(tenantGuard.requireSubmission(any(), any(), any())).thenReturn(failed);

        ApiException error = catchApiException(() -> gradingService.createManualMatch(
                ta, sessionId, submissionId, criterion.getId(), 0, 10, null));

        assertThat(error.getCode()).isEqualTo(ErrorCode.NO_EXTRACTED_TEXT);
    }

    // --- Helpers ---

    private CriterionScoreDto score(BigDecimal override, UUID levelId) {
        return CriterionScoreDto.builder()
                .criterionId(criterion.getId())
                .selectedLevelId(levelId)
                .overridePoints(override)
                .criterionFeedback("")
                .build();
    }

    private ApiException saveExpectingFailure(CriterionScoreDto score) {
        SaveGradingRecordRequest request = SaveGradingRecordRequest.builder()
                .overallFeedback("")
                .criterionScores(List.of(score))
                .build();
        return catchApiException(() -> gradingService.save(ta, sessionId, submissionId, request));
    }

    private ApiException catchApiException(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected an ApiException");
        } catch (ApiException e) {
            return e;
        }
    }
}
