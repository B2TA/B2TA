package com.b2ta.api.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A student submission for an assignment.
 * Maps {@code GET /courses/{course}/assignments/{id}/submissions}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasSubmission {

    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("assignment_id")
    private Long assignmentId;

    /**
     * Canvas workflow state. Submissions in {@code unsubmitted} are excluded from the
     * grading queue (Requirement 2.2).
     */
    @JsonProperty("workflow_state")
    private String workflowState;

    /** One of {@code online_text_entry}, {@code online_upload}, etc. May be null. */
    @JsonProperty("submission_type")
    private String submissionType;

    /** HTML body for {@code online_text_entry} submissions. */
    private String body;

    /** Increments on each resubmission; analyses are cached per attempt. */
    private Integer attempt;

    private Double score;

    @JsonProperty("graded_at")
    private String gradedAt;

    private CanvasUser user;

    private List<CanvasAttachment> attachments;

    /**
     * Existing per-criterion scores, keyed by Canvas criterion id. Present when the
     * submission has already been graded (Requirement 2.5).
     */
    @JsonProperty("rubric_assessment")
    private Map<String, CanvasRubricAssessmentEntry> rubricAssessment;

    public boolean isUnsubmitted() {
        return "unsubmitted".equals(workflowState);
    }

    public boolean isAlreadyGraded() {
        return rubricAssessment != null && !rubricAssessment.isEmpty();
    }
}
