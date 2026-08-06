package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasCriterionView;
import com.b2ta.api.canvas.dto.CanvasRating;
import com.b2ta.api.canvas.dto.CanvasRubricCriterion;
import com.b2ta.api.canvas.dto.CanvasRubricView;
import com.b2ta.api.canvas.dto.CanvasStudentView;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.b2ta.api.canvas.dto.CanvasUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanvasRubricMapperTest {

    private CanvasRubricMapper mapper;
    private ObjectMapper objectMapper;

    /** The committed fixture, relative to the api module's working directory. */
    private static final Path FIXTURE = Path.of("../../fixtures/assignment-1.json");

    static boolean fixtureExists() {
        return Files.exists(FIXTURE);
    }

    @BeforeEach
    void setUp() {
        mapper = new CanvasRubricMapper();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Against the captured Canvas fixture")
    class AgainstRealFixture {

        @Test
        @EnabledIf("com.b2ta.api.canvas.CanvasRubricMapperTest#fixtureExists")
        void parsesTheRealPayloadAndPreservesCanvasIds() throws Exception {
            CanvasAssignment assignment =
                    objectMapper.readValue(Files.readString(FIXTURE), CanvasAssignment.class);

            CanvasRubricView view = mapper.toRubricView(assignment);

            assertThat(view.isHasRubric()).isTrue();
            assertThat(view.getCriteria()).hasSize(5);

            // Verbatim ids observed on the live instance. If these ever change shape,
            // write-back silently records nothing, so pin them.
            assertThat(view.getCriteria())
                    .extracting(CanvasCriterionView::getId)
                    .containsExactly("_1838", "_7746", "_3661", "_5523", "_2293");

            assertThat(view.getCriteria())
                    .extracting(CanvasCriterionView::getLabel)
                    .containsExactly("Thesis Clarity", "Use of Evidence", "Organization",
                            "Grammar & Mechanics", "Citation Format");

            assertThat(view.getPointsPossible()).isEqualTo(20.0);
        }

        @Test
        @EnabledIf("com.b2ta.api.canvas.CanvasRubricMapperTest#fixtureExists")
        void ordersLevelsByPointsDescending() throws Exception {
            CanvasAssignment assignment =
                    objectMapper.readValue(Files.readString(FIXTURE), CanvasAssignment.class);

            CanvasRubricView view = mapper.toRubricView(assignment);

            assertThat(view.getCriteria().get(0).getLevels())
                    .extracting(l -> l.getPts())
                    .containsExactly(5.0, 4.0, 3.0, 2.0, 1.0);
        }
    }

    @Nested
    @DisplayName("Color assignment")
    class Colors {

        @Test
        void assignsColorsByIndexNotByHashingIds() {
            // Same positions, different ids: colors must be identical, because a hash over
            // changing ids would reshuffle highlight colors between reloads.
            CanvasRubricView first = mapper.toRubricView(assignmentWithIds("_1111", "_2222"));
            CanvasRubricView second = mapper.toRubricView(assignmentWithIds("_9999", "_8888"));

            assertThat(first.getCriteria().get(0).getColor())
                    .isEqualTo(second.getCriteria().get(0).getColor());
            assertThat(first.getCriteria().get(1).getColor())
                    .isEqualTo(second.getCriteria().get(1).getColor());
        }

        @Test
        void adjacentCriteriaGetDistinctColors() {
            CanvasRubricView view = mapper.toRubricView(assignmentWithIds("_a", "_b", "_c"));

            assertThat(view.getCriteria())
                    .extracting(CanvasCriterionView::getColor)
                    .doesNotHaveDuplicates();
        }

        @Test
        void derivesTranslucentFillFromTheSameColor() {
            CanvasRubricView view = mapper.toRubricView(assignmentWithIds("_a"));
            CanvasCriterionView criterion = view.getCriteria().get(0);

            assertThat(criterion.getBorder()).isEqualTo(criterion.getColor());
            assertThat(criterion.getBg()).startsWith("rgba(").endsWith(",0.14)");
        }

        @Test
        void cyclesRatherThanFailingBeyondThePaletteSize() {
            // Canvas imposes no criterion cap; a 35-criterion rubric must still load.
            String[] ids = new String[35];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = "_c" + i;
            }

            CanvasRubricView view = mapper.toRubricView(assignmentWithIds(ids));

            assertThat(view.getCriteria()).hasSize(35);
            assertThat(view.getCriteria().get(30).getColor())
                    .isEqualTo(view.getCriteria().get(0).getColor());
        }

        @Test
        void convertsHexToRgbaCorrectly() {
            assertThat(CanvasRubricMapper.toRgba("#D32F2F", 0.14))
                    .isEqualTo("rgba(211,47,47,0.14)");
        }
    }

    @Nested
    @DisplayName("Missing rubric")
    class MissingRubric {

        @Test
        void reportsAbsenceRatherThanFabricatingCriteria() {
            CanvasAssignment assignment = CanvasAssignment.builder()
                    .id(1L).name("HW1").rubric(null).build();

            CanvasRubricView view = mapper.toRubricView(assignment);

            assertThat(view.isHasRubric()).isFalse();
            assertThat(view.getCriteria()).isEmpty();
            assertThat(view.getAssignmentName()).isEqualTo("HW1");
        }

        @Test
        void treatsAnEmptyRubricArrayAsNoRubric() {
            CanvasAssignment assignment = CanvasAssignment.builder()
                    .id(1L).name("HW1").rubric(List.of()).build();

            assertThat(mapper.toRubricView(assignment).isHasRubric()).isFalse();
        }
    }

    @Nested
    @DisplayName("Grading queue")
    class Queue {

        @Test
        void excludesUnsubmittedEntries() {
            List<CanvasSubmission> submissions = List.of(
                    submission(1L, "Alice", "submitted"),
                    submission(2L, "Bob", "unsubmitted"),
                    submission(3L, "Carol", "graded"));

            List<CanvasStudentView> queue = mapper.toQueue(submissions);

            assertThat(queue).extracting(CanvasStudentView::getName)
                    .containsExactly("Alice", "Carol");
        }

        @Test
        void numbersPositionsContiguouslyAfterFiltering() {
            // "Student N of M" must count gradable work only — a gap here would show
            // "Student 3 of 2".
            List<CanvasSubmission> submissions = List.of(
                    submission(1L, "Alice", "submitted"),
                    submission(2L, "Bob", "unsubmitted"),
                    submission(3L, "Carol", "submitted"));

            List<CanvasStudentView> queue = mapper.toQueue(submissions);

            assertThat(queue).extracting(CanvasStudentView::getPosition)
                    .containsExactly(0, 1);
        }

        @Test
        void marksAlreadyGradedSubmissions() {
            CanvasSubmission graded = submission(1L, "Alice", "graded");
            graded.setRubricAssessment(java.util.Map.of(
                    "_1838", com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry.builder()
                            .points(4.0).build()));

            List<CanvasStudentView> queue = mapper.toQueue(List.of(graded));

            assertThat(queue.get(0).isAlreadyGraded()).isTrue();
        }

        @Test
        void fallsBackToUserIdWhenNameMissing() {
            CanvasSubmission noUser = CanvasSubmission.builder()
                    .id(10L).userId(7L).workflowState("submitted").build();

            assertThat(mapper.toQueue(List.of(noUser)).get(0).getName()).isEqualTo("User 7");
        }

        @Test
        void handlesNullAndEmptyInput() {
            assertThat(mapper.toQueue(null)).isEmpty();
            assertThat(mapper.toQueue(List.of())).isEmpty();
        }
    }

    // --- helpers ---

    private static CanvasAssignment assignmentWithIds(String... ids) {
        List<CanvasRubricCriterion> criteria = java.util.Arrays.stream(ids)
                .map(id -> CanvasRubricCriterion.builder()
                        .id(id)
                        .description("Criterion " + id)
                        .points(5.0)
                        .ratings(List.of(
                                CanvasRating.builder().id(id + "r1").points(3.0)
                                        .description("Mid").build(),
                                CanvasRating.builder().id(id + "r2").points(5.0)
                                        .description("Top").build()))
                        .build())
                .toList();

        return CanvasAssignment.builder()
                .id(1L).name("HW1").pointsPossible(20.0).rubric(criteria).build();
    }

    private static CanvasSubmission submission(long userId, String name, String state) {
        return CanvasSubmission.builder()
                .id(userId * 100)
                .userId(userId)
                .workflowState(state)
                .user(CanvasUser.builder().id(userId).name(name).build())
                .build();
    }
}
