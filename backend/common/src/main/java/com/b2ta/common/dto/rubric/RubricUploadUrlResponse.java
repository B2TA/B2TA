package com.b2ta.common.dto.rubric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RubricUploadUrlResponse {

    private String uploadUrl;
    private String objectKey;
}
