package com.b2ta.common.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reduces the candidates for one (criterion, submission) pair to a non-overlapping set
 * (Requirement 6.4, design Property 6).
 *
 * <p>Two mechanisms produce near-duplicates that a TA would read as the same piece of evidence:
 * the 400-character overlap between chunks means a passage near a seam is offered twice, and the
 * model itself sometimes returns a sentence and the paragraph containing it as separate matches.
 *
 * <p>The rule is: process candidates in descending confidence, keep one, and discard any later
 * candidate that shares 50 percent or more of the shorter of the two ranges with something already
 * kept. Highest confidence first means the survivor of a near-duplicate pair is the one the model
 * was most sure about, not whichever chunk happened to be processed first.
 */
@Component
public class MatchDeduplicator {

    /** Overlap at or above this fraction of the shorter range makes a candidate a duplicate. */
    public static final double OVERLAP_THRESHOLD = 0.5;

    /** Suggested matches retained per criterion per submission (Requirement 6.2). */
    public static final int MAX_MATCHES_PER_CRITERION = 5;

    /**
     * Deduplicates and truncates.
     *
     * @param candidates candidates in any order; not modified
     * @return at most {@link #MAX_MATCHES_PER_CRITERION} candidates, no two of which overlap by
     *         {@link #OVERLAP_THRESHOLD} or more of the shorter range, ordered by ascending start
     *         offset so the caller can persist them in document order
     */
    public List<CandidateMatch> deduplicate(List<CandidateMatch> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<CandidateMatch> byConfidence = new ArrayList<>(candidates);
        // The ordering has to be total, not merely mostly-total. Greedy selection means whichever of
        // two tied candidates comes first is the one retained, so any pair the comparator leaves
        // equal would make the retained set depend on the order chunks happened to finish in, and
        // the same submission could show different evidence across two runs.
        byConfidence.sort(Comparator
                .comparing(CandidateMatch::confidence).reversed()
                .thenComparingInt(CandidateMatch::start)
                .thenComparingInt(CandidateMatch::end)
                .thenComparing(CandidateMatch::rationale,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<CandidateMatch> retained = new ArrayList<>();
        for (CandidateMatch candidate : byConfidence) {
            if (retained.size() >= MAX_MATCHES_PER_CRITERION) {
                break;
            }
            boolean duplicate = retained.stream()
                    .anyMatch(kept -> candidate.overlapFraction(kept) >= OVERLAP_THRESHOLD);
            if (!duplicate) {
                retained.add(candidate);
            }
        }

        retained.sort(Comparator
                .comparingInt(CandidateMatch::start)
                .thenComparingInt(CandidateMatch::end));
        return List.copyOf(retained);
    }
}
