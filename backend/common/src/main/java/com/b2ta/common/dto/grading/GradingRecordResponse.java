package com.b2ta.common.dto.grading;

import com.b2ta.common.dto.match.ConfirmedMatchDto;
import com.b2ta.common.dto.match.SuggestedMatchDto;
import com.b2ta.common.entity.enums.ExtractionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the marking view needs for one submission, in a single response.
 *
 * <p>Bundled rather than split across separate calls because Requirement 13.8 gives navigation to
 * the next submission a 2-second budget; four sequential round trips would spend most of it on
 * latency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingRecordResponse {

    private UUID id;
    private UUID submissionId;
    private String studentDisplayName;
    private String overallFeedback;
    private Instant savedAt;

    private List<CriterionScoreDto> criterionScores;

    /** Unconfirmed suggestions only; confirmed and rejected ones are not re-presented (Req 10.2). */
    private List<SuggestedMatchDto> suggestedMatches;

    private List<ConfirmedMatchDto> confirmedMatches;

    /** Per-criterion analysis state, so the panel can distinguish no-evidence from unavailable. */
    private List<CriterionAnalysisDto> criterionAnalysis;

    /** Full extracted text; null when extraction failed. Offsets in matches index into this. */
    private String extractedText;

    private ExtractionStatus extractionStatus;
    private String extractionFailureReason;
    private Boolean isOversized;

    /** Ordinal position within the batch, 1-based, for the progress indicator (Req 13.1). */
    private Integer position;

    /** Total submissions in the batch (Req 13.1). */
    private Integer batchSize;

    /** Server-computed total, so the client can verify its own arithmetic (Req 11.3). */
    private BigDecimal totalScore;
    private BigDecimal maxScore;
    private Integer unscoredCriterionCount;
}
