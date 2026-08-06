package com.b2ta.common.dto.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIdentityRequest {

    @NotBlank(message = "Student display name is required")
    @Size(max = 200, message = "Display name must be 200 characters or fewer")
    private String studentDisplayName;
}
