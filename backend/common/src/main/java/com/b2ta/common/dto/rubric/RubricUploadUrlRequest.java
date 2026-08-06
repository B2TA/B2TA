package com.b2ta.common.dto.rubric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricUploadUrlRequest {

    @NotBlank(message = "Filename is required")
    private String filename;

    @Positive(message = "File size must be positive")
    private Long size;
}
