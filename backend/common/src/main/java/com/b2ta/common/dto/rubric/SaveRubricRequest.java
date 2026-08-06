package com.b2ta.common.dto.rubric;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class SaveRubricRequest {

    @Valid
    @NotEmpty(message = "At least one criterion is required")
    @Size(max = 30, message = "Maximum 30 criteria allowed")
    private List<CriterionDto> criteria;
}
