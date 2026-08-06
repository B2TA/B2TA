package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link CanvasClient} backed by captured Canvas response bodies on disk.
 *
 * <p>Lets the app run end-to-end without a Canvas token. Fixtures are real payloads
 * captured from the live instance, so this path exercises the same parsing code as
 * {@link LiveCanvasClient} (Requirement 6.5).
 *
 * <p>Writes are held in memory only. A fixture-mode sync reports success so the UI flow
 * can be demonstrated, but says plainly in the log that nothing reached Canvas — a demo
 * that appears to write grades while disconnected is the worst outcome on stage.
 */
@Slf4j
public class FixtureCanvasClient implements CanvasClient {

    private final Path fixturesDir;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, CanvasRubricAssessmentEntry>> writes =
            new java.util.concurrent.ConcurrentHashMap<>();

    public FixtureCanvasClient(Path fixturesDir, ObjectMapper objectMapper) {
        this.fixturesDir = fixturesDir;
        this.objectMapper = objectMapper;
        log.warn("Canvas integration running in FIXTURE mode from {} — no calls will reach Canvas.",
                fixturesDir.toAbsolutePath());
    }

    @Override
    public CanvasAssignment getAssignment(String courseId, String assignmentId) {
        return read("assignment-" + assignmentId + ".json", CanvasAssignment.class);
    }

    @Override
    public List<CanvasSubmission> listSubmissions(String courseId, String assignmentId) {
        Path path = fixturesDir.resolve("submissions-" + assignmentId + ".json");
        if (!Files.exists(path)) {
            // Distinguish "no fixture captured yet" from "the assignment has no
            // submissions" — returning an empty list silently would look like an empty
            // grading queue and send someone hunting in the wrong place.
            log.warn("No submissions fixture at {} — returning an empty grading queue.", path);
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(Files.readString(path),
                    new TypeReference<List<CanvasSubmission>>() {
                    });
        } catch (IOException e) {
            throw new CanvasException("Could not read fixture " + path + ".", 0, false, e);
        }
    }

    @Override
    public CanvasSubmission getSubmission(String courseId, String assignmentId, String userId) {
        return listSubmissions(courseId, assignmentId).stream()
                .filter(s -> userId.equals(String.valueOf(s.getUserId())))
                .findFirst()
                .orElseThrow(() -> CanvasException.notFound("submission for user " + userId));
    }

    @Override
    public CanvasSubmission submitAssessment(String courseId,
                                             String assignmentId,
                                             String userId,
                                             Map<String, CanvasRubricAssessmentEntry> assessment,
                                             String comment) {
        writes.put(key(assignmentId, userId), assessment);
        log.warn("FIXTURE MODE: recorded {} criterion scores for user {} in memory. "
                + "Nothing was written to Canvas.", assessment.size(), userId);

        CanvasSubmission submission = getSubmission(courseId, assignmentId, userId);
        submission.setRubricAssessment(assessment);
        submission.setScore(assessment.values().stream()
                .map(CanvasRubricAssessmentEntry::getPoints)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum());
        return submission;
    }

    /** Exposes what a fixture-mode sync recorded, for assertions in tests. */
    public Map<String, CanvasRubricAssessmentEntry> recordedWrite(String assignmentId, String userId) {
        return writes.get(key(assignmentId, userId));
    }

    private static String key(String assignmentId, String userId) {
        return assignmentId + "#" + userId;
    }

    private <T> T read(String filename, Class<T> type) {
        Path path = fixturesDir.resolve(filename);
        if (!Files.exists(path)) {
            throw CanvasException.notFound("fixture " + path);
        }
        try {
            return objectMapper.readValue(Files.readString(path), type);
        } catch (IOException e) {
            throw new CanvasException("Could not read fixture " + path + ".", 0, false, e);
        }
    }
}
