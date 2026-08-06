package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.b2ta.api.canvas.dto.CanvasSubmission;

import java.util.List;
import java.util.Map;

/**
 * Access to a Canvas LMS instance.
 *
 * <p>Two implementations sit behind this interface, selected by
 * {@code canvas.data-source}: {@link LiveCanvasClient} against the real instance and
 * {@link FixtureCanvasClient} against committed response bodies. Fixtures are real
 * captured payloads so both paths exercise identical parsing code — a fixture that
 * drifts from the real shape is worse than no fixture.
 */
public interface CanvasClient {

    /**
     * Fetches an assignment including its attached rubric.
     *
     * @throws CanvasException on transport failure or a non-2xx response
     */
    CanvasAssignment getAssignment(String courseId, String assignmentId);

    /**
     * Fetches every submission for an assignment, following {@code Link} pagination
     * until exhausted. Includes unsubmitted entries — filtering is the caller's job so
     * that callers wanting a roster count still see them.
     */
    List<CanvasSubmission> listSubmissions(String courseId, String assignmentId);

    /**
     * Fetches a single student's submission.
     */
    CanvasSubmission getSubmission(String courseId, String assignmentId, String userId);

    /**
     * Writes per-criterion scores and an optional overall comment back to the gradebook.
     *
     * @param assessment scores keyed by verbatim Canvas criterion id (e.g. {@code _1838})
     * @param comment    overall feedback; skipped when null or blank
     * @return the updated submission as Canvas reports it, so the caller can show the
     * Canvas-side total rather than its own arithmetic
     */
    CanvasSubmission submitAssessment(String courseId,
                                      String assignmentId,
                                      String userId,
                                      Map<String, CanvasRubricAssessmentEntry> assessment,
                                      String comment);
}
