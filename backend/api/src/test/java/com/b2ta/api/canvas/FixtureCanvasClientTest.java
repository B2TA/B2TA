package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises fixture mode against the real committed payload, so the fixture path and the
 * live path are known to parse the same shapes.
 */
class FixtureCanvasClientTest {

    private static final Path FIXTURES = Path.of("../../fixtures");

    private FixtureCanvasClient client;

    static boolean fixturesExist() {
        return Files.exists(FIXTURES.resolve("assignment-1.json"));
    }

    @BeforeEach
    void setUp() {
        client = new FixtureCanvasClient(FIXTURES, new ObjectMapper());
    }

    @Test
    @EnabledIf("com.b2ta.api.canvas.FixtureCanvasClientTest#fixturesExist")
    void readsTheCommittedAssignmentFixture() {
        CanvasAssignment assignment = client.getAssignment("1", "1");

        assertThat(assignment.hasRubric()).isTrue();
        assertThat(assignment.getRubric()).hasSize(5);
        assertThat(assignment.getRubric().get(0).getId()).isEqualTo("_1838");
        assertThat(assignment.getRubric().get(0).getRatings()).hasSize(5);
    }

    @Test
    void failsLoudlyWhenAFixtureIsMissing() {
        // Never invent an empty assignment — a missing fixture is a setup error.
        assertThatThrownBy(() -> client.getAssignment("1", "999"))
                .isInstanceOf(CanvasException.class)
                .hasMessageContaining("404");
    }

    @Test
    @EnabledIf("com.b2ta.api.canvas.FixtureCanvasClientTest#fixturesExist")
    void readsTheCommittedSubmissionsFixture() {
        List<CanvasSubmission> submissions = client.listSubmissions("1", "1");

        assertThat(submissions).isNotEmpty();
        // The client returns everything; filtering unsubmitted entries out of the
        // grading queue is the mapper's job, so both states must survive to here.
        assertThat(submissions).extracting(CanvasSubmission::getWorkflowState)
                .contains("submitted", "unsubmitted");
    }

    @Test
    void returnsAnEmptyQueueWhenNoSubmissionsFixtureExists(@TempDir Path emptyDir) {
        // A missing fixture is a setup gap, not an assignment with no submissions —
        // it yields an empty queue rather than failing, but is logged loudly.
        FixtureCanvasClient bare = new FixtureCanvasClient(emptyDir, new ObjectMapper());

        assertThat(bare.listSubmissions("1", "1")).isEmpty();
    }

    @Test
    void recordsWritesInMemoryWithoutReachingCanvas() {
        Map<String, CanvasRubricAssessmentEntry> assessment = Map.of(
                "_1838", CanvasRubricAssessmentEntry.builder().points(5.0).ratingId("blank").build());

        // No submissions fixture exists, so the lookup after the write fails — the point
        // is that the write itself was captured rather than transmitted.
        assertThatThrownBy(() -> client.submitAssessment("1", "1", "42", assessment, "ok"))
                .isInstanceOf(CanvasException.class);

        assertThat(client.recordedWrite("1", "42")).containsKey("_1838");
        assertThat(client.recordedWrite("1", "42").get("_1838").getPoints()).isEqualTo(5.0);
    }
}
