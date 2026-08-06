package com.b2ta.common.dto.rubric;

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
public class RubricResponse {

    private UUID id;
    private UUID sessionId;
    private String sourceFormat;
    private Instant createdAt;
    private Instant updatedAt;
    private List<CriterionDto> criteria;
}
