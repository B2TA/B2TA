package com.b2ta.api.controller;

import com.b2ta.api.service.SubmissionService;
import com.b2ta.common.dto.submission.SubmissionResponse;
import com.b2ta.common.dto.submission.UpdateIdentityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * Lists all submissions for a session, ordered by position.
     * Includes extraction status for each submission.
     */
    @GetMapping
    public ResponseEntity<List<SubmissionResponse>> listSubmissions(@PathVariable UUID sessionId) {
        List<SubmissionResponse> submissions = submissionService.listSubmissions(sessionId);
        return ResponseEntity.ok(submissions);
    }

    /**
     * Updates the student display name for a submission.
     * Validates name is 1-200 characters and non-blank.
     * Re-evaluates duplicate detection across the batch.
     */
    @PutMapping("/{subId}/identity")
    public ResponseEntity<SubmissionResponse> updateIdentity(
            @PathVariable UUID sessionId,
            @PathVariable UUID subId,
            @Valid @RequestBody UpdateIdentityRequest request) {
        SubmissionResponse response = submissionService.updateIdentity(sessionId, subId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Confirms all student identities in the batch.
     * Marks all submissions as acknowledged with their current display names.
     */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmIdentities(@PathVariable UUID sessionId) {
        submissionService.confirmIdentities(sessionId);
        return ResponseEntity.noContent().build();
    }
}
