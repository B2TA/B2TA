package com.b2ta.worker.handler;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Rubric;
import com.b2ta.common.entity.enums.JobStatus;
import com.b2ta.common.entity.enums.JobType;
import com.b2ta.worker.messaging.JobHandler;
import com.b2ta.worker.messaging.JobMessage;
import com.b2ta.worker.messaging.JobStatusUpdater;
import com.b2ta.worker.parsing.*;
import com.b2ta.worker.repository.GradingSessionRepository;
import com.b2ta.worker.repository.RubricRepository;
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
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Handles RUBRIC_PARSE jobs.
 * Downloads the rubric file from S3, determines format from file extension,
 * routes to the appropriate parser, and persists the parsed Rubric with
 * criteria and performance levels.
 * <p>
 * Enforces a 10-second timeout on the entire parse operation.
 * On failure, records the filename, format, and reason.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RubricParseHandler implements JobHandler {

    private static final long PARSE_TIMEOUT_SECONDS = 10;

    /**
     * Fixed palette of 30 distinct hex colors with >= 3:1 contrast ratio against white.
     */
    private static final List<String> COLOR_PALETTE = List.of(
            "#D32F2F", "#1976D2", "#388E3C", "#E65100", "#7B1FA2",
            "#00796B", "#C2185B", "#5D4037", "#455A64", "#E64A19",
            "#0097A7", "#689F38", "#512DA8", "#0288D1", "#33691E",
            "#303F9F", "#D81B60", "#00838F", "#6A1B9A", "#2E7D32",
            "#AD1457", "#4527A0", "#1565C0", "#EF6C00", "#283593",
            "#B71C1C", "#004D40", "#827717", "#4E342E", "#37474F"
    );

    private final S3Client s3Client;
    private final CsvRubricParser csvRubricParser;
    private final XlsxRubricParser xlsxRubricParser;
    private final PdfRubricParser pdfRubricParser;
    private final RubricRepository rubricRepository;
    private final GradingSessionRepository sessionRepository;
    private final JobStatusUpdater jobStatusUpdater;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Override
    public JobType getJobType() {
        return JobType.RUBRIC_PARSE;
    }

    @Override
    public void handle(JobMessage message) throws Exception {
        UUID jobId = UUID.fromString(message.getJobId());
        UUID sessionId = UUID.fromString(message.getSessionId());
        String s3Key = (String) message.getPayload().get("s3Key");
        String filename = (String) message.getPayload().getOrDefault("filename", s3Key);

        log.info("Processing RUBRIC_PARSE job {} for session {}, s3Key={}", jobId, sessionId, s3Key);

        String format = detectFormat(s3Key);
        if (format == null) {
            String reason = "Unsupported file format. Expected .pdf, .csv, or .xlsx";
            jobStatusUpdater.updateStatus(jobId, JobStatus.FAILED,
                    buildFailureReason(filename, "unknown", reason));
            return;
        }

        // Download file from S3
        byte[] fileBytes;
        try {
            fileBytes = downloadFromS3(s3Key);
        } catch (Exception e) {
            log.error("Failed to download rubric from S3: {}", s3Key, e);
            jobStatusUpdater.updateStatus(jobId, JobStatus.FAILED,
                    buildFailureReason(filename, format, "Failed to download file from storage: " + e.getMessage()));
            return;
        }

        // Parse with timeout
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ParsedRubric> future = executor.submit(() -> parseRubric(fileBytes, format));

        ParsedRubric parsedRubric;
        try {
            parsedRubric = future.get(PARSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("RUBRIC_PARSE job {} timed out after {}s", jobId, PARSE_TIMEOUT_SECONDS);
            jobStatusUpdater.updateStatus(jobId, JobStatus.FAILED,
                    buildFailureReason(filename, format, "PARSE_TIMEOUT"));
            return;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String reason;
            if (cause instanceof RubricParseException) {
                reason = cause.getMessage();
            } else {
                reason = "Unexpected error during parsing: " + cause.getMessage();
            }
            log.warn("RUBRIC_PARSE job {} failed: {}", jobId, reason);
            jobStatusUpdater.updateStatus(jobId, JobStatus.FAILED,
                    buildFailureReason(filename, format, reason));
            return;
        } finally {
            executor.shutdownNow();
        }

        // Persist the parsed rubric
        try {
            persistRubric(parsedRubric, sessionId, s3Key, format);
            jobStatusUpdater.updateStatus(jobId, JobStatus.COMPLETED, null);
            log.info("RUBRIC_PARSE job {} completed successfully. {} criteria parsed.",
                    jobId, parsedRubric.getCriteria().size());
        } catch (Exception e) {
            log.error("Failed to persist parsed rubric for job {}", jobId, e);
            jobStatusUpdater.updateStatus(jobId, JobStatus.FAILED,
                    buildFailureReason(filename, format, "Failed to save rubric: " + e.getMessage()));
        }
    }

    private ParsedRubric parseRubric(byte[] fileBytes, String format) throws RubricParseException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
        return switch (format) {
            case "csv" -> csvRubricParser.parse(inputStream);
            case "xlsx" -> xlsxRubricParser.parse(inputStream);
            case "pdf" -> pdfRubricParser.parse(inputStream);
            default -> throw new RubricParseException("Unsupported format: " + format);
        };
    }

    @Transactional
    protected void persistRubric(ParsedRubric parsedRubric, UUID sessionId, String s3Key, String format) {
        GradingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("GradingSession not found: " + sessionId));

        // Delete existing rubric for this session if any
        rubricRepository.findBySessionId(sessionId).ifPresent(existing -> {
            rubricRepository.delete(existing);
            rubricRepository.flush();
        });

        Rubric rubric = Rubric.builder()
                .session(session)
                .s3Key(s3Key)
                .sourceFormat(format)
                .criteria(new java.util.ArrayList<>())
                .build();

        List<ParsedRubric.ParsedCriterion> parsedCriteria = parsedRubric.getCriteria();
        for (int i = 0; i < parsedCriteria.size(); i++) {
            ParsedRubric.ParsedCriterion pc = parsedCriteria.get(i);

            Criterion criterion = Criterion.builder()
                    .rubric(rubric)
                    .title(truncate(pc.getTitle(), 200))
                    .description(truncate(pc.getDescription() != null ? pc.getDescription() : "", 2000))
                    .maxPoints(pc.getMaxPoints())
                    .displayColor(getColor(i))
                    .position(i)
                    .requiresCompletion(pc.isRequiresCompletion())
                    .performanceLevels(new java.util.ArrayList<>())
                    .build();

            if (pc.getLevels() != null) {
                for (int j = 0; j < pc.getLevels().size(); j++) {
                    ParsedRubric.ParsedLevel pl = pc.getLevels().get(j);

                    PerformanceLevel level = PerformanceLevel.builder()
                            .criterion(criterion)
                            .label(truncate(pl.getLabel(), 200))
                            .description(truncate(pl.getDescription() != null ? pl.getDescription() : "", 2000))
                            .points(pl.getPoints())
                            .position(j)
                            .build();

                    criterion.getPerformanceLevels().add(level);
                }
            }

            rubric.getCriteria().add(criterion);
        }

        rubricRepository.save(rubric);
    }

    private String detectFormat(String s3Key) {
        if (s3Key == null) return null;
        String lower = s3Key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return "csv";
        if (lower.endsWith(".xlsx")) return "xlsx";
        if (lower.endsWith(".pdf")) return "pdf";
        return null;
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

    private String getColor(int index) {
        return COLOR_PALETTE.get(index % COLOR_PALETTE.size());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String buildFailureReason(String filename, String format, String reason) {
        return String.format("file=%s, format=%s, reason=%s", filename, format, reason);
    }
}
