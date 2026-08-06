package com.b2ta.common.dto.submission;

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
public class SubmissionUploadUrlsRequest {

    @NotEmpty(message = "At least one filename is required")
    @Size(max = 300, message = "Maximum 300 files per batch")
    private List<FileUploadEntry> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileUploadEntry {
        private String filename;
        private Long size;
    }
}
