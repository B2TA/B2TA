package com.b2ta.common.ai;

import java.math.BigDecimal;

/**
 * A passage the Match_Engine proposes as evidence for one criterion, in global offset space.
 *
 * <p>Distinct from the {@code SuggestedMatch} entity: candidates exist before validation,
 * deduplication, and truncation to the top five, and never reach the database unless they survive
 * all three.
 *
 * @param start      inclusive start offset in the extracted submission text
 * @param end        exclusive end offset; always greater than {@code start}
 * @param rationale  1-300 character explanation (Requirement 6.3)
 * @param confidence 0.00-1.00 match strength (Requirement 6.2)
 */
public record CandidateMatch(int start, int end, String rationale, BigDecimal confidence) {

    /** Shortest passage the model may return; anything shorter is not usable evidence. */
    public static final int MIN_PASSAGE_LENGTH = 20;

    /** Longest passage the model may return (Requirement 6.2). */
    public static final int MAX_PASSAGE_LENGTH = 1500;

    /** Maximum rationale length (Requirement 6.3). */
    public static final int MAX_RATIONALE_LENGTH = 300;

    public int length() {
        return end - start;
    }

    /**
     * True when this candidate satisfies every field invariant of design Property 5 against a
     * submission of the given analysed length.
     */
    public boolean isValidFor(int analyzedCharCount) {
        return start >= 0
                && end > start
                && end <= analyzedCharCount
                && length() >= MIN_PASSAGE_LENGTH
                && length() <= MAX_PASSAGE_LENGTH
                && rationale != null
                && !rationale.isBlank()
                && rationale.length() <= MAX_RATIONALE_LENGTH
                && confidence != null
                && confidence.compareTo(BigDecimal.ZERO) >= 0
                && confidence.compareTo(BigDecimal.ONE) <= 0;
    }

    /**
     * Fraction of the shorter of the two passages that this candidate shares with {@code other}.
     *
     * <p>Normalising by the shorter range is what makes a short passage fully inside a long one
     * count as a duplicate; normalising by the union or by either fixed side would let a
     * near-subset through.
     *
     * @return 0.0 when the ranges are disjoint, 1.0 when one fully contains the other
     */
    public double overlapFraction(CandidateMatch other) {
        int overlapStart = Math.max(start, other.start);
        int overlapEnd = Math.min(end, other.end);
        int overlap = overlapEnd - overlapStart;
        if (overlap <= 0) {
            return 0.0;
        }
        int shorter = Math.min(length(), other.length());
        return shorter == 0 ? 0.0 : (double) overlap / shorter;
    }
}
