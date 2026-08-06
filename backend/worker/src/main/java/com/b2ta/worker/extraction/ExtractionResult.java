package com.b2ta.worker.extraction;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of text extraction from a submission file.
 * Contains the extracted text, character count, paragraph-level text runs,
 * an oversized flag, and an optional failure reason.
 */
@Data
@Builder
public class ExtractionResult {

    /**
     * The full extracted text content. Null when extraction fails.
     */
    private final String extractedText;

    /**
     * Total character count of the extracted text. Zero when extraction fails.
     */
    private final int charCount;

    /**
     * Paragraph-level text runs with zero-based character offsets.
     * Each run has start < end, runs are in ascending start offset order, and non-overlapping.
     * Empty when extraction fails.
     */
    private final List<TextRun> textRuns;

    /**
     * True if the extracted text exceeds 100,000 characters.
     */
    private final boolean oversized;

    /**
     * Null on success. One of the ExtractionFailureReason values when extraction fails.
     */
    private final ExtractionFailureReason failureReason;

    /**
     * Returns true if extraction was successful (no failure reason).
     */
    public boolean isSuccess() {
        return failureReason == null;
    }

    /**
     * Represents a contiguous text run with zero-based character offsets.
     */
    @Data
    @Builder
    public static class TextRun {
        /**
         * Zero-based start character offset (inclusive).
         */
        private final int start;

        /**
         * Zero-based end character offset (exclusive).
         */
        private final int end;
    }

    /**
     * Creates a successful extraction result.
     */
    public static ExtractionResult success(String text, List<TextRun> textRuns) {
        int charCount = text != null ? text.length() : 0;
        return ExtractionResult.builder()
                .extractedText(text)
                .charCount(charCount)
                .textRuns(textRuns != null ? textRuns : List.of())
                .oversized(charCount > 100_000)
                .failureReason(null)
                .build();
    }

    /**
     * Creates a failed extraction result with the given reason.
     */
    public static ExtractionResult failure(ExtractionFailureReason reason) {
        return ExtractionResult.builder()
                .extractedText(null)
                .charCount(0)
                .textRuns(List.of())
                .oversized(false)
                .failureReason(reason)
                .build();
    }
}
