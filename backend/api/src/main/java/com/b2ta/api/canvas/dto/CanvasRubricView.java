package com.b2ta.api.canvas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * An assignment's rubric as the marking view consumes it.
 *
 * <p>When {@code hasRubric} is false the client shows an explicit
 * "No rubric attached to this assignment" state. It must not substitute demo data
 * (Requirement 1.3).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasRubricView {

    private String assignmentId;

    private String assignmentName;

    private Double pointsPossible;

    private boolean hasRubric;

    private List<CanvasCriterionView> criteria;
}
