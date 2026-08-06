package com.b2ta.api.service;

import com.b2ta.api.config.AwsProperties;
import com.b2ta.api.repository.GradingSessionRepository;
import com.b2ta.api.repository.RubricRepository;
import com.b2ta.api.security.SecurityContextHelper;
import com.b2ta.common.dto.export.ExportResponse;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.Rubric;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates rubric export: load rubric → validate → serialize to CSV → upload to S3 → return download URL.
 */
@Service
@RequiredArgsConstructor
public class RubricExportService {

    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);

    private final RubricRepository rubricRepository;
    private final GradingSessionRepository sessionRepository;
    private final SecurityContextHelper securityContextHelper;
    private final RubricPrinter rubricPrinter;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;

    /**
     * Exports the rubric for a session as CSV, uploads to S3, and returns a pre-signed download URL.
     *
     * @param sessionId the grading session ID
     * @return ExportResponse with the download URL and filename
     * @throws EntityNotFoundException  if session or rubric not found
     * @throws ResponseStatusException  400 if rubric has no criteria
     */
    @Transactional(readOnly = true)
    public ExportResponse exportRubric(UUID sessionId) {
        UUID taId = securityContextHelper.getCurrentTaId();

        // Verify session ownership
        GradingSession session = sessionRepository.findByIdAndTaId(sessionId, taId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found"));

        // Load rubric
        Rubric rubric = rubricRepository.findBySessionId(session.getId())
                .orElseThrow(() -> new EntityNotFoundException("Rubric not found for session"));

        // Validate at least 1 criterion
        if (rubric.getCriteria() == null || rubric.getCriteria().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rubric requires at least one criterion to export");
        }

        // Serialize to CSV
        byte[] csvBytes = rubricPrinter.serialize(rubric);

        // Build S3 key: exports/{ta_id}/{session_id}/rubric-export-{timestamp}.csv
        String timestamp = Instant.now().toString().replace(":", "-");
        String filename = "rubric-export-" + timestamp + ".csv";
        String objectKey = String.format("exports/%s/%s/%s", taId, sessionId, filename);

        // Upload to S3
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(awsProperties.getS3().getBucket())
                .key(objectKey)
                .contentType("text/csv; charset=UTF-8")
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(csvBytes));

        // Generate pre-signed GET URL (15 minutes)
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(awsProperties.getS3().getBucket())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String downloadUrl = presignedRequest.url().toString();

        return ExportResponse.builder()
                .downloadUrl(downloadUrl)
                .filename(filename)
                .build();
    }
}
