package com.b2ta.common.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private UUID sessionId;
    private Instant reviewConfirmedAt;
    private int totalSubmissions;
    private int flaggedCount;
    private int unflaggedCount;
    private List<SubmissionSummaryDto> submissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmissionSummaryDto {
        private UUID submissionId;
        private String studentDisplayName;
        private BigDecimal totalPoints;
        private BigDecimal maxPoints;
        private List<CriterionScoreSummary> criterionScores;
        private List<String> flags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionScoreSummary {
        private UUID criterionId;
        private String criterionTitle;
        private BigDecimal points;
        private String selectedLevelLabel;
    }
}
