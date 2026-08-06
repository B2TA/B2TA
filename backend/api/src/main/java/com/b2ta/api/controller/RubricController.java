package com.b2ta.api.controller;

import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.api.service.JobService;
import com.b2ta.api.service.RubricExportService;
import com.b2ta.api.service.RubricService;
import com.b2ta.common.dto.export.ExportResponse;
import com.b2ta.common.dto.job.JobCreatedResponse;
import com.b2ta.common.dto.rubric.RubricResponse;
import com.b2ta.common.dto.rubric.SaveRubricRequest;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.enums.JobType;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/rubric")
@RequiredArgsConstructor
public class RubricController {

    private final RubricService rubricService;
    private final RubricExportService rubricExportService;
    private final JobService jobService;
    private final GradingSessionRepository sessionRepository;
    private final SecurityContextHelper securityContextHelper;

    /**
     * GET /api/sessions/{sessionId}/rubric
     * Load the rubric with all criteria and performance levels.
     */
    @GetMapping
    public ResponseEntity<RubricResponse> getRubric(@PathVariable UUID sessionId) {
        RubricResponse response = rubricService.getRubric(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/sessions/{sessionId}/rubric
     * Save/replace the rubric (full replacement with validation).
     */
    @PutMapping
    public ResponseEntity<RubricResponse> saveRubric(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SaveRubricRequest request) {
        RubricResponse response = rubricService.saveRubric(sessionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/sessions/{sessionId}/rubric/parse
     * Trigger rubric parse job. Returns a job ID for polling.
     */
    @PostMapping("/parse")
    public ResponseEntity<JobCreatedResponse> triggerParse(@PathVariable UUID sessionId) {
        UUID taId = securityContextHelper.getCurrentTaId();
        GradingSession session = sessionRepository.findByIdAndTaId(sessionId, taId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        JobCreatedResponse response = jobService.createAndPublishJob(
                session,
                JobType.RUBRIC_PARSE,
                Map.of("sessionId", sessionId.toString())
        );

        return ResponseEntity.accepted().body(response);
    }

    /**
     * POST /api/sessions/{sessionId}/rubric/export
     * Serialize the rubric to CSV, upload to S3, and return a pre-signed download URL.
     * Returns 400 if the rubric has zero criteria.
     */
    @PostMapping("/export")
    public ResponseEntity<ExportResponse> exportRubric(@PathVariable UUID sessionId) {
        ExportResponse response = rubricExportService.exportRubric(sessionId);
        return ResponseEntity.ok(response);
    }
}
