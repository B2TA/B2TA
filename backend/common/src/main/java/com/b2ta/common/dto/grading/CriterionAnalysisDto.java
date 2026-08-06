package com.b2ta.common.dto.grading;

import com.b2ta.common.entity.enums.AnalysisState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Match_Engine state for one criterion of the current submission.
 *
 * <p>The marking view needs this to choose between three states that all present as zero suggested
 * matches: analysis still running, analysis complete with no evidence found (Requirement 6.6), and
 * analysis unavailable (Requirements 6.7, 6.8). Without it the panel would show "no evidence found"
 * for a criterion whose analysis actually failed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionAnalysisDto {

    private UUID criterionId;

    private AnalysisState state;

    /** Reason analysis is unavailable; null in every other state. */
    private String failureReason;

    /** Characters that were analysed, which is less than the document for an oversized submission. */
    private Integer analyzedCharCount;
}
