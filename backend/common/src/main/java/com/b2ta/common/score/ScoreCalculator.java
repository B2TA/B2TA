package com.b2ta.common.score;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionScore;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Derives awarded points and totals from performance level selections and manual overrides
 * (Requirements 11.2-11.4, 11.8-11.11, design Property 8).
 *
 * <p>Every awarded value comes from either the fixed point value of a selected level or a TA-entered
 * override; nothing is inferred (Requirement 11.8). An override takes precedence when both are
 * present, which is how a TA departs from the rubric for one criterion without changing the level
 * they judged to apply.
 *
 * <p>All arithmetic is {@link BigDecimal} at scale 2. Double arithmetic would make a sum of values
 * like 8.1 and 3.3 land a cent away from what the TA sees on the two cards, and that difference would
 * be visible in the exported gradebook.
 */
@Component
public class ScoreCalculator {

    /** Decimal places for every point value and total (Requirement 11.3). */
    public static final int SCALE = 2;

    /** Awarded points and totals for one submission. */
    public record ScoreSummary(BigDecimal total,
                               BigDecimal maxTotal,
                               int unscoredCount,
                               int overrideCount,
                               Map<UUID, BigDecimal> awardedByCriterion) {

        /** True when every criterion carries a level selection or an override (Requirement 11.10). */
        public boolean isComplete() {
            return unscoredCount == 0;
        }
    }

    /**
     * Awarded points for one criterion, or null when it is unscored.
     *
     * <p>Null rather than zero: an unscored criterion is excluded from the total
     * (Requirement 11.10) and exported as an empty value rather than a zero (Requirement 16.5),
     * so the two states must stay distinguishable all the way through.
     */
    public BigDecimal awardedPoints(CriterionScore score) {
        if (score == null) {
            return null;
        }
        if (score.getOverridePoints() != null) {
            return score.getOverridePoints().setScale(SCALE, RoundingMode.HALF_UP);
        }
        if (score.getSelectedLevel() != null && score.getSelectedLevel().getPoints() != null) {
            return score.getSelectedLevel().getPoints().setScale(SCALE, RoundingMode.HALF_UP);
        }
        return null;
    }

    /**
     * Summarises one submission's scores.
     *
     * @param criteria all criteria of the rubric, which fixes the maximum and the unscored count
     * @param scores   the criterion scores stored for this submission; may cover only some criteria
     */
    public ScoreSummary summarize(List<Criterion> criteria, Collection<CriterionScore> scores) {
        Map<UUID, CriterionScore> byCriterion = scores.stream()
                .filter(score -> score.getCriterion() != null)
                .collect(Collectors.toMap(
                        score -> score.getCriterion().getId(),
                        Function.identity(),
                        (first, second) -> first));

        BigDecimal total = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
        BigDecimal maxTotal = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
        int unscored = 0;
        int overrides = 0;
        Map<UUID, BigDecimal> awarded = new LinkedHashMap<>();

        for (Criterion criterion : criteria) {
            if (criterion.getMaxPoints() != null) {
                maxTotal = maxTotal.add(criterion.getMaxPoints().setScale(SCALE, RoundingMode.HALF_UP));
            }
            CriterionScore score = byCriterion.get(criterion.getId());
            BigDecimal points = awardedPoints(score);
            awarded.put(criterion.getId(), points);

            if (points == null) {
                unscored++;
            } else {
                total = total.add(points);
            }
            if (score != null && score.getOverridePoints() != null) {
                overrides++;
            }
        }

        return new ScoreSummary(
                total.setScale(SCALE, RoundingMode.HALF_UP),
                maxTotal.setScale(SCALE, RoundingMode.HALF_UP),
                unscored,
                overrides,
                // Not Map.copyOf: it rejects null values, and null is how an unscored criterion is
                // represented here. Collapsing it to zero would make an ungraded criterion
                // indistinguishable from one awarded no marks.
                Collections.unmodifiableMap(awarded));
    }

    /**
     * Validates a manual override against a criterion's maximum (Requirement 11.4, 11.5).
     *
     * @return null when the value is acceptable, otherwise the reason it is not
     */
    public String validateOverride(Criterion criterion, BigDecimal override) {
        if (override == null) {
            return null;
        }
        if (override.scale() > SCALE) {
            return "Override for '" + criterion.getTitle()
                    + "' must have at most 2 decimal places";
        }
        if (override.compareTo(BigDecimal.ZERO) < 0) {
            return "Override for '" + criterion.getTitle() + "' must be 0 or greater";
        }
        BigDecimal max = criterion.getMaxPoints();
        if (max != null && override.compareTo(max) > 0) {
            return "Override for '" + criterion.getTitle() + "' must be between 0 and "
                    + max.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return null;
    }
}
