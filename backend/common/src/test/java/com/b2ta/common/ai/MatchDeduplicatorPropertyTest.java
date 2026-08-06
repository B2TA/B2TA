package com.b2ta.common.ai;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.6 — design Property 6: Match Overlap Deduplication.
 *
 * <p>No two retained matches for a single (criterion, submission) pair may overlap by 50 percent or
 * more of the shorter of the two ranges. The test generates arbitrary candidate sets, including
 * heavily overlapping ones, and checks the invariant on the retained set.
 */
@Tag("pbt")
class MatchDeduplicatorPropertyTest {

    private final MatchDeduplicator deduplicator = new MatchDeduplicator();

    @Provide
    Arbitrary<CandidateMatch> candidate() {
        Arbitrary<Integer> start = Arbitraries.integers().between(0, 9000);
        Arbitrary<Integer> length = Arbitraries.integers().between(
                CandidateMatch.MIN_PASSAGE_LENGTH, CandidateMatch.MAX_PASSAGE_LENGTH);
        Arbitrary<BigDecimal> confidence = Arbitraries.integers().between(0, 100)
                .map(hundredths -> BigDecimal.valueOf(hundredths, 2));
        Arbitrary<String> rationale = Arbitraries.strings()
                .withCharRange('a', 'z').ofMinLength(1).ofMaxLength(120);

        return Combinators.combine(start, length, rationale, confidence)
                .as((s, len, why, conf) -> new CandidateMatch(s, s + len, why, conf));
    }

    @Provide
    Arbitrary<List<CandidateMatch>> candidates() {
        return candidate().list().ofMinSize(0).ofMaxSize(40);
    }

    @Property(tries = 300)
    void retainedMatchesDoNotOverlapBeyondTheThreshold(
            @ForAll("candidates") List<CandidateMatch> candidates) {

        List<CandidateMatch> retained = deduplicator.deduplicate(candidates);

        for (int i = 0; i < retained.size(); i++) {
            for (int j = i + 1; j < retained.size(); j++) {
                double overlap = retained.get(i).overlapFraction(retained.get(j));
                assertThat(overlap)
                        .as("overlap between retained matches %d and %d", i, j)
                        .isLessThan(MatchDeduplicator.OVERLAP_THRESHOLD);
            }
        }
    }

    @Property(tries = 300)
    void retainsAtMostFiveMatches(@ForAll("candidates") List<CandidateMatch> candidates) {
        assertThat(deduplicator.deduplicate(candidates))
                .hasSizeLessThanOrEqualTo(MatchDeduplicator.MAX_MATCHES_PER_CRITERION);
    }

    @Property(tries = 300)
    void retainedMatchesAreASubsetOfTheInput(@ForAll("candidates") List<CandidateMatch> candidates) {
        assertThat(deduplicator.deduplicate(candidates)).isSubsetOf(candidates);
    }

    @Property(tries = 300)
    void retainedMatchesAreInAscendingStartOrder(
            @ForAll("candidates") List<CandidateMatch> candidates) {

        List<CandidateMatch> retained = deduplicator.deduplicate(candidates);
        for (int i = 1; i < retained.size(); i++) {
            assertThat(retained.get(i).start())
                    .isGreaterThanOrEqualTo(retained.get(i - 1).start());
        }
    }

    @Property(tries = 300)
    void outputDoesNotDependOnInputOrder(@ForAll("candidates") List<CandidateMatch> candidates) {
        List<CandidateMatch> forward = deduplicator.deduplicate(candidates);
        List<CandidateMatch> reversed = deduplicator.deduplicate(candidates.reversed());

        // Deterministic tie-breaking means the same submission yields the same suggestions on every
        // run, regardless of the order chunks happened to complete in.
        assertThat(reversed).isEqualTo(forward);
    }

    @Test
    void keepsTheHigherConfidenceOfAnOverlappingPair() {
        CandidateMatch weak = new CandidateMatch(0, 100, "weak", bd("0.40"));
        CandidateMatch strong = new CandidateMatch(10, 110, "strong", bd("0.90"));

        List<CandidateMatch> retained = deduplicator.deduplicate(List.of(weak, strong));

        assertThat(retained).containsExactly(strong);
    }

    @Test
    void keepsDisjointMatches() {
        CandidateMatch first = new CandidateMatch(0, 100, "first", bd("0.50"));
        CandidateMatch second = new CandidateMatch(200, 300, "second", bd("0.50"));

        assertThat(deduplicator.deduplicate(List.of(first, second)))
                .containsExactly(first, second);
    }

    @Test
    void discardsAShortMatchFullyInsideALongerOne() {
        CandidateMatch outer = new CandidateMatch(0, 1000, "outer", bd("0.80"));
        CandidateMatch inner = new CandidateMatch(400, 460, "inner", bd("0.70"));

        // The inner range is only 6 percent of the outer, but 100 percent of the shorter range,
        // which is the normalisation the requirement specifies.
        assertThat(deduplicator.deduplicate(List.of(outer, inner))).containsExactly(outer);
    }

    @Test
    void keepsMatchesTouchingAtABoundary() {
        CandidateMatch first = new CandidateMatch(0, 100, "first", bd("0.60"));
        CandidateMatch second = new CandidateMatch(100, 200, "second", bd("0.60"));

        assertThat(first.overlapFraction(second)).isZero();
        assertThat(deduplicator.deduplicate(List.of(first, second))).hasSize(2);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY);
    }
}
