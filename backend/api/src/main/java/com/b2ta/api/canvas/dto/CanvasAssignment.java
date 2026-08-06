package com.b2ta.api.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A Canvas assignment with its attached rubric.
 * Maps {@code GET /courses/{course}/assignments/{id}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasAssignment {

    private Long id;

    private String name;

    @JsonProperty("points_possible")
    private Double pointsPossible;

    @JsonProperty("course_id")
    private Long courseId;

    @JsonProperty("submission_types")
    private List<String> submissionTypes;

    /**
     * The rubric criteria. Absent or empty when no rubric is attached to the
     * assignment — callers must surface that as an explicit empty state rather than
     * falling back to demo data (Requirement 1.3).
     */
    private List<CanvasRubricCriterion> rubric;

    @JsonProperty("use_rubric_for_grading")
    private Boolean useRubricForGrading;

    public boolean hasRubric() {
        return rubric != null && !rubric.isEmpty();
    }
}
