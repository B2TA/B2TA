package com.b2ta.worker.parsing;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort PDF rubric parser.
 * Extracts text using PDFBox and attempts to detect table-like structures.
 * <p>
 * Strategy:
 * 1. Extract all text from the PDF.
 * 2. If fewer than 50 characters, fail with NO_EXTRACTABLE_TEXT.
 * 3. Attempt to detect a table structure by looking for consistent column separators
 *    (tabs or multiple consecutive spaces) across lines.
 * 4. Parse detected table rows as criterion + performance levels.
 * 5. If no table structure detected, fail with "No table structure detected".
 */
@Component
@Slf4j
public class PdfRubricParser {

    private static final int MIN_EXTRACTABLE_CHARS = 50;
    // Pattern: two or more consecutive spaces or a tab character as column separator
    private static final Pattern COLUMN_SEPARATOR = Pattern.compile("\\t|\\s{3,}");
    // Pattern to match points in format "(points)" at end of text
    private static final Pattern POINTS_PATTERN = Pattern.compile("^(.+?)\\s*\\(([\\d.]+)\\)\\s*$");

    /**
     * Parse a PDF input stream into a ParsedRubric.
     *
     * @param inputStream the PDF file content
     * @return parsed rubric data
     * @throws RubricParseException if parsing fails
     */
    public ParsedRubric parse(InputStream inputStream) throws RubricParseException {
        String text;
        try {
            byte[] pdfBytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                text = stripper.getText(document);
            }
        } catch (IOException e) {
            throw new RubricParseException("Failed to read PDF file: " + e.getMessage());
        }

        if (text == null || text.trim().length() < MIN_EXTRACTABLE_CHARS) {
            throw new RubricParseException("NO_EXTRACTABLE_TEXT");
        }

        // Split into lines and attempt table detection
        List<String> lines = Arrays.stream(text.split("\\r?\\n"))
                .filter(line -> !line.isBlank())
                .toList();

        // Attempt to find lines with consistent multi-column structure
        List<List<String>> tableRows = detectTableRows(lines);

        if (tableRows.size() < 2) {
            throw new RubricParseException("No table structure detected");
        }

        // First row with multiple columns is treated as the header
        List<String> headerCells = tableRows.get(0);
        List<String> levelLabels = new ArrayList<>();
        for (int i = 1; i < headerCells.size(); i++) {
            String label = headerCells.get(i).trim();
            if (!label.isEmpty()) {
                levelLabels.add(label);
            }
        }

        if (levelLabels.isEmpty()) {
            throw new RubricParseException("No table structure detected");
        }

        // Parse remaining rows as criteria
        List<ParsedRubric.ParsedCriterion> criteria = new ArrayList<>();
        for (int rowIdx = 1; rowIdx < tableRows.size(); rowIdx++) {
            List<String> row = tableRows.get(rowIdx);
            String title = row.get(0).trim();
            if (title.isEmpty()) continue;

            ParsedRubric.ParsedCriterion criterion = parseCriterionRow(title, row, levelLabels);
            criteria.add(criterion);
        }

        if (criteria.isEmpty()) {
            throw new RubricParseException("No table structure detected");
        }

        return ParsedRubric.builder().criteria(criteria).build();
    }

    /**
     * Attempts to detect table rows by finding lines that can be split into
     * multiple columns using tab or multi-space separators.
     * A valid table requires that at least 2 consecutive lines produce the same
     * number of columns (>= 2).
     */
    private List<List<String>> detectTableRows(List<String> lines) {
        List<List<String>> candidateRows = new ArrayList<>();

        for (String line : lines) {
            String[] parts = COLUMN_SEPARATOR.split(line);
            List<String> cells = Arrays.stream(parts)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();

            if (cells.size() >= 2) {
                candidateRows.add(cells);
            }
        }

        if (candidateRows.size() < 2) {
            return List.of();
        }

        // Find the most common column count among candidate rows
        int headerColumns = candidateRows.get(0).size();

        // Filter rows that have similar column count (within +/- 1 of header)
        List<List<String>> tableRows = new ArrayList<>();
        tableRows.add(candidateRows.get(0));

        for (int i = 1; i < candidateRows.size(); i++) {
            List<String> row = candidateRows.get(i);
            if (Math.abs(row.size() - headerColumns) <= 1) {
                // Normalize to header column count by padding or trimming
                List<String> normalized = new ArrayList<>(row);
                while (normalized.size() < headerColumns) {
                    normalized.add("");
                }
                if (normalized.size() > headerColumns) {
                    normalized = normalized.subList(0, headerColumns);
                }
                tableRows.add(normalized);
            }
        }

        return tableRows.size() >= 2 ? tableRows : List.of();
    }

    private ParsedRubric.ParsedCriterion parseCriterionRow(String title, List<String> row,
                                                            List<String> levelLabels) {
        List<ParsedRubric.ParsedLevel> levels = new ArrayList<>();
        boolean requiresCompletion = false;
        BigDecimal maxPoints = null;

        for (int i = 0; i < levelLabels.size(); i++) {
            int colIdx = i + 1;
            String cellValue = colIdx < row.size() ? row.get(colIdx).trim() : "";

            if (cellValue.isEmpty()) {
                levels.add(ParsedRubric.ParsedLevel.builder()
                        .label(levelLabels.get(i))
                        .description("")
                        .points(null)
                        .build());
                requiresCompletion = true;
                continue;
            }

            Matcher matcher = POINTS_PATTERN.matcher(cellValue);
            String description;
            BigDecimal points = null;

            if (matcher.matches()) {
                description = matcher.group(1).trim();
                try {
                    points = new BigDecimal(matcher.group(2));
                } catch (NumberFormatException e) {
                    points = null;
                    requiresCompletion = true;
                }
            } else {
                // Try parsing the entire cell as a number (pure points column)
                try {
                    points = new BigDecimal(cellValue);
                    description = cellValue;
                } catch (NumberFormatException e) {
                    description = cellValue;
                    points = null;
                    requiresCompletion = true;
                }
            }

            levels.add(ParsedRubric.ParsedLevel.builder()
                    .label(levelLabels.get(i))
                    .description(description)
                    .points(points)
                    .build());

            if (points != null && (maxPoints == null || points.compareTo(maxPoints) > 0)) {
                maxPoints = points;
            }
        }

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
