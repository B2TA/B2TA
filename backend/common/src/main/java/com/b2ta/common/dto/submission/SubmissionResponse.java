package com.b2ta.common.dto.submission;

import com.b2ta.common.entity.enums.ExtractionStatus;
import com.b2ta.common.entity.enums.IdentityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    private UUID id;
    private String originalFilename;
    private String studentDisplayName;
    private String canvasSubmissionId;
    private IdentityStatus identityStatus;
    private ExtractionStatus extractionStatus;
    private String extractionFailureReason;
    private Integer extractedCharCount;
    private Boolean isOversized;
    private Integer position;
    private Instant createdAt;
}
