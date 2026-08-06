package com.b2ta.common.dto.grading;

import com.b2ta.common.dto.match.ConfirmedMatchDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveGradingRecordRequest {

    @Size(max = 10000, message = "Overall feedback must be 10,000 characters or fewer")
    private String overallFeedback;

    @Valid
    private List<CriterionScoreDto> criterionScores;

    @Valid
    private List<ConfirmedMatchDto> confirmedMatches;
}
