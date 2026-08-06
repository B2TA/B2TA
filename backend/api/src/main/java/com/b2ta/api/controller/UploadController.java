package com.b2ta.api.controller;

import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.api.service.UploadService;
import com.b2ta.common.dto.rubric.RubricUploadUrlRequest;
import com.b2ta.common.dto.rubric.RubricUploadUrlResponse;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsRequest;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for generating pre-signed S3 upload URLs.
 * Provides endpoints for rubric file upload (single) and submission file uploads (batch).
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * POST /api/sessions/{id}/rubric/upload-url
     * Generates a pre-signed S3 PUT URL for a single rubric file upload.
     */
    @PostMapping("/rubric/upload-url")
    public ResponseEntity<RubricUploadUrlResponse> getRubricUploadUrl(
            @PathVariable UUID sessionId,
            @Valid @RequestBody RubricUploadUrlRequest request) {

        UUID taId = securityContextHelper.getCurrentTaId();
        RubricUploadUrlResponse response = uploadService.generateRubricUploadUrl(taId, sessionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/sessions/{id}/submissions/upload-urls
     * Generates pre-signed S3 PUT URLs for a batch of submission file uploads (1-300 files).
     */
    @PostMapping("/submissions/upload-urls")
    public ResponseEntity<SubmissionUploadUrlsResponse> getSubmissionUploadUrls(
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmissionUploadUrlsRequest request) {

        UUID taId = securityContextHelper.getCurrentTaId();
        SubmissionUploadUrlsResponse response = uploadService.generateSubmissionUploadUrls(taId, sessionId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Exception handler for validation errors from the UploadService.
     * Returns 400 Bad Request with the error message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Simple error response body.
     */
    public record ErrorResponse(String message) {}
}
