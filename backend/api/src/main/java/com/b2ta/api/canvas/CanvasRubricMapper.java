package com.b2ta.api.canvas;

import com.b2ta.api.canvas.dto.CanvasAssignment;
import com.b2ta.api.canvas.dto.CanvasCriterionView;
import com.b2ta.api.canvas.dto.CanvasLevelView;
import com.b2ta.api.canvas.dto.CanvasRating;
import com.b2ta.api.canvas.dto.CanvasRubricCriterion;
import com.b2ta.api.canvas.dto.CanvasRubricView;
import com.b2ta.api.canvas.dto.CanvasStudentView;
import com.b2ta.api.canvas.dto.CanvasSubmission;
import com.b2ta.api.util.ColorPalette;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Translates Canvas payloads into the shapes the marking view renders.
 */
@Component
public class CanvasRubricMapper {

    /** Alpha applied to a criterion's color to produce its highlight fill. */
    private static final double HIGHLIGHT_ALPHA = 0.14;

    /**
     * Maps an assignment and its rubric.
     *
     * <p>Colors are assigned by criterion <em>index</em>, never by hashing the id. A hash
     * over ids reshuffles colors between reloads, which breaks the color-coding the whole
     * marking view rests on.
     */
    public CanvasRubricView toRubricView(CanvasAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");

        if (!assignment.hasRubric()) {
            return CanvasRubricView.builder()
                    .assignmentId(String.valueOf(assignment.getId()))
                    .assignmentName(assignment.getName())
                    .pointsPossible(assignment.getPointsPossible())
                    .hasRubric(false)
                    .criteria(List.of())
                    .build();
        }

        List<CanvasCriterionView> criteria = new ArrayList<>();
        List<CanvasRubricCriterion> source = assignment.getRubric();
        for (int i = 0; i < source.size(); i++) {
            criteria.add(toCriterionView(source.get(i), i));
        }

        return CanvasRubricView.builder()
                .assignmentId(String.valueOf(assignment.getId()))
                .assignmentName(assignment.getName())
                .pointsPossible(assignment.getPointsPossible())
                .hasRubric(true)
                .criteria(criteria)
                .build();
    }

    private CanvasCriterionView toCriterionView(CanvasRubricCriterion criterion, int index) {
        // Canvas imposes no criterion cap, so cycle rather than throw past the palette's
        // 30 colors. Repeating a color is a cosmetic collision; failing the whole rubric
        // load is not.
        String color = ColorPalette.getColor(index % ColorPalette.size());

        String description = criterion.getLongDescription();
        if (description == null || description.isBlank()) {
            description = criterion.getDescription();
        }

        return CanvasCriterionView.builder()
                .id(criterion.getId())
                .label(criterion.getDescription())
                .description(description)
                .maxPts(criterion.getPoints())
                .color(color)
                .bg(toRgba(color, HIGHLIGHT_ALPHA))
                .border(color)
                .levels(toLevels(criterion.getRatings()))
                .build();
    }

    /**
     * Orders levels by points descending, as the sidebar presents them. Canvas returns
     * them in that order today, but does not promise to.
     */
    private List<CanvasLevelView> toLevels(List<CanvasRating> ratings) {
        if (ratings == null) {
            return List.of();
        }
        return ratings.stream()
                .map(r -> CanvasLevelView.builder()
                        .id(r.getId())
                        .pts(r.getPoints())
                        .label(r.getDescription())
                        .desc(r.getLongDescription())
                        .build())
                .sorted(Comparator.comparing(
                        CanvasLevelView::getPts,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * Builds the grading queue, dropping unsubmitted entries (Requirement 2.2) and
     * numbering what remains so "Student N of M" counts only gradable work.
     */
    public List<CanvasStudentView> toQueue(List<CanvasSubmission> submissions) {
        if (submissions == null) {
            return List.of();
        }

        List<CanvasSubmission> gradable = submissions.stream()
                .filter(s -> !s.isUnsubmitted())
                .sorted(Comparator.comparing(
                        s -> displayName(s).toLowerCase(java.util.Locale.ROOT)))
                .toList();

        List<CanvasStudentView> queue = new ArrayList<>(gradable.size());
        for (int i = 0; i < gradable.size(); i++) {
            CanvasSubmission s = gradable.get(i);
            queue.add(CanvasStudentView.builder()
                    .userId(String.valueOf(s.getUserId()))
                    .name(displayName(s))
                    .submissionId(s.getId())
                    .submissionType(s.getSubmissionType())
                    .workflowState(s.getWorkflowState())
                    .position(i)
                    .alreadyGraded(s.isAlreadyGraded())
                    .attempt(s.getAttempt())
                    .build());
        }
        return queue;
    }

    private static String displayName(CanvasSubmission submission) {
        if (submission.getUser() != null && submission.getUser().getName() != null) {
            return submission.getUser().getName();
        }
        // include[]=user was omitted or the user is deleted; fall back to the id so the
        // queue entry is still selectable rather than blank.
        return "User " + submission.getUserId();
    }

    /**
     * Converts {@code #RRGGBB} to {@code rgba(r,g,b,alpha)} for the highlight fill.
     */
    static String toRgba(String hex, double alpha) {
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        int r = Integer.parseInt(cleaned.substring(0, 2), 16);
        int g = Integer.parseInt(cleaned.substring(2, 4), 16);
        int b = Integer.parseInt(cleaned.substring(4, 6), 16);
        return "rgba(" + r + "," + g + "," + b + "," + alpha + ")";
    }
}
