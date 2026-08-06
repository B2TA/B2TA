package com.b2ta.common.score;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.CriterionScore;
import com.b2ta.common.entity.PerformanceLevel;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5.8 — design Property 8: Score Total Arithmetic.
 *
 * <p>The total must equal the sum of awarded points across scored criteria, rounded to 2 decimal
 * places, and the maximum must equal the sum of criterion maximums. Unscored criteria contribute
 * nothing and are counted separately rather than treated as zero.
 */
@Tag("pbt")
class ScoreCalculatorPropertyTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    /** A criterion with a maximum and three levels worth 0, half, and full marks. */
    private Criterion criterion(BigDecimal maxPoints) {
        Criterion criterion = Criterion.builder()
                .id(UUID.randomUUID())
                .title("Criterion " + maxPoints)
                .maxPoints(maxPoints)
                .displayColor("#1F77B4")
                .position(0)
                .performanceLevels(new ArrayList<>())
                .build();

        for (BigDecimal points : List.of(
                BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY),
                maxPoints.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP),
                maxPoints)) {
            criterion.getPerformanceLevels().add(PerformanceLevel.builder()
                    .id(UUID.randomUUID())
                    .criterion(criterion)
                    .label("Level " + points)
                    .points(points)
                    .position(criterion.getPerformanceLevels().size())
                    .build());
        }
        return criterion;
    }

    @Provide
    Arbitrary<List<BigDecimal>> criterionMaximums() {
        return Arbitraries.integers().between(1, 10000)
                .map(hundredths -> BigDecimal.valueOf(hundredths, 2))
                .list().ofMinSize(1).ofMaxSize(30);
    }

    @Property(tries = 300)
    void totalEqualsTheSumOfAwardedPoints(@ForAll("criterionMaximums") List<BigDecimal> maximums,
                                          @ForAll("levelChoices") List<Integer> choices) {
        List<Criterion> criteria = maximums.stream().map(this::criterion).toList();
        List<CriterionScore> scores = new ArrayList<>();
        BigDecimal expectedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        int expectedUnscored = 0;

        for (int i = 0; i < criteria.size(); i++) {
            Criterion criterion = criteria.get(i);
            // -1 means unscored; 0..2 pick one of the three levels.
            int choice = choices.isEmpty() ? -1 : choices.get(i % choices.size()) % 4 - 1;

            if (choice < 0) {
                expectedUnscored++;
                continue;
            }
            PerformanceLevel level = criterion.getPerformanceLevels().get(choice);
            scores.add(CriterionScore.builder()
                    .id(UUID.randomUUID())
                    .criterion(criterion)
                    .selectedLevel(level)
                    .build());
            expectedTotal = expectedTotal.add(level.getPoints());
        }

        ScoreCalculator.ScoreSummary summary = calculator.summarize(criteria, scores);

        assertThat(summary.total())
                .isEqualByComparingTo(expectedTotal.setScale(2, RoundingMode.HALF_UP));
        assertThat(summary.total().scale()).isEqualTo(ScoreCalculator.SCALE);
        assertThat(summary.unscoredCount()).isEqualTo(expectedUnscored);
    }

    @Provide
    Arbitrary<List<Integer>> levelChoices() {
        return Arbitraries.integers().between(0, 3).list().ofMinSize(1).ofMaxSize(30);
    }

    @Property(tries = 300)
    void maximumEqualsTheSumOfCriterionMaximums(
            @ForAll("criterionMaximums") List<BigDecimal> maximums) {

        List<Criterion> criteria = maximums.stream().map(this::criterion).toList();
        BigDecimal expected = maximums.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        assertThat(calculator.summarize(criteria, List.of()).maxTotal())
                .isEqualByComparingTo(expected);
    }

    @Property(tries = 300)
    void overrideTakesPrecedenceOverTheSelectedLevel(
            @ForAll("criterionMaximums") List<BigDecimal> maximums) {

        Criterion criterion = criterion(maximums.get(0));
        PerformanceLevel fullMarks = criterion.getPerformanceLevels().get(2);
        BigDecimal override = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

        CriterionScore score = CriterionScore.builder()
                .criterion(criterion)
                .selectedLevel(fullMarks)
                .overridePoints(override)
                .build();

        assertThat(calculator.awardedPoints(score)).isEqualByComparingTo(override);
    }

    @Test
    void unscoredCriteriaAreExcludedRatherThanCountedAsZero() {
        Criterion scored = criterion(new BigDecimal("10.00"));
        Criterion unscored = criterion(new BigDecimal("5.00"));

        CriterionScore score = CriterionScore.builder()
                .criterion(scored)
                .selectedLevel(scored.getPerformanceLevels().get(2))
                .build();

        ScoreCalculator.ScoreSummary summary =
                calculator.summarize(List.of(scored, unscored), List.of(score));

        assertThat(summary.total()).isEqualByComparingTo("10.00");
        assertThat(summary.maxTotal()).isEqualByComparingTo("15.00");
        assertThat(summary.unscoredCount()).isEqualTo(1);
        assertThat(summary.isComplete()).isFalse();
        // Null, not zero: the export writes an empty cell for this criterion (Requirement 16.5).
        assertThat(summary.awardedByCriterion().get(unscored.getId())).isNull();
    }

    @Test
    void rejectsAnOverrideAboveTheCriterionMaximum() {
        Criterion criterion = criterion(new BigDecimal("10.00"));

        assertThat(calculator.validateOverride(criterion, new BigDecimal("10.01")))
                .contains("between 0 and 10.00");
        assertThat(calculator.validateOverride(criterion, new BigDecimal("10.00"))).isNull();
        assertThat(calculator.validateOverride(criterion, new BigDecimal("-0.01")))
                .contains("0 or greater");
        assertThat(calculator.validateOverride(criterion, new BigDecimal("1.234")))
                .contains("2 decimal places");
    }
}
