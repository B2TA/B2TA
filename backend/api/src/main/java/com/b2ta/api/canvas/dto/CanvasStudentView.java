package com.b2ta.api.canvas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry in the grading queue, backing the "Student N of M" control.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasStudentView {

    private String userId;

    private String name;

    private Long submissionId;

    private String submissionType;

    private String workflowState;

    /** Zero-based position in the queue. */
    private int position;

    /** True when Canvas already holds a rubric assessment for this submission. */
    private boolean alreadyGraded;

    private Integer attempt;
}
