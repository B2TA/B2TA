package com.b2ta.common.dto.match;

import com.b2ta.common.entity.enums.MatchState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedMatchDto {

    private UUID id;
    private UUID submissionId;
    private UUID criterionId;
    private Integer passageStart;
    private Integer passageEnd;
    private String rationale;
    private BigDecimal confidence;
    private MatchState matchState;
    private Boolean isStale;
    private Instant createdAt;
}
