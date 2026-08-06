package com.b2ta.api.service;

import com.b2ta.common.entity.Criterion;
import com.b2ta.common.entity.PerformanceLevel;
import com.b2ta.common.entity.Rubric;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serializes a Rubric into UTF-8 encoded CSV format (RFC 4180 compliant).
 *
 * CSV layout:
 * - Header row: "Criterion", "Description", "Max Points", "Color", "Level 1", "Level 2", ...
 * - Data rows: criterion title, description, max points (or empty), display color,
 *   then for each level position: "label|description|points" (pipe-delimited within the cell)
 *
 * This format is the CSV rubric interchange format accepted by the Rubric_Parser
 * through the .csv input path.
 */
@Component
public class RubricPrinter {

    private static final String CRLF = "\r\n";
    private static final char COMMA = ',';
    private static final char DQUOTE = '"';
    private static final char PIPE = '|';

    /**
     * Serializes a Rubric to CSV bytes (UTF-8).
     *
     * @param rubric the rubric to serialize; must have at least 1 criterion
     * @return UTF-8 encoded CSV bytes
     * @throws IllegalArgumentException if rubric has no criteria
     */
    public byte[] serialize(Rubric rubric) {
        List<Criterion> criteria = rubric.getCriteria();
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("Rubric must have at least one criterion to serialize");
        }

        int maxLevels = criteria.stream()
                .mapToInt(c -> c.getPerformanceLevels() != null ? c.getPerformanceLevels().size() : 0)
                .max()
                .orElse(0);

        StringBuilder csv = new StringBuilder();

        // Header row
        appendField(csv, "Criterion");
        csv.append(COMMA);
        appendField(csv, "Description");
        csv.append(COMMA);
        appendField(csv, "Max Points");
        csv.append(COMMA);
        appendField(csv, "Color");
        for (int i = 0; i < maxLevels; i++) {
            csv.append(COMMA);
            appendField(csv, "Level " + (i + 1));
        }
        csv.append(CRLF);

        // Data rows
        for (Criterion criterion : criteria) {
            appendField(csv, criterion.getTitle());
            csv.append(COMMA);
            appendField(csv, criterion.getDescription() != null ? criterion.getDescription() : "");
            csv.append(COMMA);
            appendField(csv, criterion.getMaxPoints() != null ? criterion.getMaxPoints().toPlainString() : "");
            csv.append(COMMA);
            appendField(csv, criterion.getDisplayColor() != null ? criterion.getDisplayColor() : "");

            List<PerformanceLevel> levels = criterion.getPerformanceLevels();
            for (int i = 0; i < maxLevels; i++) {
                csv.append(COMMA);
                if (levels != null && i < levels.size()) {
                    PerformanceLevel level = levels.get(i);
                    String cellValue = buildLevelCell(level);
                    appendField(csv, cellValue);
                } else {
                    appendField(csv, "");
                }
            }
            csv.append(CRLF);
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Builds the pipe-delimited cell value for a performance level.
     * Format: "label|description|points"
     */
    private String buildLevelCell(PerformanceLevel level) {
        String label = level.getLabel() != null ? level.getLabel() : "";
        String description = level.getDescription() != null ? level.getDescription() : "";
        String points = level.getPoints() != null ? level.getPoints().toPlainString() : "";
        return label + PIPE + description + PIPE + points;
    }

    /**
     * Appends a field value to the CSV, applying RFC 4180 quoting rules.
     *
     * A field is enclosed in double quotes if it contains:
     * - a comma
     * - a double quote (which is escaped by doubling)
     * - a newline (CR, LF, or CRLF)
     * - leading or trailing whitespace
     */
    private void appendField(StringBuilder csv, String value) {
        if (value == null || value.isEmpty()) {
            // Empty field — no quoting needed
            return;
        }

        if (requiresQuoting(value)) {
            csv.append(DQUOTE);
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == DQUOTE) {
                    csv.append(DQUOTE).append(DQUOTE);
                } else {
                    csv.append(c);
                }
            }
            csv.append(DQUOTE);
        } else {
            csv.append(value);
        }
    }

    /**
     * Determines whether a field value requires RFC 4180 quoting.
     */
    private boolean requiresQuoting(String value) {
        if (value.isEmpty()) {
            return false;
        }

        // Leading or trailing whitespace
        if (Character.isWhitespace(value.charAt(0)) || Character.isWhitespace(value.charAt(value.length() - 1))) {
            return true;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == COMMA || c == DQUOTE || c == '\n' || c == '\r') {
                return true;
            }
        }
        return false;
    }
}
