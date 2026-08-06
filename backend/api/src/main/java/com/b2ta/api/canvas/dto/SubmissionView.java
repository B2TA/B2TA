package com.b2ta.api.canvas.dto;

import com.b2ta.api.analyze.AnalysisResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * One submission as the marking view consumes it: the text to render, the verified
 * evidence spans to highlight, and any scores Canvas already holds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionView {

    private String userId;

    private String studentName;

    private List<ParagraphView> paragraphs;

    /** Verified evidence spans, all unconfirmed until the TA acts on them. */
    private List<AnalysisResult.VerifiedSpan> spans;

    /** Suggested comment text keyed by criterion id, then by level label. */
    private Map<String, Map<String, String>> comments;

    /**
     * Set when text extraction failed. The TA can still grade manually, so this is
     * surfaced rather than turned into an error response.
     */
    private String extractionError;

    private boolean alreadyGraded;

    /** Scores Canvas already holds, keyed by criterion id. */
    private Map<String, Double> existingScores;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParagraphView {
        private int idx;
        private String label;
        private String text;

        /**
         * Explicitly named: Lombok generates {@code isTitle()} for this field, and
         * Jackson strips the {@code is} prefix, which would serialize it as
         * {@code "title"} and leave the client's {@code isTitle} undefined.
         */
        @com.fasterxml.jackson.annotation.JsonProperty("isTitle")
        private boolean isTitle;
    }
}
