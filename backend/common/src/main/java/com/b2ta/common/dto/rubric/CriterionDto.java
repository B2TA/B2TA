package com.b2ta.common.dto.rubric;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionDto {

    private UUID id;

    @NotBlank(message = "Criterion title is required")
    @Size(max = 200, message = "Criterion title must be 200 characters or fewer")
    private String title;

    private String description;

    @DecimalMin(value = "0.01", message = "Max points must be at least 0.01")
    @DecimalMax(value = "1000.00", message = "Max points must be at most 1000")
    private BigDecimal maxPoints;

    private String displayColor;

    @NotNull(message = "Position is required")
    @Min(value = 0, message = "Position must be non-negative")
    private Integer position;

    private Boolean requiresCompletion;

    @Valid
    @Size(min = 1, max = 10, message = "Must have between 1 and 10 performance levels")
    private List<PerformanceLevelDto> performanceLevels;
}
