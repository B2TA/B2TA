package com.b2ta.common.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentSuggestResponse {

    private List<FeedbackSnippet> snippets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackSnippet {
        private String text;
        private Boolean isAiGenerated;
    }
}
