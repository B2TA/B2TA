package com.b2ta.worker.parsing;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CSV rubric files.
 * <p>
 * Expected format:
 * - Header row: first column is "Criterion" (or criterion title column), remaining columns are performance level labels.
 * - Data rows: first column is the criterion title, remaining columns contain level descriptions.
 * - Cell format for each level: "Description (points)" or just "Description" with points optionally in a separate column.
 * - If a point value is missing or non-numeric, that field is set to null and requiresCompletion=true.
 */
@Component
@Slf4j
public class CsvRubricParser {

    // Pattern to match "Description (points)" or "(points)" at end of cell
    private static final Pattern POINTS_PATTERN = Pattern.compile("^(.+?)\\s*\\(([\\d.]+)\\)\\s*$");

    /**
     * Parse a CSV input stream into a ParsedRubric.
     *
     * @param inputStream the CSV file content
     * @return parsed rubric data
     * @throws RubricParseException if parsing fails
     */
    public ParsedRubric parse(InputStream inputStream) throws RubricParseException {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<CSVRecord> records = parser.getRecords();

            if (records.isEmpty()) {
                throw new RubricParseException("CSV file is empty — no header row found");
            }

            // First row is the header
            CSVRecord header = records.get(0);
            if (header.size() < 2) {
                throw new RubricParseException("CSV header must have at least 2 columns (criterion title + at least one level)");
            }

            // Extract level labels from header (columns after the first)
            List<String> levelLabels = new ArrayList<>();
            for (int i = 1; i < header.size(); i++) {
                String label = header.get(i).trim();
                if (!label.isEmpty()) {
                    levelLabels.add(label);
                }
            }

            if (levelLabels.isEmpty()) {
                throw new RubricParseException("CSV header contains no performance level labels after the criterion column");
            }

            // Parse data rows
            List<ParsedRubric.ParsedCriterion> criteria = new ArrayList<>();
            for (int rowIdx = 1; rowIdx < records.size(); rowIdx++) {
                CSVRecord row = records.get(rowIdx);
                if (row.size() == 0) continue;

                String title = row.get(0).trim();
                if (title.isEmpty()) continue; // Skip rows with empty criterion title

                ParsedRubric.ParsedCriterion criterion = parseCriterionRow(title, row, levelLabels);
                criteria.add(criterion);
            }

            if (criteria.isEmpty()) {
                throw new RubricParseException("No criteria found in CSV — all data rows are empty");
            }

            return ParsedRubric.builder().criteria(criteria).build();

        } catch (IOException e) {
            throw new RubricParseException("Failed to read CSV file: " + e.getMessage());
        }
    }

    private ParsedRubric.ParsedCriterion parseCriterionRow(String title, CSVRecord row,
                                                            List<String> levelLabels) {
        List<ParsedRubric.ParsedLevel> levels = new ArrayList<>();
        boolean requiresCompletion = false;
        BigDecimal maxPoints = null;

        for (int i = 0; i < levelLabels.size(); i++) {
            int colIdx = i + 1; // offset by the criterion title column
            String cellValue = colIdx < row.size() ? row.get(colIdx).trim() : "";

            if (cellValue.isEmpty()) {
                // Empty cell — still create a level with no description and unresolved points
                levels.add(ParsedRubric.ParsedLevel.builder()
                        .label(levelLabels.get(i))
                        .description("")
                        .points(null)
                        .build());
                requiresCompletion = true;
                continue;
            }

            // Try to extract points from the cell content
            Matcher matcher = POINTS_PATTERN.matcher(cellValue);
            String description;
            BigDecimal points = null;

            if (matcher.matches()) {
                description = matcher.group(1).trim();
                try {
                    points = new BigDecimal(matcher.group(2));
                } catch (NumberFormatException e) {
                    // Non-numeric points value — mark as unresolved
                    points = null;
                    requiresCompletion = true;
                }
            } else {
                // No (points) pattern — entire cell is description, points unresolved
                description = cellValue;
                points = null;
                requiresCompletion = true;
            }

            levels.add(ParsedRubric.ParsedLevel.builder()
                    .label(levelLabels.get(i))
                    .description(description)
                    .points(points)
                    .build());

            // Track max points across all levels
            if (points != null && (maxPoints == null || points.compareTo(maxPoints) > 0)) {
                maxPoints = points;
            }
        }

        // If maxPoints is still null, the criterion requires completion
        if (maxPoints == null) {
            requiresCompletion = true;
        }

        return ParsedRubric.ParsedCriterion.builder()
                .title(title)
                .description("")
                .maxPoints(maxPoints)
                .requiresCompletion(requiresCompletion)
                .levels(levels)
                .build();
    }
}
