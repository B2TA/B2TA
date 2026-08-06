package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasCriterionView;
import com.b2ta.api.canvas.dto.CanvasRubricAssessmentEntry;
import com.b2ta.api.canvas.dto.CanvasRubricView;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.b2ta.api.canvas.dto.SyncRequest;
import com.b2ta.api.canvas.dto.SyncResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pushes a TA's grading decisions to the Canvas gradebook.
 *
 * <p>The governing rule is that the AI proposes and the TA disposes: this service writes
 * only points that arrived in the request as an explicit TA selection. An AI suggestion
 * never reaches Canvas on its own (Requirement 5.3).
 */
@Service
@Slf4j
public class CanvasSyncService {

    private final CanvasClient canvasClient;
    private final CanvasRubricMapper mapper;
    private final CanvasProperties properties;

    public CanvasSyncService(CanvasClient canvasClient,
                             CanvasRubricMapper mapper,
                             CanvasProperties properties) {
        this.canvasClient = canvasClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * Validates and writes one submission's rubric assessment.
     *
     * @param syncedBy identity of the TA performing the sync, recorded in the log
     * @throws IncompleteGradingException when any rubric criterion is unscored
     * @throws CanvasException            when Canvas rejects the write
     */
    public SyncResponse sync(String assignmentId,
                             String userId,
                             SyncRequest request,
                             String syncedBy) {

        CanvasAssignment assignment =
                canvasClient.getAssignment(properties.getCourseId(), assignmentId);
        CanvasRubricView rubric = mapper.toRubricView(assignment);

        if (!rubric.isHasRubric()) {
            throw new IncompleteGradingException(
                    "This assignment has no rubric attached, so there is nothing to sync.",
                    List.of());
        }

        Map<String, CanvasRubricAssessmentEntry> assessment =
                buildAssessment(rubric, request);

        CanvasSubmission updated = canvasClient.submitAssessment(
                properties.getCourseId(), assignmentId, userId, assessment, request.getComment());

        Instant syncedAt = Instant.now();
        boolean fixtureMode = properties.getDataSource() == CanvasProperties.DataSource.FIXTURES;

        // Who synced what and when (Requirement 5.6). Criterion count only, never the
        // scores or comment text (Requirement 6.3).
        log.info("Canvas sync by {} for user {} on assignment {} at {}: {} criteria{}",
                syncedBy, userId, assignmentId, syncedAt, assessment.size(),
                fixtureMode ? " (FIXTURE MODE — not written to Canvas)" : "");

        return SyncResponse.builder()
                .synced(true)
                .userId(userId)
                .canvasTotal(updated == null ? null : updated.getScore())
                .syncedAt(syncedAt)
                .syncedBy(syncedBy)
                .criteriaWritten(assessment.size())
                .fixtureMode(fixtureMode)
                .build();
    }

    /**
     * Builds the {@code rubric_assessment} map, refusing to proceed unless every rubric
     * criterion carries an explicit score.
     */
    private Map<String, CanvasRubricAssessmentEntry> buildAssessment(CanvasRubricView rubric,
                                                                     SyncRequest request) {
        Map<String, SyncRequest.CriterionSelection> selections = request.getSelections().stream()
                .filter(s -> s.getCriterionId() != null)
                .collect(Collectors.toMap(
                        SyncRequest.CriterionSelection::getCriterionId,
                        s -> s,
                        (a, b) -> b,
                        LinkedHashMap::new));

        Set<String> rubricIds = rubric.getCriteria().stream()
                .map(CanvasCriterionView::getId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        // A selection naming a criterion this rubric does not contain means the client is
        // out of sync with Canvas. Sending it would be silently discarded by Canvas, so
        // fail loudly instead.
        List<String> unknown = selections.keySet().stream()
                .filter(id -> !rubricIds.contains(id))
                .toList();
        if (!unknown.isEmpty()) {
            throw new IncompleteGradingException(
                    "These criteria are not part of the current Canvas rubric: "
                            + String.join(", ", unknown)
                            + ". Reload the rubric and try again.",
                    unknown);
        }

        List<String> unscored = rubric.getCriteria().stream()
                .filter(c -> {
                    SyncRequest.CriterionSelection selection = selections.get(c.getId());
                    return selection == null || selection.getPoints() == null;
                })
                .map(CanvasCriterionView::getLabel)
                .toList();

        if (!unscored.isEmpty()) {
            throw new IncompleteGradingException(
                    "Every criterion needs a score before syncing. Still unscored: "
                            + String.join(", ", unscored),
                    unscored);
        }

        Map<String, CanvasRubricAssessmentEntry> assessment = new LinkedHashMap<>();
        for (CanvasCriterionView criterion : rubric.getCriteria()) {
            SyncRequest.CriterionSelection selection = selections.get(criterion.getId());
            assessment.put(criterion.getId(), CanvasRubricAssessmentEntry.builder()
                    .points(selection.getPoints())
                    // Canvas expects the literal "blank" when a raw point value was
                    // entered rather than a named rating being picked.
                    .ratingId(selection.getRatingId() == null ? "blank" : selection.getRatingId())
                    .comments(selection.getComments() == null ? "" : selection.getComments())
                    .build());
        }
        return assessment;
    }

    /**
     * The sync was blocked before any call to Canvas. Carries the offending criteria so
     * the UI can point at them directly.
     */
    public static class IncompleteGradingException extends RuntimeException {

        private final transient List<String> criteria;

        public IncompleteGradingException(String message, List<String> criteria) {
            super(message);
            this.criteria = criteria;
        }

        public List<String> getCriteria() {
            return criteria;
        }
    }
}
