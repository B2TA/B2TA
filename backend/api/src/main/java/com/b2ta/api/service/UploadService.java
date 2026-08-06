package com.b2ta.api.service;

import com.b2ta.api.config.AwsProperties;
import com.b2ta.common.dto.rubric.RubricUploadUrlRequest;
import com.b2ta.common.dto.rubric.RubricUploadUrlResponse;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsRequest;
import com.b2ta.common.dto.submission.SubmissionUploadUrlsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates scoped pre-signed S3 PUT URLs for rubric and submission file uploads.
 * All URLs are scoped to the TA's S3 prefix: uploads/{ta_id}/{session_id}/...
 * Pre-signed URL TTL: 15 minutes.
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(15);

    private static final Set<String> ALLOWED_RUBRIC_EXTENSIONS = Set.of(".pdf", ".csv", ".xlsx");
    private static final long RUBRIC_MIN_SIZE = 1L;
    private static final long RUBRIC_MAX_SIZE = 5_242_880L;

    private static final Set<String> ALLOWED_SUBMISSION_EXTENSIONS = Set.of(".pdf", ".docx", ".txt", ".md", ".zip");
    private static final long SUBMISSION_MIN_SIZE = 1L;
    private static final long SUBMISSION_MAX_SIZE = 52_428_800L;

    private static final int MAX_SUBMISSION_BATCH_SIZE = 300;

    private final S3Presigner s3Presigner;
    private final AwsProperties awsProperties;

    /**
     * Generates a pre-signed PUT URL for a single rubric file upload.
     *
     * @param taId      the UUID of the authenticated TA
     * @param sessionId the UUID of the grading session
     * @param request   the upload request containing filename and size
     * @return response with the pre-signed URL and object key
     * @throws IllegalArgumentException if validation fails
     */
    public RubricUploadUrlResponse generateRubricUploadUrl(UUID taId, UUID sessionId, RubricUploadUrlRequest request) {
        String filename = request.getFilename();
        Long size = request.getSize();

        validateFileExtension(filename, ALLOWED_RUBRIC_EXTENSIONS, "Accepted rubric formats: .pdf, .csv, .xlsx");
        validateFileSize(size, RUBRIC_MIN_SIZE, RUBRIC_MAX_SIZE,
                "Rubric file size must be between 1 byte and 5,242,880 bytes");

        String extension = getExtension(filename);
        String objectKey = buildObjectKey(taId, sessionId, "rubrics", extension);

        String uploadUrl = generatePresignedPutUrl(objectKey);

        return RubricUploadUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .objectKey(objectKey)
                .build();
    }

    /**
     * Generates pre-signed PUT URLs for a batch of submission file uploads (1-300 files).
     *
     * @param taId      the UUID of the authenticated TA
     * @param sessionId the UUID of the grading session
     * @param request   the upload request containing a list of files with filenames and sizes
     * @return response with a list of pre-signed URLs and object keys
     * @throws IllegalArgumentException if validation fails
     */
    public SubmissionUploadUrlsResponse generateSubmissionUploadUrls(UUID taId, UUID sessionId,
                                                                     SubmissionUploadUrlsRequest request) {
        List<SubmissionUploadUrlsRequest.FileUploadEntry> files = request.getFiles();

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }
        if (files.size() > MAX_SUBMISSION_BATCH_SIZE) {
            throw new IllegalArgumentException("Maximum " + MAX_SUBMISSION_BATCH_SIZE + " files per batch");
        }

        List<SubmissionUploadUrlsResponse.FileUploadUrl> urls = files.stream()
                .map(entry -> generateSingleSubmissionUrl(taId, sessionId, entry))
                .collect(Collectors.toList());

        return SubmissionUploadUrlsResponse.builder()
                .urls(urls)
                .build();
    }

    private SubmissionUploadUrlsResponse.FileUploadUrl generateSingleSubmissionUrl(
            UUID taId, UUID sessionId, SubmissionUploadUrlsRequest.FileUploadEntry entry) {

        String filename = entry.getFilename();
        Long size = entry.getSize();

        validateFileExtension(filename, ALLOWED_SUBMISSION_EXTENSIONS,
                "Accepted submission formats: .pdf, .docx, .txt, .md, .zip");
        validateFileSize(size, SUBMISSION_MIN_SIZE, SUBMISSION_MAX_SIZE,
                "Submission file size must be between 1 byte and 52,428,800 bytes");

        String extension = getExtension(filename);
        String objectKey = buildObjectKey(taId, sessionId, "submissions", extension);

        String uploadUrl = generatePresignedPutUrl(objectKey);

        return SubmissionUploadUrlsResponse.FileUploadUrl.builder()
                .filename(filename)
                .uploadUrl(uploadUrl)
                .objectKey(objectKey)
                .build();
    }

    private String generatePresignedPutUrl(String objectKey) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(awsProperties.getS3().getBucket())
                .key(objectKey)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_URL_TTL)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    /**
     * Builds an S3 object key scoped to the TA and session.
     * Format: uploads/{ta_id}/{session_id}/{subfolder}/{uuid}.{ext}
     */
    private String buildObjectKey(UUID taId, UUID sessionId, String subfolder, String extension) {
        String uuid = UUID.randomUUID().toString();
        return String.format("uploads/%s/%s/%s/%s%s", taId, sessionId, subfolder, uuid, extension);
    }

    private void validateFileExtension(String filename, Set<String> allowedExtensions, String errorMessage) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }
        String extension = getExtension(filename);
        if (extension.isEmpty() || !allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private void validateFileSize(Long size, long minSize, long maxSize, String errorMessage) {
        if (size == null) {
            throw new IllegalArgumentException("File size is required");
        }
        if (size < minSize || size > maxSize) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    /**
     * Extracts the file extension from a filename, lowercased.
     * Returns empty string if no extension is present.
     */
    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot).toLowerCase(Locale.ROOT);
    }
}
