package com.b2ta.common.dto.submission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionUploadUrlsResponse {

    private List<FileUploadUrl> urls;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileUploadUrl {
        private String filename;
        private String uploadUrl;
        private String objectKey;
    }
}
