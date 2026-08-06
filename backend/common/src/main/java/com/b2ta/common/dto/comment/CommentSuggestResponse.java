package com.b2ta.common.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Candidate feedback snippets (Requirement 12.3).
 *
 * <p>Each snippet is flagged as AI-generated so the marking view can label inserted text until the TA
 * edits or accepts the field (Requirement 12.5). The flag lives on the snippet rather than on the
 * response so a future mix of stored and generated suggestions stays distinguishable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentSuggestResponse {

    private List<FeedbackSnippet> snippets;

    public static CommentSuggestResponse ofAiSnippets(List<String> texts) {
        return CommentSuggestResponse.builder()
                .snippets(texts.stream()
                        .map(text -> FeedbackSnippet.builder()
                                .text(text)
                                .isAiGenerated(true)
                                .build())
                        .toList())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackSnippet {
        private String text;
        private Boolean isAiGenerated;
    }
}
