package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import com.b2ta.api.security.TenantGuard;
import com.b2ta.common.csv.CsvWriter;
import com.b2ta.common.dto.rubric.CriterionDto;
import com.b2ta.common.dto.rubric.PerformanceLevelDto;
import com.b2ta.common.dto.rubric.RubricExportResponse;
import com.b2ta.common.dto.rubric.RubricResponse;
import com.b2ta.common.dto.rubric.SaveRubricRequest;
import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.GradingSession;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Rubric;
import com.b2ta.common.error.ApiException;
import com.b2ta.common.error.ErrorCode;
import com.b2ta.common.repository.RubricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rubric read and write (Requirements 2.1-2.10, 3.x).
 *
 * <p>Task 5.x needs the rubric read path — the grading, review, and export services all resolve
 * criteria and performance levels through it — and the write path so a rubric can be entered without
 * the file-parsing pipeline. Colour assignment and file parsing belong to Team A's tasks 3.2 and 3.4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RubricService {

    /**
     * Distinct criterion colours.
     *
     * <p>Highlights are the primary way a passage is tied to a criterion, so two criteria sharing a
     * colour would make the document unreadable. Each has at least a 3:1 contrast ratio against white
     * so a highlight boundary is visible without relying on colour alone.
     */
    private static final List<String> COLOR_PALETTE = List.of(
            "#1F77B4", "#D62728", "#2CA02C", "#9467BD", "#8C564B",
            "#E377C2", "#7F7F7F", "#BCBD22", "#17BECF", "#FF7F0E",
            "#393B79", "#8C6D31", "#843C39", "#7B4173", "#3182BD",
            "#31A354", "#756BB1", "#636363", "#E6550D", "#6BAED6",
            "#B5CF6B", "#A55194", "#CE6DBD", "#9C9EDE", "#AD494A",
            "#C7C7C7", "#5254A3", "#637939", "#BD9E39", "#D6616B");

    /** Maximum criteria per rubric (Requirement 1.4). */
    private static final int MAX_CRITERIA = 30;

    private final TenantGuard tenantGuard;
    private final RubricRepository rubricRepository;

    @Transactional(readOnly = true)
    public RubricResponse get(TaPrincipal ta, UUID sessionId) {
        Rubric rubric = tenantGuard.requireRubric(ta, sessionId);
        return toResponse(rubric);
    }

    /** Returns the rubric if one exists, without failing when it does not. */
    @Transactional(readOnly = true)
    public Optional<RubricResponse> find(TaPrincipal ta, UUID sessionId) {
        tenantGuard.requireSession(ta, sessionId);
        return rubricRepository.findBySessionIdAndTaId(sessionId, ta.taId()).map(this::toResponse);
    }

    /**
     * Replaces the rubric wholesale.
     *
     * <p>A full replacement rather than a diff: the editor lets a TA reorder, add, and remove criteria
     * in one pass, and reconciling that as a patch would need client-assigned identities for rows that
     * do not exist yet. Criteria that keep their id keep it, so grading records that reference them
     * survive an edit.
     */
    @Transactional
    public RubricResponse save(TaPrincipal ta, UUID sessionId, SaveRubricRequest request) {
        GradingSession session = tenantGuard.requireSession(ta, sessionId);
        validate(request);

        Rubric rubric = rubricRepository.findBySessionId(sessionId)
                .orElseGet(() -> Rubric.builder()
                        .session(session)
                        .sourceFormat("manual")
                        .criteria(new ArrayList<>())
                        .build());

        List<CriterionDto> requested = request.getCriteria();
        List<Criterion> resolved = new ArrayList<>(requested.size());

        for (int position = 0; position < requested.size(); position++) {
            CriterionDto dto = requested.get(position);
            Criterion criterion = findExisting(rubric, dto.getId())
                    .orElseGet(() -> Criterion.builder()
                            .rubric(rubric)
                            .performanceLevels(new ArrayList<>())
                            .build());

            criterion.setTitle(dto.getTitle().trim());
            criterion.setDescription(dto.getDescription() == null ? "" : dto.getDescription());
            criterion.setMaxPoints(dto.getMaxPoints());
            criterion.setDisplayColor(dto.getDisplayColor() == null || dto.getDisplayColor().isBlank()
                    ? COLOR_PALETTE.get(position % COLOR_PALETTE.size())
                    : dto.getDisplayColor());
            criterion.setPosition((short) position);
            criterion.setRequiresCompletion(Boolean.TRUE.equals(dto.getRequiresCompletion()));

            applyLevels(criterion, dto.getPerformanceLevels());
            resolved.add(criterion);
        }

        rubric.getCriteria().clear();
        rubric.getCriteria().addAll(resolved);
        rubric.setUpdatedAt(Instant.now());

        Rubric saved = rubricRepository.save(rubric);
        log.info("Saved rubric {} for session {} with {} criteria",
                saved.getId(), sessionId, saved.getCriteria().size());
        return toResponse(saved);
    }

    private void applyLevels(Criterion criterion, List<PerformanceLevelDto> requested) {
        List<PerformanceLevel> resolved = new ArrayList<>();
        for (int position = 0; position < requested.size(); position++) {
            PerformanceLevelDto dto = requested.get(position);
            PerformanceLevel level = criterion.getPerformanceLevels().stream()
                    .filter(existing -> dto.getId() != null && dto.getId().equals(existing.getId()))
                    .findFirst()
                    .orElseGet(() -> PerformanceLevel.builder().criterion(criterion).build());

            level.setLabel(dto.getLabel().trim());
            level.setDescription(dto.getDescription() == null ? "" : dto.getDescription());
            level.setPoints(dto.getPoints());
            level.setPosition((short) position);
            resolved.add(level);
        }
        criterion.getPerformanceLevels().clear();
        criterion.getPerformanceLevels().addAll(resolved);
    }

    private Optional<Criterion> findExisting(Rubric rubric, UUID criterionId) {
        if (criterionId == null) {
            return Optional.empty();
        }
        return rubric.getCriteria().stream()
                .filter(criterion -> criterionId.equals(criterion.getId()))
                .findFirst();
    }

    /**
     * Checks the constraints bean validation cannot express.
     *
     * <p>Level points exceeding the criterion maximum is the one that matters most: it would let the
     * score calculator produce a total above the maximum, which a gradebook import would reject.
     */
    private void validate(SaveRubricRequest request) {
        List<CriterionDto> criteria = request.getCriteria();
        if (criteria.size() > MAX_CRITERIA) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A rubric may hold at most " + MAX_CRITERIA + " criteria");
        }
        for (CriterionDto criterion : criteria) {
            if (criterion.getPerformanceLevels() == null
                    || criterion.getPerformanceLevels().isEmpty()) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Criterion '" + criterion.getTitle()
                                + "' must have at least one performance level");
            }
            if (criterion.getMaxPoints() == null) {
                continue;
            }
            for (PerformanceLevelDto level : criterion.getPerformanceLevels()) {
                if (level.getPoints() != null
                        && level.getPoints().compareTo(criterion.getMaxPoints()) > 0) {
                    throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "Level '" + level.getLabel() + "' awards more than the maximum of "
                                    + criterion.getMaxPoints() + " for '"
                                    + criterion.getTitle() + "'");
                }
            }
        }
    }

    /** Rubric CSV export (Requirement 3.1-3.3). */
    @Transactional(readOnly = true)
    public RubricExportResponse exportCsv(TaPrincipal ta, UUID sessionId) {
        Rubric rubric = tenantGuard.requireRubric(ta, sessionId);
        CsvWriter csv = new CsvWriter();
        csv.writeRow(List.of("Criterion", "Description", "Max Points",
                "Level Label", "Level Description", "Level Points"));

        for (Criterion criterion : rubric.getCriteria()) {
            for (PerformanceLevel level : criterion.getPerformanceLevels()) {
                csv.writeRow(List.of(
                        criterion.getTitle(),
                        criterion.getDescription() == null ? "" : criterion.getDescription(),
                        criterion.getMaxPoints() == null ? "" : criterion.getMaxPoints().toPlainString(),
                        level.getLabel(),
                        level.getDescription() == null ? "" : level.getDescription(),
                        level.getPoints() == null ? "" : level.getPoints().toPlainString()));
            }
        }
        return RubricExportResponse.builder()
                .filename("rubric-" + sessionId + ".csv")
                .downloadUrl("data:text/csv;charset=utf-8,"
                        + java.net.URLEncoder.encode(csv.toCsv(),
                        java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }

    private RubricResponse toResponse(Rubric rubric) {
        return RubricResponse.builder()
                .id(rubric.getId())
                .sessionId(rubric.getSession().getId())
                .sourceFormat(rubric.getSourceFormat())
                .createdAt(rubric.getCreatedAt())
                .updatedAt(rubric.getUpdatedAt())
                .criteria(rubric.getCriteria().stream()
                        .map(criterion -> CriterionDto.builder()
                                .id(criterion.getId())
                                .title(criterion.getTitle())
                                .description(criterion.getDescription())
                                .maxPoints(criterion.getMaxPoints())
                                .displayColor(criterion.getDisplayColor())
                                .position((int) criterion.getPosition())
                                .requiresCompletion(criterion.getRequiresCompletion())
                                .performanceLevels(criterion.getPerformanceLevels().stream()
                                        .map(level -> PerformanceLevelDto.builder()
                                                .id(level.getId())
                                                .label(level.getLabel())
                                                .description(level.getDescription())
                                                .points(level.getPoints())
                                                .position((int) level.getPosition())
                                                .build())
                                        .toList())
                                .build())
                        .toList())
                .build();
    }
}
