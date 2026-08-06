package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.config.AwsProperties;
import com.b2ta.common.csv.CsvWriter;
import com.b2ta.common.dto.export.ExportResponse;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionScore;
import com.b2ta.common.entity.GradingRecord;
import com.b2ta.common.entity.Submission;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.repository.CriterionRepository;
import com.b2ta.common.repository.GradingRecordRepository;
import com.b2ta.common.repository.SubmissionRepository;
import com.b2ta.common.score.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Produces grade export files and returns a pre-signed download URL (task 5.11,
 * Requirements 16.1-16.11).
 *
 * <p>Two invariants shape this code:
 *
 * <ul>
 *   <li><b>Nothing is recalculated.</b> The awarded points written to the file are the values held in
 *       the grading store (Requirement 16.3). If a TA overrode a criterion, the override is what gets
 *       exported; the exporter does not re-derive anything from performance levels.
 *   <li><b>An unscored criterion exports as empty, not zero</b> (Requirement 16.5). A zero is a grade;
 *       an empty cell is an absent grade, and the two must not be conflated in a gradebook import.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final TenantGuard tenantGuard;
    private final ReviewService reviewService;
    private final SubmissionRepository submissionRepository;
    private final CriterionRepository criterionRepository;
    private final GradingRecordRepository gradingRecordRepository;
    private final ScoreCalculator scoreCalculator;
    private final S3StorageService storage;
    private final S3KeyBuilder keyBuilder;
    private final AwsProperties awsProperties;

    /**
     * Generic export: one row per submission with per-criterion points, level labels, totals, and
     * feedback (Requirement 16.1).
     */
    @Transactional(readOnly = true)
    public ExportResponse exportGeneric(TaPrincipal ta, UUID sessionId) {
        ExportData data = loadData(ta, sessionId);
        CsvWriter csv = new CsvWriter();

        List<String> header = new ArrayList<>();
        header.add("Student");
        for (Criterion criterion : data.criteria()) {
            header.add(criterion.getTitle() + " Points");
            header.add(criterion.getTitle() + " Level");
        }
        header.add("Total Score");
        header.add("Maximum Score");
        header.add("Feedback");
        csv.writeRow(header);

        for (SubmissionRow row : data.rows()) {
            List<String> fields = new ArrayList<>();
            fields.add(row.studentDisplayName());
            for (Criterion criterion : data.criteria()) {
                CriterionScore score = row.scoresByCriterion().get(criterion.getId());
                BigDecimal points = scoreCalculator.awardedPoints(score);
                fields.add(points == null ? "" : points.toPlainString());
                fields.add(score == null || score.getSelectedLevel() == null
                        ? "" : score.getSelectedLevel().getLabel());
            }
            fields.add(row.total() == null ? "" : row.total().toPlainString());
            fields.add(row.maxTotal().toPlainString());
            fields.add(row.feedback());
            csv.writeRow(fields);
        }

        return store(ta, sessionId, "grades-generic", csv);
    }

    /**
     * Canvas gradebook export (Requirement 16.2).
     *
     * <p>Canvas expects its own fixed leading columns and a per-assignment score column, and it
     * ignores rows it cannot match. The two placeholder ID columns are part of that format; they are
     * emitted empty because this system matches students by display name, and Canvas falls back to
     * name matching when the ID columns are blank.
     */
    @Transactional(readOnly = true)
    public ExportResponse exportCanvas(TaPrincipal ta, UUID sessionId) {
        ExportData data = loadData(ta, sessionId);
        CsvWriter csv = new CsvWriter();

        BigDecimal pointsPossible = data.criteria().stream()
                .map(Criterion::getMaxPoints)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        csv.writeRow(List.of("Student", "ID", "SIS User ID", "SIS Login ID", "Section",
                data.assignmentColumnName()));
        // Canvas reads the "Points Possible" row to validate the score column before importing.
        csv.writeRow(List.of("    Points Possible", "", "", "", "",
                pointsPossible.toPlainString()));

        for (SubmissionRow row : data.rows()) {
            csv.writeRow(List.of(
                    row.studentDisplayName(),
                    "",
                    "",
                    "",
                    "",
                    row.total() == null ? "" : row.total().toPlainString()));
        }

        return store(ta, sessionId, "grades-canvas", csv);
    }

    private ExportResponse store(TaPrincipal ta, UUID sessionId, String name, CsvWriter csv) {
        if (!awsProperties.isS3Configured()) {
            throw ApiException.unprocessable(ErrorCode.EXPORT_FAILED,
                    "File storage is not configured on this deployment, so an export file cannot be "
                            + "produced");
        }
        Instant now = Instant.now();
        String objectKey = keyBuilder.export(ta, sessionId, name, now);
        try {
            storage.putCsv(objectKey, csv.toUtf8Bytes());
            String url = storage.presignedDownloadUrl(objectKey);
            log.info("Export {} for session {} written to a pre-signed URL valid for {} minutes",
                    name, sessionId, awsProperties.getS3().getDownloadUrlTtlMinutes());
            return ExportResponse.builder()
                    .downloadUrl(url)
                    .filename(objectKey.substring(objectKey.lastIndexOf('/') + 1))
                    .build();
        } catch (RuntimeException e) {
            // Requirement 16.9: the grading store is untouched by a failed export, so the TA can
            // simply retry.
            log.error("Export {} failed for session {}", name, sessionId, e);
            throw ApiException.unprocessable(ErrorCode.EXPORT_FAILED,
                    "The export file could not be written. No grades were changed; try again.");
        }
    }

    /**
     * Loads everything an export needs, after enforcing the review gate.
     *
     * <p>The gate is checked here rather than in the controller so both formats go through it.
     */
    private ExportData loadData(TaPrincipal ta, UUID sessionId) {
        reviewService.requireConfirmedReview(ta, sessionId);
        var session = tenantGuard.requireSession(ta, sessionId);

        List<Criterion> criteria = criterionRepository.findBySessionIdWithLevels(sessionId);
        List<Submission> submissions = submissionRepository.findBySessionIdOrderByPosition(sessionId);
        Map<UUID, GradingRecord> records = gradingRecordRepository
                .findBySessionIdWithScores(sessionId).stream()
                .collect(Collectors.toMap(record -> record.getSubmission().getId(),
                        Function.identity(), (first, second) -> first));

        List<SubmissionRow> rows = new ArrayList<>(submissions.size());
        for (Submission submission : submissions) {
            GradingRecord record = records.get(submission.getId());
            List<CriterionScore> scores = record == null ? List.of() : record.getCriterionScores();
            ScoreCalculator.ScoreSummary summary = scoreCalculator.summarize(criteria, scores);

            Map<UUID, CriterionScore> byCriterion = scores.stream()
                    .filter(score -> score.getCriterion() != null)
                    .collect(Collectors.toMap(score -> score.getCriterion().getId(),
                            Function.identity(), (first, second) -> first));

            // A submission with nothing scored at all exports an empty total rather than 0.00
            // (Requirement 16.5).
            BigDecimal total = summary.unscoredCount() == criteria.size() && !criteria.isEmpty()
                    ? null
                    : summary.total();

            rows.add(new SubmissionRow(
                    submission.getStudentDisplayName() == null
                            ? "" : submission.getStudentDisplayName(),
                    byCriterion,
                    total,
                    summary.maxTotal(),
                    record == null || record.getOverallFeedback() == null
                            ? "" : record.getOverallFeedback()));
        }

        return new ExportData(criteria, rows, session.getName());
    }

    private record ExportData(List<Criterion> criteria, List<SubmissionRow> rows,
                              String assignmentColumnName) {
    }

    private record SubmissionRow(String studentDisplayName,
                                 Map<UUID, CriterionScore> scoresByCriterion,
                                 BigDecimal total,
                                 BigDecimal maxTotal,
                                 String feedback) {
    }
}
