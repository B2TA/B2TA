package com.b2ta.api.analyze;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A verified analysis of one submission against one rubric.
 *
 * <p>Every evidence span here has already been located verbatim in the submission text;
 * fabricated quotes are dropped before this object is constructed, and
 * {@link #droppedSpanCount} records how many.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    private List<CriterionAnalysis> criteria;

    /** Draft feedback for the student, for the TA to edit rather than write cold. */
    private String overallNote;

    /** How many model-supplied quotes failed verification and were discarded. */
    private int droppedSpanCount;

    /** Total spans the model proposed, verified or not. */
    private int proposedSpanCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterionAnalysis {

        /** Verbatim Canvas criterion id. */
        private String criterionId;

        private Double suggestedPoints;

        private Double confidence;

        /** One sentence for the TA. */
        private String rationale;

        /** One of {@code none}, {@code missing}, {@code possible_misconception}. */
        private String flag;

        private List<VerifiedSpan> evidence;
    }

    /**
     * An evidence span whose quote was found verbatim in the submission.
     *
     * <p>Offsets are computed server-side and are paragraph-relative. Any offsets the
     * model supplied are ignored — it cannot count characters reliably.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerifiedSpan {

        private String id;

        private String criterionId;

        /** The exact text as it appears in the document, not as the model typed it. */
        private String text;

        /** Always false on arrival from the AI, until the TA confirms it. */
        private boolean confirmed;

        private String tooltip;

        private int paragraphIdx;

        private int offsetInParagraph;
    }
}
