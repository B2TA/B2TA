package com.b2ta.common.dto.grading;

import com.b2ta.common.dto.match.ConfirmedMatchDto;
import com.b2ta.common.dto.match.SuggestedMatchDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingRecordResponse {

    private UUID id;
    private UUID submissionId;
    private String overallFeedback;
    private Instant savedAt;
    private List<CriterionScoreDto> criterionScores;
    private List<SuggestedMatchDto> suggestedMatches;
    private List<ConfirmedMatchDto> confirmedMatches;
    private String extractedText;
}
