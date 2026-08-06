package com.b2ta.common.ai;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.6 — design Property 5: Match Output Field Invariant.
 *
 * <p>Every match that survives validation must satisfy {@code 0 <= start < end <= analysedLength},
 * have a passage length between 20 and 1500, a rationale of 1-300 characters, and a confidence in
 * 0.00-1.00. The test asserts the validator accepts exactly the candidates meeting those bounds, since
 * that validator is the only thing standing between a model response and a stored match.
 */
@Tag("pbt")
class CandidateMatchPropertyTest {

    @Provide
    Arbitrary<CandidateMatch> anyCandidate() {
        Arbitrary<Integer> start = Arbitraries.integers().between(-50, 5000);
        Arbitrary<Integer> length = Arbitraries.integers().between(-10, 2000);
        Arbitrary<String> rationale = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(0).ofMaxLength(400);
        Arbitrary<BigDecimal> confidence = Arbitraries.integers().between(-50, 150)
                .map(hundredths -> BigDecimal.valueOf(hundredths, 2));

        return Combinators.combine(start, length, rationale, confidence)
                .as((s, len, why, conf) -> new CandidateMatch(s, s + Math.max(len, 1), why, conf));
    }

    @Property(tries = 500)
    void validationAcceptsExactlyTheCandidatesWithinBounds(
            @ForAll("anyCandidate") CandidateMatch candidate,
            @ForAll @IntRange(min = 1, max = 6000) int analyzedLength) {

        boolean valid = candidate.isValidFor(analyzedLength);

        boolean expected = candidate.start() >= 0
                && candidate.end() > candidate.start()
                && candidate.end() <= analyzedLength
                && candidate.length() >= CandidateMatch.MIN_PASSAGE_LENGTH
                && candidate.length() <= CandidateMatch.MAX_PASSAGE_LENGTH
                && !candidate.rationale().isBlank()
                && candidate.rationale().length() <= CandidateMatch.MAX_RATIONALE_LENGTH
                && candidate.confidence().compareTo(BigDecimal.ZERO) >= 0
                && candidate.confidence().compareTo(BigDecimal.ONE) <= 0;

        assertThat(valid).isEqualTo(expected);
    }

    @Property(tries = 500)
    void validatedCandidatesIndexIntoTheAnalyzedText(
            @ForAll("anyCandidate") CandidateMatch candidate,
            @ForAll @IntRange(min = 1, max = 6000) int analyzedLength) {

        if (!candidate.isValidFor(analyzedLength)) {
            return;
        }
        // The point of the invariant: a validated candidate can always be used to slice the analysed
        // text without an index check at the call site.
        String text = "x".repeat(analyzedLength);
        assertThat(text.substring(candidate.start(), candidate.end()))
                .hasSize(candidate.length());
    }

    @Property(tries = 300)
    void overlapFractionIsSymmetric(@ForAll("anyCandidate") CandidateMatch a,
                                    @ForAll("anyCandidate") CandidateMatch b) {
        assertThat(a.overlapFraction(b)).isEqualTo(b.overlapFraction(a));
    }

    @Property(tries = 300)
    void overlapFractionStaysInTheUnitInterval(@ForAll("anyCandidate") CandidateMatch a,
                                               @ForAll("anyCandidate") CandidateMatch b) {
        assertThat(a.overlapFraction(b)).isBetween(0.0, 1.0);
    }

    @Test
    void rejectsAPassageShorterThanTheMinimum() {
        CandidateMatch tooShort = new CandidateMatch(0, 19, "why", new BigDecimal("0.90"));
        assertThat(tooShort.isValidFor(1000)).isFalse();
    }

    @Test
    void rejectsAPassageEndingPastTheAnalyzedText() {
        CandidateMatch outOfBounds = new CandidateMatch(900, 1100, "why", new BigDecimal("0.90"));
        assertThat(outOfBounds.isValidFor(1000)).isFalse();
    }

    @Test
    void acceptsAPassageAtTheExactBounds() {
        CandidateMatch atLimit = new CandidateMatch(0, CandidateMatch.MAX_PASSAGE_LENGTH, "why",
                BigDecimal.ONE);
        assertThat(atLimit.isValidFor(CandidateMatch.MAX_PASSAGE_LENGTH)).isTrue();
    }
}
