package com.b2ta.common.dto.match;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateManualMatchRequest {

    @NotNull(message = "Criterion ID is required")
    private UUID criterionId;

    @NotNull(message = "Passage start is required")
    @Min(value = 0, message = "Passage start must be non-negative")
    private Integer passageStart;

    @NotNull(message = "Passage end is required")
    @Min(value = 1, message = "Passage end must be positive")
    private Integer passageEnd;

    private String rationale;
}
