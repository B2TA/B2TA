package com.b2ta.common.dto.grading;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionScoreDto {

    private UUID id;
    private UUID criterionId;
    private UUID selectedLevelId;
    private BigDecimal overridePoints;
    private String criterionFeedback;
}
