package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasRating;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.b2ta.api.canvas.dto.CanvasRubricCriterion;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.b2ta.api.canvas.dto.SyncRequest;
import com.b2ta.api.canvas.dto.SyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanvasSyncServiceTest {

    @Mock
    private CanvasClient canvasClient;

    private CanvasSyncService service;
    private CanvasProperties properties;

    private static final String ASSIGNMENT_ID = "1";
    private static final String USER_ID = "42";
    private static final String TA = "ta-uuid";

    @BeforeEach
    void setUp() {
        properties = new CanvasProperties();
        properties.setCourseId("1");
        service = new CanvasSyncService(canvasClient, new CanvasRubricMapper(), properties);
    }

    private void stubRubric() {
        when(canvasClient.getAssignment(eq("1"), eq(ASSIGNMENT_ID))).thenReturn(twoCriterionAssignment());
    }

    private void stubWrite() {
        when(canvasClient.submitAssessment(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(CanvasSubmission.builder().userId(42L).score(8.0).build());
    }

    @Nested
    @DisplayName("Blocking incomplete grading")
    class Blocking {

        @Test
        void blocksWhenACriterionIsMissingEntirely() {
            stubRubric();

            SyncRequest request = SyncRequest.builder()
                    .selections(List.of(selection("_1838", 5.0)))
                    .build();

            assertThatThrownBy(() -> service.sync(ASSIGNMENT_ID, USER_ID, request, TA))
                    .isInstanceOf(CanvasSyncService.IncompleteGradingException.class)
                    .hasMessageContaining("Use of Evidence");

            // Nothing may reach Canvas when the grading is incomplete.
            verify(canvasClient, never())
                    .submitAssessment(anyString(), anyString(), anyString(), any(), any());
        }

        @Test
        void blocksWhenACriterionIsPresentButUnscored() {
            stubRubric();

            SyncRequest request = SyncRequest.builder()
                    .selections(List.of(selection("_1838", 5.0), selection("_7746", null)))
                    .build();

            assertThatThrownBy(() -> service.sync(ASSIGNMENT_ID, USER_ID, request, TA))
                    .isInstanceOf(CanvasSyncService.IncompleteGradingException.class)
                    .hasMessageContaining("Use of Evidence");

            verify(canvasClient, never())
                    .submitAssessment(anyString(), anyString(), anyString(), any(), any());
        }

        @Test
        void namesEveryUnscoredCriterionNotJustTheFirst() {
            stubRubric();

            SyncRequest request = SyncRequest.builder()
                    .selections(List.of(selection("_1838", null), selection("_7746", null)))
                    .build();

            assertThatThrownBy(() -> service.sync(ASSIGNMENT_ID, USER_ID, request, TA))
                    .isInstanceOf(CanvasSyncService.IncompleteGradingException.class)
                    .hasMessageContaining("Thesis Clarity")
                    .hasMessageContaining("Use of Evidence");
        }

        @Test
        void rejectsCriteriaThatAreNotOnTheCanvasRubric() {
            stubRubric();

            // Canvas silently discards unknown criterion ids, so a stale client must fail
            // loudly rather than appear to have written a score that vanished.
            SyncRequest request = SyncRequest.builder()
                    .selections(List.of(
                            selection("_1838", 5.0),
                            selection("_7746", 3.0),
                            selection("_STALE", 4.0)))
                    .build();

            assertThatThrownBy(() -> service.sync(ASSIGNMENT_ID, USER_ID, request, TA))
                    .isInstanceOf(CanvasSyncService.IncompleteGradingException.class)
                    .hasMessageContaining("_STALE");

            verify(canvasClient, never())
                    .submitAssessment(anyString(), anyString(), anyString(), any(), any());
        }

        @Test
        void blocksWhenTheAssignmentHasNoRubric() {
            when(canvasClient.getAssignment(eq("1"), eq(ASSIGNMENT_ID)))
                    .thenReturn(CanvasAssignment.builder().id(1L).name("HW1").build());

            SyncRequest request = SyncRequest.builder()
                    .selections(List.of(selection("_1838", 5.0)))
                    .build();

            assertThatThrownBy(() -> service.sync(ASSIGNMENT_ID, USER_ID, request, TA))
                    .isInstanceOf(CanvasSyncService.IncompleteGradingException.class)
                    .hasMessageContaining("no rubric");
        }
    }

    @Nested
    @DisplayName("Write shape")
    class WriteShape {

        @Test
        void keysAssessmentByVerbatimCanvasCriterionIds() {
            stubRubric();
            stubWrite();

            service.sync(ASSIGNMENT_ID, USER_ID, completeRequest(), TA);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, CanvasRubricAssessmentEntry>> captor =
                    ArgumentCaptor.forClass(Map.class);
            verify(canvasClient).submitAssessment(
                    eq("1"), eq(ASSIGNMENT_ID), eq(USER_ID), captor.capture(), any());

            // Internal slugs like "thesis" would be accepted by Canvas and record nothing.
            assertThat(captor.getValue()).containsOnlyKeys("_1838", "_7746");
            assertThat(captor.getValue().get("_1838").getPoints()).isEqualTo(5.0);
            assertThat(captor.getValue().get("_7746").getPoints()).isEqualTo(3.0);
        }

        @Test
        void defaultsRatingIdToBlankWhenTheTaEnteredRawPoints() {
            stubRubric();
            stubWrite();

            service.sync(ASSIGNMENT_ID, USER_ID, completeRequest(), TA);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, CanvasRubricAssessmentEntry>> captor =
                    ArgumentCaptor.forClass(Map.class);
            verify(canvasClient).submitAssessment(
                    anyString(), anyString(), anyString(), captor.capture(), any());

            assertThat(captor.getValue().get("_1838").getRatingId()).isEqualTo("blank");
        }

        @Test
        void preservesAnExplicitRatingId() {
            stubRubric();
            stubWrite();

            SyncRequest request = SyncRequest.builder()
                    .selections(List.of(
                            SyncRequest.CriterionSelection.builder()
                                    .criterionId("_1838").points(5.0).ratingId("_3845").build(),
                            selection("_7746", 3.0)))
                    .build();

            service.sync(ASSIGNMENT_ID, USER_ID, request, TA);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, CanvasRubricAssessmentEntry>> captor =
                    ArgumentCaptor.forClass(Map.class);
            verify(canvasClient).submitAssessment(
                    anyString(), anyString(), anyString(), captor.capture(), any());

            assertThat(captor.getValue().get("_1838").getRatingId()).isEqualTo("_3845");
        }

        @Test
        void passesTheCommentThroughWhenPresent() {
            stubRubric();
            stubWrite();

            SyncRequest request = completeRequest();
            request.setComment("Strong thesis, thin evidence.");

            service.sync(ASSIGNMENT_ID, USER_ID, request, TA);

            verify(canvasClient).submitAssessment(
                    anyString(), anyString(), anyString(), any(),
                    eq("Strong thesis, thin evidence."));
        }
    }

    @Nested
    @DisplayName("Result reporting")
    class Reporting {

        @Test
        void reportsCanvasTotalRatherThanRecomputingIt() {
            stubRubric();
            when(canvasClient.submitAssessment(anyString(), anyString(), anyString(), any(), any()))
                    .thenReturn(CanvasSubmission.builder().userId(42L).score(7.5).build());

            SyncResponse response = service.sync(ASSIGNMENT_ID, USER_ID, completeRequest(), TA);

            // Local arithmetic would say 8.0; Canvas is the authority.
            assertThat(response.getCanvasTotal()).isEqualTo(7.5);
            assertThat(response.isSynced()).isTrue();
            assertThat(response.getCriteriaWritten()).isEqualTo(2);
        }

        @Test
        void recordsWhoSyncedAndWhen() {
            stubRubric();
            stubWrite();

            SyncResponse response = service.sync(ASSIGNMENT_ID, USER_ID, completeRequest(), TA);

            assertThat(response.getSyncedBy()).isEqualTo(TA);
            assertThat(response.getSyncedAt()).isNotNull();
        }

        @Test
        void flagsFixtureModeSoASuccessIsNotMistakenForARealWrite() {
            properties.setDataSource(CanvasProperties.DataSource.FIXTURES);
            stubRubric();
            stubWrite();

            SyncResponse response = service.sync(ASSIGNMENT_ID, USER_ID, completeRequest(), TA);

            assertThat(response.isFixtureMode()).isTrue();
        }

        @Test
        void doesNotFlagFixtureModeWhenLive() {
            properties.setDataSource(CanvasProperties.DataSource.CANVAS);
            stubRubric();
            stubWrite();

            SyncResponse response = service.sync(ASSIGNMENT_ID, USER_ID, completeRequest(), TA);

            assertThat(response.isFixtureMode()).isFalse();
        }
    }

    // --- helpers ---

    private static SyncRequest completeRequest() {
        return SyncRequest.builder()
                .selections(List.of(selection("_1838", 5.0), selection("_7746", 3.0)))
                .build();
    }

    private static SyncRequest.CriterionSelection selection(String id, Double points) {
        return SyncRequest.CriterionSelection.builder()
                .criterionId(id)
                .points(points)
                .build();
    }

    private static CanvasAssignment twoCriterionAssignment() {
        return CanvasAssignment.builder()
                .id(1L)
                .name("HW1")
                .pointsPossible(10.0)
                .rubric(List.of(
                        CanvasRubricCriterion.builder()
                                .id("_1838").description("Thesis Clarity").points(5.0)
                                .ratings(List.of(CanvasRating.builder()
                                        .id("_3845").points(5.0).description("Exemplary").build()))
                                .build(),
                        CanvasRubricCriterion.builder()
                                .id("_7746").description("Use of Evidence").points(5.0)
                                .ratings(List.of(CanvasRating.builder()
                                        .id("_9001").points(5.0).description("Exemplary").build()))
                                .build()))
                .build();
    }
}
