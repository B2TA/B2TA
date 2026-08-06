package com.b2ta.common.dto.session;

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
public class CreateSessionRequest {

    @NotBlank(message = "Session name is required")
    @Size(max = 255, message = "Session name must be 255 characters or fewer")
    private String name;
}
