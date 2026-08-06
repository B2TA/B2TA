package com.b2ta.api.controller;

import com.b2ta.api.security.CurrentTa;
import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.service.SubmissionService;
import com.b2ta.common.dto.submission.SubmissionResponse;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsRequest;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsResponse;
import com.b2ta.common.dto.submission.UpdateIdentityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Submission listing and student identity management. */
@RestController
@RequestMapping("/api/sessions/{sessionId}/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @GetMapping
    public List<SubmissionResponse> list(@CurrentTa TaPrincipal ta, @PathVariable UUID sessionId) {
        return submissionService.list(ta, sessionId);
    }

    @PostMapping("/upload-urls")
    public SubmissionUploadUrlsResponse uploadUrls(
            @CurrentTa TaPrincipal ta,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SubmissionUploadUrlsRequest request) {
        return submissionService.createUploadUrls(ta, sessionId, request);
    }

    @PutMapping("/{submissionId}/identity")
    public SubmissionResponse updateIdentity(@CurrentTa TaPrincipal ta,
                                             @PathVariable UUID sessionId,
                                             @PathVariable UUID submissionId,
                                             @Valid @RequestBody UpdateIdentityRequest request) {
        return submissionService.updateIdentity(ta, sessionId, submissionId, request);
    }

    @PostMapping("/confirm")
    public List<SubmissionResponse> confirmIdentities(@CurrentTa TaPrincipal ta,
                                                      @PathVariable UUID sessionId) {
        return submissionService.confirmIdentities(ta, sessionId);
    }
}
