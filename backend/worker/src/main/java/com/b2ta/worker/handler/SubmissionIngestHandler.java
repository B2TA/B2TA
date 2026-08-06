package com.b2ta.worker.handler;

import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.entity.enums.ExtractionStatus;
import com.b2ta.common.entity.enums.IdentityStatus;
import com.b2ta.common.entity.enums.JobType;
import com.b2ta.worker.messaging.JobHandler;
import com.b2ta.worker.messaging.JobMessage;
import com.b2ta.worker.messaging.JobStatusUpdater;
import com.b2ta.worker.repository.GradingSessionRepository;
import com.b2ta.worker.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles SUBMISSION_INGEST jobs. Downloads uploaded files from S3,
 * expands ZIP archives (with validation), and creates Submission records
 * with extraction_status=PENDING for later text extraction (task 3.9).
 *
 * Idempotency: skips files where a Submission with the same (session_id, original_filename)
 * already exists.
 *
 * ZIP validation:
 * - Rejects path traversal (entries with ".." segments or leading "/")
 * - Enforces max 300 entries
 * - Enforces max 1 GB uncompressed total
 * - Skips unsupported extensions (only .pdf, .docx, .txt, .md allowed)
 * - Skips nested .zip files
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubmissionIngestHandler implements JobHandler {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".docx", ".txt", ".md");
    private static final int MAX_ZIP_ENTRIES = 300;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 1_073_741_824L; // 1 GB

    private final S3Client s3Client;
    private final SubmissionRepository submissionRepository;
    private final GradingSessionRepository sessionRepository;
    private final JobStatusUpdater jobStatusUpdater;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Override
    public JobType getJobType() {
        return JobType.SUBMISSION_INGEST;
    }

    @Override
    public void handle(JobMessage message) throws Exception {
        UUID sessionId = UUID.fromString(message.getSessionId());
        UUID jobId = UUID.fromString(message.getJobId());

        @SuppressWarnings("unchecked")
        List<String> objectKeys = (List<String>) message.getPayload().get("objectKeys");

        if (objectKeys == null || objectKeys.isEmpty()) {
            log.warn("SUBMISSION_INGEST job {} has no objectKeys in payload", jobId);
            return;
        }

        GradingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "GradingSession not found: " + sessionId));

        int totalFiles = objectKeys.size();
        jobStatusUpdater.updateProgress(jobId, 0, totalFiles);

        int processedCount = 0;

        for (String objectKey : objectKeys) {
            try {
                processObjectKey(objectKey, session, sessionId, jobId);
            } catch (PathTraversalException e) {
                // Path traversal detected — fail the entire job immediately
                throw new RuntimeException(
                        "ZIP archive rejected: path traversal detected in entry '" +
                                e.getOffendingPath() + "' of " + objectKey, e);
            } catch (ZipLimitExceededException e) {
                // Limit exceeded — fail the entire job
                throw new RuntimeException(
                        "ZIP archive rejected: " + e.getMessage() + " in " + objectKey, e);
            }

            processedCount++;
            jobStatusUpdater.updateProgress(jobId, processedCount, totalFiles);
        }

        log.info("SUBMISSION_INGEST job {} completed. Processed {} files for session {}",
                jobId, processedCount, sessionId);
    }

    private void processObjectKey(String objectKey, GradingSession session,
                                  UUID sessionId, UUID jobId) throws Exception {
        String lowerKey = objectKey.toLowerCase(Locale.ROOT);

        if (lowerKey.endsWith(".zip")) {
            processZipArchive(objectKey, session, sessionId);
        } else if (isSupportedExtension(lowerKey)) {
            String filename = extractFilename(objectKey);
            createSubmissionIfNotExists(session, sessionId, objectKey, filename);
        } else {
            log.info("Skipping unsupported file extension in object key: {}", objectKey);
        }
    }

    private void processZipArchive(String objectKey, GradingSession session,
                                   UUID sessionId) throws Exception {
        byte[] zipBytes = downloadFromS3(objectKey);

        int entryCount = 0;
        long totalUncompressedBytes = 0;
        List<SkippedEntry> skippedEntries = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                entryCount++;

                // Validate entry count limit
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new ZipLimitExceededException(
                            "ZIP contains more than " + MAX_ZIP_ENTRIES + " entries " +
                                    "(observed at least " + entryCount + ")");
                }

                // Validate path traversal
                String entryName = entry.getName();
                validateNoPathTraversal(entryName);

                // Track uncompressed size
                long entrySize = readEntrySize(zis);
                totalUncompressedBytes += entrySize;

                if (totalUncompressedBytes > MAX_ZIP_UNCOMPRESSED_BYTES) {
                    throw new ZipLimitExceededException(
                            "ZIP uncompressed content exceeds " + MAX_ZIP_UNCOMPRESSED_BYTES +
                                    " bytes (observed " + totalUncompressedBytes + " bytes)");
                }

                // Determine if the entry has a supported extension
                String entryLower = entryName.toLowerCase(Locale.ROOT);
                String entryFilename = extractFilenameFromPath(entryName);

                if (entryLower.endsWith(".zip")) {
                    skippedEntries.add(new SkippedEntry(entryName, "nested zip"));
                    log.debug("Skipping nested .zip entry: {}", entryName);
                } else if (!isSupportedExtension(entryLower)) {
                    skippedEntries.add(new SkippedEntry(entryName, "unsupported extension"));
                    log.debug("Skipping unsupported extension in ZIP entry: {}", entryName);
                } else {
                    // Supported file — create submission record
                    // Use the entry filename within the ZIP as the original filename
                    createSubmissionIfNotExists(session, sessionId, objectKey + "!" + entryName, entryFilename);
                }

                zis.closeEntry();
            }
        }

        if (!skippedEntries.isEmpty()) {
            log.info("ZIP archive {} had {} skipped entries: {}",
                    objectKey, skippedEntries.size(), skippedEntries);
        }
    }

    @Transactional
    protected void createSubmissionIfNotExists(GradingSession session, UUID sessionId,
                                               String s3Key, String originalFilename) {
        // Idempotency check: session_id + original_filename
        Optional<Submission> existing = submissionRepository
                .findBySessionIdAndOriginalFilename(sessionId, originalFilename);

        if (existing.isPresent()) {
            log.debug("Submission already exists for session={} filename={}, skipping",
                    sessionId, originalFilename);
            return;
        }

        long currentCount = submissionRepository.countBySessionId(sessionId);

        Submission submission = Submission.builder()
                .session(session)
                .s3Key(s3Key)
                .originalFilename(originalFilename)
                .studentDisplayName(originalFilename) // Placeholder; RosterResolver will update
                .identityStatus(IdentityStatus.UNVERIFIED)
                .extractionStatus(ExtractionStatus.PENDING)
                .isOversized(false)
                .position((int) currentCount + 1)
                .build();

        submissionRepository.save(submission);
        log.debug("Created submission for session={} filename={} position={}",
                sessionId, originalFilename, submission.getPosition());
    }

    private byte[] downloadFromS3(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            return response.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to download S3 object: " + objectKey, e);
        }
    }

    /**
     * Reads the full content of the current ZIP entry to determine its uncompressed size.
     * Returns the number of bytes read.
     */
    private long readEntrySize(ZipInputStream zis) throws IOException {
        byte[] buffer = new byte[8192];
        long totalRead = 0;
        int bytesRead;
        while ((bytesRead = zis.read(buffer)) != -1) {
            totalRead += bytesRead;
            // Early exit if we exceed the limit (avoids reading the full entry)
            if (totalRead > MAX_ZIP_UNCOMPRESSED_BYTES) {
                return totalRead;
            }
        }
        return totalRead;
    }

    /**
     * Validates that a ZIP entry path does not attempt path traversal.
     * Rejects entries containing ".." path segments or starting with "/".
     */
    private void validateNoPathTraversal(String entryName) throws PathTraversalException {
        // Normalize separators
        String normalized = entryName.replace('\\', '/');

        if (normalized.startsWith("/")) {
            throw new PathTraversalException(entryName);
        }

        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new PathTraversalException(entryName);
            }
        }
    }

    private boolean isSupportedExtension(String lowerFilename) {
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
    }

    /**
     * Extracts the filename from an S3 object key (last segment after '/').
     */
    private String extractFilename(String objectKey) {
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
    }

    /**
     * Extracts the filename from a ZIP entry path (last segment after '/').
     */
    private String extractFilenameFromPath(String entryPath) {
        // Normalize separators
        String normalized = entryPath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }

    /**
     * Represents a skipped entry within a ZIP archive.
     */
    private record SkippedEntry(String entryName, String reason) {
        @Override
        public String toString() {
            return entryName + " (" + reason + ")";
        }
    }

    /**
     * Exception thrown when a ZIP entry path contains path traversal.
     */
    static class PathTraversalException extends Exception {
        private final String offendingPath;

        PathTraversalException(String offendingPath) {
            super("Path traversal detected: " + offendingPath);
            this.offendingPath = offendingPath;
        }

        public String getOffendingPath() {
            return offendingPath;
        }
    }

    /**
     * Exception thrown when a ZIP archive exceeds entry count or size limits.
     */
    static class ZipLimitExceededException extends Exception {
        ZipLimitExceededException(String message) {
            super(message);
        }
    }
}
