package com.b2ta.common.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Review screen data: one row per submission with scores, totals, and flags (Requirement 15.2).
 *
 * <p>Counts are plain integers and are always present, including when they are zero
 * (Requirement 15.6) — an omitted count reads as "unknown" rather than "none".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private UUID sessionId;

    /** When the TA last confirmed this review; null means an export is still blocked (Req 15.1). */
    private Instant reviewConfirmedAt;

    private int totalSubmissions;
    private int flaggedCount;
    private int unflaggedCount;

    /** Criterion headers in rubric order, so the client can render columns without a second call. */
    private List<CriterionHeader> criteria;

    private List<SubmissionSummaryDto> submissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionHeader {
        private UUID criterionId;
        private String title;
        private BigDecimal maxPoints;
        private int position;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionSummaryDto {
        private UUID submissionId;
        private String studentDisplayName;

        /** Ordinal position in the batch, 1-based. */
        private int position;

        /** Sum of awarded points across scored criteria (Req 15.2). */
        private BigDecimal totalPoints;

        /** Sum of every criterion maximum, whether scored or not (Req 15.2). */
        private BigDecimal maxPoints;

        /** Criteria with neither a level nor an override, shown on the row (Req 15.3). */
        private int unscoredCriterionCount;

        /** Criteria carrying a manual override (Req 15.5). */
        private int overrideCount;

        private List<CriterionScoreSummary> criterionScores;

        private List<ReviewFlag> flags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionScoreSummary {
        private UUID criterionId;
        private String criterionTitle;

        /** Awarded points, or null when the criterion is unscored (never zero for unscored). */
        private BigDecimal points;

        private String selectedLevelLabel;

        /** True when these points came from a manual override rather than a level (Req 15.5). */
        private boolean overridden;
    }
}
