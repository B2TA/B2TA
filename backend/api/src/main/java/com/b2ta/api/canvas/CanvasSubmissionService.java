package com.b2ta.api.canvas;

import com.b2ta.api.analyze.AnalysisResult;
import com.b2ta.api.analyze.BedrockAnalyzer;
import com.b2ta.api.analyze.NormalizedDocument;
import com.b2ta.api.canvas.dto.CanvasCriterionView;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.b2ta.api.canvas.dto.CanvasRubricView;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.b2ta.api.canvas.dto.SubmissionView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assembles the marking view's data for one submission: text, verified evidence spans,
 * suggested comments, and any scores Canvas already holds.
 */
@Service
@Slf4j
public class CanvasSubmissionService {

    private final CanvasClient canvasClient;
    private final CanvasRubricMapper mapper;
    private final SubmissionTextExtractor extractor;
    private final BedrockAnalyzer analyzer;
    private final CanvasProperties properties;

    /**
     * Analyses keyed by assignment, user, and attempt. Keying on attempt means a
     * resubmission produces a fresh analysis instead of serving a stale one for work
     * that has changed.
     */
    private final Map<String, AnalysisResult> analysisCache = new ConcurrentHashMap<>();

    public CanvasSubmissionService(CanvasClient canvasClient,
                                   CanvasRubricMapper mapper,
                                   SubmissionTextExtractor extractor,
                                   BedrockAnalyzer analyzer,
                                   CanvasProperties properties) {
        this.canvasClient = canvasClient;
        this.mapper = mapper;
        this.extractor = extractor;
        this.analyzer = analyzer;
        this.properties = properties;
    }

    public SubmissionView getSubmissionView(String assignmentId, String userId) {
        CanvasSubmission submission =
                canvasClient.getSubmission(properties.getCourseId(), assignmentId, userId);
        CanvasRubricView rubric = mapper.toRubricView(
                canvasClient.getAssignment(properties.getCourseId(), assignmentId));

        String studentName = submission.getUser() != null && submission.getUser().getName() != null
                ? submission.getUser().getName()
                : "User " + userId;

        SubmissionTextExtractor.Extraction extraction = extractor.extract(submission);

        if (extraction.failed()) {
            // Extraction failure must not block grading — return the shell so the TA can
            // still score against the rubric manually.
            return SubmissionView.builder()
                    .userId(userId)
                    .studentName(studentName)
                    .paragraphs(List.of())
                    .spans(List.of())
                    .comments(Map.of())
                    .extractionError(extraction.error())
                    .alreadyGraded(submission.isAlreadyGraded())
                    .existingScores(existingScores(submission))
                    .build();
        }

        NormalizedDocument document =
                NormalizedDocument.of(extraction.text(), rubric.getAssignmentName());

        Optional<AnalysisResult> analysis = analyzeCached(
                assignmentId, userId, submission.getAttempt(), document, rubric.getCriteria());

        return SubmissionView.builder()
                .userId(userId)
                .studentName(studentName)
                .paragraphs(toParagraphViews(document))
                .spans(analysis.map(CanvasSubmissionService::allSpans).orElseGet(List::of))
                .comments(analysis.map(a -> toComments(a, rubric.getCriteria()))
                        .orElseGet(Map::of))
                .extractionError(null)
                .alreadyGraded(submission.isAlreadyGraded())
                .existingScores(existingScores(submission))
                .build();
    }

    private Optional<AnalysisResult> analyzeCached(String assignmentId,
                                                   String userId,
                                                   Integer attempt,
                                                   NormalizedDocument document,
                                                   List<CanvasCriterionView> criteria) {
        String key = assignmentId + "#" + userId + "#" + (attempt == null ? "0" : attempt);

        AnalysisResult cached = analysisCache.get(key);
        if (cached != null) {
            log.debug("Serving cached analysis for {}", key);
            return Optional.of(cached);
        }

        Optional<AnalysisResult> fresh = analyzer.analyze(document, criteria);
        fresh.ifPresent(result -> analysisCache.put(key, result));
        return fresh;
    }

    private static List<AnalysisResult.VerifiedSpan> allSpans(AnalysisResult analysis) {
        List<AnalysisResult.VerifiedSpan> spans = new ArrayList<>();
        analysis.getCriteria().forEach(criterion -> spans.addAll(criterion.getEvidence()));
        return spans;
    }

    /**
     * Builds suggested comment text keyed by criterion id then level label, so the
     * feedback editor can offer a draft for whichever level the TA selects.
     */
    private static Map<String, Map<String, String>> toComments(
            AnalysisResult analysis, List<CanvasCriterionView> criteria) {

        Map<String, CanvasCriterionView> byId = new LinkedHashMap<>();
        criteria.forEach(criterion -> byId.put(criterion.getId(), criterion));

        Map<String, Map<String, String>> comments = new LinkedHashMap<>();
        for (AnalysisResult.CriterionAnalysis criterionAnalysis : analysis.getCriteria()) {
            CanvasCriterionView criterion = byId.get(criterionAnalysis.getCriterionId());
            if (criterion == null || criterion.getLevels() == null) {
                continue;
            }

            Map<String, String> perLevel = new LinkedHashMap<>();
            criterion.getLevels().forEach(level -> {
                if (level.getLabel() != null) {
                    // The rationale is the model's read of this criterion; it is the
                    // same regardless of which level the TA ultimately picks.
                    perLevel.put(level.getLabel(), criterionAnalysis.getRationale());
                }
            });
            comments.put(criterionAnalysis.getCriterionId(), perLevel);
        }
        return comments;
    }

    private static List<SubmissionView.ParagraphView> toParagraphViews(NormalizedDocument document) {
        return document.paragraphs().stream()
                .map(paragraph -> SubmissionView.ParagraphView.builder()
                        .idx(paragraph.idx())
                        .label(paragraph.label())
                        .text(paragraph.text())
                        .isTitle(paragraph.isTitle())
                        .build())
                .toList();
    }

    private static Map<String, Double> existingScores(CanvasSubmission submission) {
        if (submission.getRubricAssessment() == null) {
            return Map.of();
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        submission.getRubricAssessment().forEach((criterionId, entry) -> {
            Double points = entry == null ? null : entry.getPoints();
            if (points != null) {
                scores.put(criterionId, points);
            }
        });
        return scores;
    }

    /** Drops any cached analysis for a submission, forcing a fresh one on next load. */
    public void invalidate(String assignmentId, String userId, Integer attempt) {
        analysisCache.remove(assignmentId + "#" + userId + "#" + (attempt == null ? "0" : attempt));
    }
}
