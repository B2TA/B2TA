package com.b2ta.api.canvas;

import com.b2ta.api.analyze.AnalysisResult;
import com.b2ta.api.analyze.BedrockAnalyzer;
import com.b2ta.api.analyze.NormalizedDocument;
import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasRating;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.b2ta.api.canvas.dto.CanvasRubricCriterion;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.b2ta.api.canvas.dto.CanvasUser;
import com.b2ta.api.canvas.dto.SubmissionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanvasSubmissionServiceTest {

    @Mock
    private CanvasClient canvasClient;

    @Mock
    private BedrockAnalyzer analyzer;

    private CanvasSubmissionService service;

    private static final String ASSIGNMENT_ID = "1";
    private static final String USER_ID = "42";

    @BeforeEach
    void setUp() {
        CanvasProperties properties = new CanvasProperties();
        properties.setCourseId("1");
        service = new CanvasSubmissionService(
                canvasClient, new CanvasRubricMapper(),
                new SubmissionTextExtractor(), analyzer, properties);
    }

    private void stubCanvas(CanvasSubmission submission) {
        when(canvasClient.getSubmission(anyString(), anyString(), anyString()))
                .thenReturn(submission);
        when(canvasClient.getAssignment(anyString(), anyString())).thenReturn(assignment());
    }

    @Nested
    @DisplayName("Assembling the view")
    class Assembling {

        @Test
        void splitsExtractedTextIntoParagraphs() {
            stubCanvas(textSubmission("<p>First para.</p><p>Second para.</p>"));
            when(analyzer.analyze(any(), any())).thenReturn(Optional.empty());

            SubmissionView view = service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            // Title occupies index 0; body paragraphs follow.
            assertThat(view.getParagraphs()).hasSize(3);
            assertThat(view.getParagraphs().get(0).isTitle()).isTrue();
            assertThat(view.getParagraphs().get(1).getLabel()).isEqualTo("¶1");
            assertThat(view.getParagraphs().get(2).getText()).isEqualTo("Second para.");
        }

        @Test
        void serializesIsTitleUnderTheNameTheClientReads() throws Exception {
            // Lombok generates isTitle(); Jackson strips the "is" prefix and would emit
            // "title", leaving the client's isTitle undefined and rendering the essay
            // title as an ordinary body paragraph.
            stubCanvas(textSubmission("<p>Body text here.</p>"));
            when(analyzer.analyze(any(), any())).thenReturn(Optional.empty());

            SubmissionView view = service.getSubmissionView(ASSIGNMENT_ID, USER_ID);
            String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(view.getParagraphs().get(0));

            assertThat(json).contains("\"isTitle\":true");
        }

        @Test
        void carriesStudentNameThrough() {
            stubCanvas(textSubmission("<p>Body text here.</p>"));
            when(analyzer.analyze(any(), any())).thenReturn(Optional.empty());

            assertThat(service.getSubmissionView(ASSIGNMENT_ID, USER_ID).getStudentName())
                    .isEqualTo("Maya Chen");
        }

        @Test
        void exposesExistingCanvasScores() {
            CanvasSubmission graded = textSubmission("<p>Body text here.</p>");
            graded.setRubricAssessment(Map.of(
                    "_1838", CanvasRubricAssessmentEntry.builder().points(4.0).build(),
                    "_7746", CanvasRubricAssessmentEntry.builder().points(3.0).build()));
            stubCanvas(graded);
            when(analyzer.analyze(any(), any())).thenReturn(Optional.empty());

            SubmissionView view = service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            assertThat(view.isAlreadyGraded()).isTrue();
            assertThat(view.getExistingScores())
                    .containsEntry("_1838", 4.0)
                    .containsEntry("_7746", 3.0);
        }

        @Test
        void flattensSpansAcrossCriteria() {
            stubCanvas(textSubmission("<p>Body text here.</p>"));
            when(analyzer.analyze(any(), any())).thenReturn(Optional.of(
                    AnalysisResult.builder()
                            .criteria(List.of(
                                    analysisFor("_1838", "h1"),
                                    analysisFor("_7746", "h2")))
                            .build()));

            SubmissionView view = service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            assertThat(view.getSpans()).extracting(AnalysisResult.VerifiedSpan::getId)
                    .containsExactly("h1", "h2");
        }
    }

    @Nested
    @DisplayName("Extraction failure")
    class ExtractionFailure {

        @Test
        void stillReturnsAGradableShell() {
            // A TA must be able to score manually even when the document cannot be read.
            stubCanvas(CanvasSubmission.builder()
                    .userId(42L).submissionType("online_quiz")
                    .user(CanvasUser.builder().id(42L).name("Maya Chen").build())
                    .build());

            SubmissionView view = service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            assertThat(view.getExtractionError()).isNotNull();
            assertThat(view.getStudentName()).isEqualTo("Maya Chen");
            assertThat(view.getParagraphs()).isEmpty();
            assertThat(view.getSpans()).isEmpty();
        }

        @Test
        void preservesExistingScoresOnExtractionFailure() {
            CanvasSubmission broken = CanvasSubmission.builder()
                    .userId(42L).submissionType("online_quiz")
                    .rubricAssessment(Map.of(
                            "_1838", CanvasRubricAssessmentEntry.builder().points(5.0).build()))
                    .build();
            stubCanvas(broken);

            SubmissionView view = service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            assertThat(view.getExtractionError()).isNotNull();
            assertThat(view.getExistingScores()).containsEntry("_1838", 5.0);
        }
    }

    @Nested
    @DisplayName("Analysis caching")
    class Caching {

        @Test
        void analyzesOncePerAttemptAndServesTheCacheAfterwards() {
            stubCanvas(textSubmission("<p>Body text here.</p>"));
            when(analyzer.analyze(any(), any())).thenReturn(Optional.of(
                    AnalysisResult.builder().criteria(List.of()).build()));

            service.getSubmissionView(ASSIGNMENT_ID, USER_ID);
            service.getSubmissionView(ASSIGNMENT_ID, USER_ID);
            service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            verify(analyzer, times(1)).analyze(any(), any());
        }

        @Test
        void reanalyzesWhenTheStudentResubmits() {
            // Keying on attempt means changed work is never served a stale analysis.
            CanvasSubmission first = textSubmission("<p>Body text here.</p>");
            first.setAttempt(1);
            stubCanvas(first);
            when(analyzer.analyze(any(), any())).thenReturn(Optional.of(
                    AnalysisResult.builder().criteria(List.of()).build()));

            service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            CanvasSubmission resubmitted = textSubmission("<p>Revised body text.</p>");
            resubmitted.setAttempt(2);
            when(canvasClient.getSubmission(anyString(), anyString(), anyString()))
                    .thenReturn(resubmitted);

            service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            verify(analyzer, times(2)).analyze(any(), any());
        }

        @Test
        void doesNotCacheAnAbsentAnalysis() {
            // A failed Bedrock call must not poison the cache with "no matches".
            stubCanvas(textSubmission("<p>Body text here.</p>"));
            when(analyzer.analyze(any(), any())).thenReturn(Optional.empty());

            service.getSubmissionView(ASSIGNMENT_ID, USER_ID);
            service.getSubmissionView(ASSIGNMENT_ID, USER_ID);

            verify(analyzer, times(2)).analyze(any(), any());
        }
    }

    // --- helpers ---

    private static CanvasSubmission textSubmission(String body) {
        return CanvasSubmission.builder()
                .id(100L)
                .userId(42L)
                .submissionType("online_text_entry")
                .body(body)
                .attempt(1)
                .user(CanvasUser.builder().id(42L).name("Maya Chen").build())
                .build();
    }

    private static CanvasAssignment assignment() {
        return CanvasAssignment.builder()
                .id(1L)
                .name("Essay 3: Argumentative Analysis")
                .pointsPossible(10.0)
                .rubric(List.of(
                        CanvasRubricCriterion.builder()
                                .id("_1838").description("Thesis Clarity").points(5.0)
                                .ratings(List.of(CanvasRating.builder()
                                        .id("_r1").points(5.0).description("Exemplary").build()))
                                .build(),
                        CanvasRubricCriterion.builder()
                                .id("_7746").description("Use of Evidence").points(5.0)
                                .ratings(List.of(CanvasRating.builder()
                                        .id("_r2").points(5.0).description("Exemplary").build()))
                                .build()))
                .build();
    }

    private static AnalysisResult.CriterionAnalysis analysisFor(String criterionId, String spanId) {
        return AnalysisResult.CriterionAnalysis.builder()
                .criterionId(criterionId)
                .suggestedPoints(4.0)
                .confidence(0.8)
                .rationale("Supported.")
                .evidence(List.of(AnalysisResult.VerifiedSpan.builder()
                        .id(spanId).criterionId(criterionId).text("Body")
                        .confirmed(false).paragraphIdx(1).offsetInParagraph(0).build()))
                .build();
    }
}
