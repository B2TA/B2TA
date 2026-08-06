package com.b2ta.common.dto.rubric;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class PerformanceLevelDto {

    private UUID id;

    @NotBlank(message = "Level label is required")
    @Size(max = 200, message = "Level label must be 200 characters or fewer")
    private String label;

    private String description;

    private BigDecimal points;

    @NotNull(message = "Position is required")
    @Min(value = 0, message = "Position must be non-negative")
    private Integer position;
}
