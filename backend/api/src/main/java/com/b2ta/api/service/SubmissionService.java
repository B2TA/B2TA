package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.dto.submission.SubmissionResponse;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsRequest;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsResponse;
import com.b2ta.common.dto.submission.UpdateIdentityRequest;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.enums.IdentityStatus;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Submission listing, identity correction, and TA-scoped upload URLs.
 *
 * <p>The ingestion pipeline itself is Team A's task 3.4-3.6. What is here is the part tasks 5.x need:
 * the read path that the marking view and review screen navigate, the identity correction that clears
 * an unverified flag, and pre-signed upload URLs that are scoped to the requesting TA's prefix
 * (Requirement 18.6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    /** Batch ceiling (Requirement 19.1, 19.2). */
    public static final int MAX_BATCH_SIZE = 150;

    private final TenantGuard tenantGuard;
    private final SubmissionRepository submissionRepository;
    private final S3StorageService storage;
    private final S3KeyBuilder keyBuilder;
    private final com.b2ta.common.config.AwsProperties awsProperties;

    @Transactional(readOnly = true)
    public List<SubmissionResponse> list(TaPrincipal ta, UUID sessionId) {
        tenantGuard.requireSession(ta, sessionId);
        return submissionRepository.findBySessionIdOrderByPosition(sessionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Corrects a resolved student name and marks the identity verified (Requirement 5.7). */
    @Transactional
    public SubmissionResponse updateIdentity(TaPrincipal ta, UUID sessionId, UUID submissionId,
                                             UpdateIdentityRequest request) {
        Submission submission = tenantGuard.requireSubmission(ta, sessionId, submissionId);

        String name = normalizeDisplayName(request.getStudentDisplayName());
        if (name.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A student display name of 1 to 200 characters is required");
        }
        submission.setStudentDisplayName(name);
        // The TA has now stated who this is, which is exactly what "verified" means here.
        submission.setIdentityStatus(IdentityStatus.VERIFIED);
        return toResponse(submissionRepository.save(submission));
    }

    /** Marks every submission's identity as confirmed by the TA (Requirement 5.8). */
    @Transactional
    public List<SubmissionResponse> confirmIdentities(TaPrincipal ta, UUID sessionId) {
        tenantGuard.requireSession(ta, sessionId);
        List<Submission> submissions = submissionRepository
                .findBySessionIdOrderByPosition(sessionId);
        for (Submission submission : submissions) {
            if (submission.getIdentityStatus() != IdentityStatus.VERIFIED) {
                submission.setIdentityStatus(IdentityStatus.VERIFIED);
            }
        }
        submissionRepository.saveAll(submissions);
        return submissions.stream().map(this::toResponse).toList();
    }

    /**
     * Issues pre-signed upload URLs, one per file.
     *
     * <p>Keys are built from the authenticated principal, never from the request, so a URL cannot be
     * obtained for another TA's prefix no matter what filename is submitted (Requirement 18.6).
     */
    @Transactional(readOnly = true)
    public SubmissionUploadUrlsResponse createUploadUrls(TaPrincipal ta, UUID sessionId,
                                                         SubmissionUploadUrlsRequest request) {
        tenantGuard.requireSession(ta, sessionId);
        requireStorage();

        int existing = submissionRepository.countBySessionId(sessionId);
        int requested = request.getFiles().size();
        if (existing + requested > MAX_BATCH_SIZE) {
            throw ApiException.badRequest(ErrorCode.BATCH_LIMIT_EXCEEDED,
                            "A batch holds at most " + MAX_BATCH_SIZE + " submissions; this session "
                                    + "already has " + existing)
                    .with("existing", existing)
                    .with("requested", requested)
                    .with("limit", MAX_BATCH_SIZE);
        }

        List<SubmissionUploadUrlsResponse.FileUploadUrl> urls = new ArrayList<>(requested);
        for (SubmissionUploadUrlsRequest.FileUploadEntry file : request.getFiles()) {
            String key = keyBuilder.submissionUpload(ta, sessionId, file.getFilename());
            urls.add(SubmissionUploadUrlsResponse.FileUploadUrl.builder()
                    .filename(file.getFilename())
                    .objectKey(key)
                    .uploadUrl(storage.presignedUploadUrl(key, "application/octet-stream"))
                    .build());
        }
        return SubmissionUploadUrlsResponse.builder().urls(urls).build();
    }

    private void requireStorage() {
        if (!awsProperties.isS3Configured()) {
            throw ApiException.unprocessable(ErrorCode.EXPORT_FAILED,
                    "File storage is not configured on this deployment, so uploads are unavailable");
        }
    }

    /** Collapses whitespace and trims, per the roster resolver's normalisation rules. */
    private String normalizeDisplayName(String raw) {
        if (raw == null) {
            return "";
        }
        String collapsed = raw.replaceAll("\\s+", " ").trim();
        return collapsed.length() > 200 ? collapsed.substring(0, 200) : collapsed;
    }

    private SubmissionResponse toResponse(Submission submission) {
        return SubmissionResponse.builder()
                .id(submission.getId())
                .originalFilename(submission.getOriginalFilename())
                .studentDisplayName(submission.getStudentDisplayName())
                .canvasSubmissionId(submission.getCanvasSubmissionId())
                .identityStatus(submission.getIdentityStatus())
                .extractionStatus(submission.getExtractionStatus())
                .extractionFailureReason(submission.getExtractionFailureReason())
                .extractedCharCount(submission.getExtractedCharCount())
                .isOversized(submission.getIsOversized())
                .position(submission.getPosition())
                .createdAt(submission.getCreatedAt())
                .build();
    }
}
