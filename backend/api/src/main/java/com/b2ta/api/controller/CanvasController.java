package com.b2ta.api.controller;

import com.b2ta.api.canvas.CanvasClient;
import com.b2ta.api.canvas.CanvasProperties;
import com.b2ta.api.canvas.CanvasRubricMapper;
import com.b2ta.api.canvas.CanvasSubmissionService;
import com.b2ta.api.canvas.CanvasSyncService;
import com.b2ta.api.canvas.dto.CanvasRubricView;
import com.b2ta.api.canvas.dto.CanvasStudentView;
import com.b2ta.api.canvas.dto.SubmissionView;
import com.b2ta.api.canvas.dto.SyncRequest;
import com.b2ta.api.canvas.dto.SyncResponse;
import com.b2ta.api.security.SecurityContextHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Canvas-backed rubric, roster, and gradebook endpoints.
 *
 * <p>Every Canvas call is routed through here so the API token stays server-side; the
 * frontend never talks to Canvas directly (Requirement 6.2).
 */
@RestController
@RequestMapping("/api/canvas")
@RequiredArgsConstructor
public class CanvasController {

    private final CanvasClient canvasClient;
    private final CanvasRubricMapper mapper;
    private final CanvasSyncService syncService;
    private final CanvasSubmissionService submissionService;
    private final CanvasProperties properties;
    private final SecurityContextHelper securityContextHelper;

    /**
     * The assignment's rubric. Responds with {@code hasRubric: false} rather than demo
     * data when no rubric is attached.
     */
    @GetMapping("/assignments/{assignmentId}/rubric")
    public ResponseEntity<CanvasRubricView> getRubric(@PathVariable String assignmentId) {
        return ResponseEntity.ok(mapper.toRubricView(
                canvasClient.getAssignment(properties.getCourseId(), assignmentId)));
    }

    /**
     * The grading queue, with unsubmitted entries excluded.
     */
    @GetMapping("/assignments/{assignmentId}/queue")
    public ResponseEntity<List<CanvasStudentView>> getQueue(@PathVariable String assignmentId) {
        return ResponseEntity.ok(mapper.toQueue(
                canvasClient.listSubmissions(properties.getCourseId(), assignmentId)));
    }

    /**
     * One student's submission, ready to render: extracted text split into paragraphs,
     * verified evidence spans, suggested comments, and any scores Canvas already holds.
     *
     * <p>Every evidence span here was located verbatim in the submission text — a quote
     * the model fabricated is dropped before this response is built.
     */
    @GetMapping("/assignments/{assignmentId}/submissions/{userId}")
    public ResponseEntity<SubmissionView> getSubmission(@PathVariable String assignmentId,
                                                        @PathVariable String userId) {
        return ResponseEntity.ok(submissionService.getSubmissionView(assignmentId, userId));
    }

    /**
     * Writes the TA's scores to the Canvas gradebook.
     */
    @PostMapping("/assignments/{assignmentId}/submissions/{userId}/sync")
    public ResponseEntity<SyncResponse> sync(@PathVariable String assignmentId,
                                             @PathVariable String userId,
                                             @Valid @RequestBody SyncRequest request) {
        String syncedBy = securityContextHelper.getCurrentTaId() == null
                ? "unknown"
                : securityContextHelper.getCurrentTaId().toString();

        return ResponseEntity.ok(syncService.sync(assignmentId, userId, request, syncedBy));
    }
}
