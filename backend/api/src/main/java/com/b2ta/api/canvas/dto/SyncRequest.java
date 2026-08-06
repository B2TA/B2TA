package com.b2ta.api.canvas.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A TA's request to push one submission's grades to the Canvas gradebook.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequest {

    /** Scores the TA explicitly selected. */
    @NotEmpty(message = "At least one criterion score is required")
    private List<CriterionSelection> selections;

    /** Overall feedback for the student; omitted from the write when blank. */
    private String comment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionSelection {

        /** Verbatim Canvas criterion id (e.g. {@code _1838}). */
        private String criterionId;

        /**
         * Points the TA selected. Null means the criterion is still unscored, which
         * blocks the whole sync (Requirement 5.2).
         */
        private Double points;

        /** Canvas rating id when the TA picked a named level, else null. */
        private String ratingId;

        /** Optional per-criterion comment. */
        private String comments;
    }
}
