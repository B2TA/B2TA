package com.b2ta.api.service;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Rubric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RubricPrinterTest {

    private RubricPrinter rubricPrinter;

    @BeforeEach
    void setUp() {
        rubricPrinter = new RubricPrinter();
    }

    @Test
    void serialize_singleCriterionWithLevels_producesCorrectCsv() {
        Rubric rubric = buildRubric(List.of(
                buildCriterion("Thesis", "Clear thesis statement", new BigDecimal("10"), "#D32F2F", 0, List.of(
                        buildLevel("Excellent", "Strong thesis", new BigDecimal("10"), 0),
                        buildLevel("Good", "Adequate thesis", new BigDecimal("7"), 1)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("Criterion,Description,Max Points,Color,Level 1,Level 2\r\n");
        assertThat(csv).contains("Thesis,Clear thesis statement,10,#D32F2F,Excellent|Strong thesis|10,Good|Adequate thesis|7\r\n");
    }

    @Test
    void serialize_multipleCriteria_producesRowPerCriterion() {
        Rubric rubric = buildRubric(List.of(
                buildCriterion("Criterion A", "", new BigDecimal("5"), "#D32F2F", 0, List.of(
                        buildLevel("High", "", new BigDecimal("5"), 0)
                )),
                buildCriterion("Criterion B", "Some desc", new BigDecimal("20"), "#1976D2", 1, List.of(
                        buildLevel("Full", "Full marks", new BigDecimal("20"), 0),
                        buildLevel("Partial", "Partial marks", new BigDecimal("10"), 1)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        assertThat(lines).hasSize(3); // header + 2 data rows
        // First criterion only has 1 level; second has 2. Header should have Level 1, Level 2
        assertThat(lines[0]).isEqualTo("Criterion,Description,Max Points,Color,Level 1,Level 2");
        // First row has empty cell for Level 2
        assertThat(lines[1]).isEqualTo("Criterion A,,5,#D32F2F,High||5,");
    }

    @Test
    void serialize_rfc4180Quoting_commaInField() {
        Rubric rubric = buildRubric(List.of(
                buildCriterion("Grammar, Style", "Commas, semicolons, and more", new BigDecimal("10"), "#D32F2F", 0, List.of(
                        buildLevel("Good", "", new BigDecimal("10"), 0)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // Fields with commas should be double-quoted
        assertThat(csv).contains("\"Grammar, Style\"");
        assertThat(csv).contains("\"Commas, semicolons, and more\"");
    }

    @Test
    void serialize_rfc4180Quoting_doubleQuoteInField() {
        Rubric rubric = buildRubric(List.of(
                buildCriterion("Use of \"evidence\"", "", new BigDecimal("10"), "#D32F2F", 0, List.of(
                        buildLevel("Good", "", new BigDecimal("10"), 0)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // Double quotes within a field should be escaped (doubled) and field should be quoted
        assertThat(csv).contains("\"Use of \"\"evidence\"\"\"");
    }

    @Test
    void serialize_rfc4180Quoting_newlineInField() {
        Rubric rubric = buildRubric(List.of(
                buildCriterion("Criterion", "Line 1\nLine 2", new BigDecimal("10"), "#D32F2F", 0, List.of(
                        buildLevel("Good", "", new BigDecimal("10"), 0)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // Field with newline should be quoted
        assertThat(csv).contains("\"Line 1\nLine 2\"");
    }

    @Test
    void serialize_rfc4180Quoting_leadingTrailingWhitespace() {
        Rubric rubric = buildRubric(List.of(
                buildCriterion(" Leading", "Trailing ", new BigDecimal("10"), "#D32F2F", 0, List.of(
                        buildLevel("Good", "", new BigDecimal("10"), 0)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // Fields with leading/trailing whitespace should be quoted
        assertThat(csv).contains("\" Leading\"");
        assertThat(csv).contains("\"Trailing \"");
    }

    @Test
    void serialize_nullMaxPoints_emitsEmptyField() {
        Rubric rubric = buildRubric(List.of(
                buildCriterion("Unresolved", "", null, "#D32F2F", 0, List.of(
                        buildLevel("TBD", "", null, 0)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        // "Unresolved,,," — empty description, empty max points, color, then level with empty points
        assertThat(lines[1]).isEqualTo("Unresolved,,,#D32F2F,TBD||");
    }

    @Test
    void serialize_emptyCriteria_throwsIllegalArgument() {
        Rubric rubric = buildRubric(new ArrayList<>());

        assertThatThrownBy(() -> rubricPrinter.serialize(rubric))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one criterion");
    }

    @Test
    void serialize_nullCriteria_throwsIllegalArgument() {
        Rubric rubric = Rubric.builder().criteria(null).build();

        assertThatThrownBy(() -> rubricPrinter.serialize(rubric))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one criterion");
    }

    @Test
    void serialize_pipesInLevelFieldsArePreserved() {
        // Pipes are the level cell delimiter but should appear literally if in label/description
        Rubric rubric = buildRubric(List.of(
                buildCriterion("Criterion", "", new BigDecimal("10"), "#D32F2F", 0, List.of(
                        buildLevel("A|B", "desc|with|pipes", new BigDecimal("10"), 0)
                ))
        ));

        byte[] csvBytes = rubricPrinter.serialize(rubric);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        // The pipe characters are part of the cell content — the CSV field may need quoting
        // because pipes inside create "A|B|desc|with|pipes|10" which doesn't require CSV quoting
        // unless it contains commas, quotes, or newlines
        assertThat(csv).contains("A|B|desc|with|pipes|10");
    }

    // --- Helper methods ---

    private Rubric buildRubric(List<Criterion> criteria) {
        return Rubric.builder().criteria(criteria).build();
    }

    private Criterion buildCriterion(String title, String description, BigDecimal maxPoints,
                                     String displayColor, int position, List<PerformanceLevel> levels) {
        Criterion criterion = Criterion.builder()
                .title(title)
                .description(description)
                .maxPoints(maxPoints)
                .displayColor(displayColor)
                .position(position)
                .performanceLevels(new ArrayList<>(levels))
                .build();
        return criterion;
    }

    private PerformanceLevel buildLevel(String label, String description, BigDecimal points, int position) {
        return PerformanceLevel.builder()
                .label(label)
                .description(description)
                .points(points)
                .position(position)
                .build();
    }
}
