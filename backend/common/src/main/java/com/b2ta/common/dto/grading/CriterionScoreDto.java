package com.b2ta.common.dto.grading;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One criterion's score within a grading record.
 *
 * <p>The upper bound on {@code overridePoints} is the criterion's own {@code maxPoints}, which is
 * not knowable from the payload alone, so it is enforced in the service layer
 * (Requirement 11.4-11.5). Bean validation covers the parts that are context-free: the criterion
 * reference, non-negativity, and the 2-decimal-place limit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriterionScoreDto {

    private UUID id;

    @NotNull(message = "Criterion ID is required")
    private UUID criterionId;

    private UUID selectedLevelId;

    @DecimalMin(value = "0.00", message = "Override points must be 0 or greater")
    @Digits(integer = 5, fraction = 2, message = "Override points must have at most 2 decimal places")
    private BigDecimal overridePoints;

    @Size(max = 2000, message = "Criterion feedback must be 2,000 characters or fewer")
    private String criterionFeedback;
}
