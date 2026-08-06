package com.b2ta.worker.parsing;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses XLSX rubric files.
 * Same logic as CSV: first row is the header (level labels), data rows are criteria.
 * Only the first sheet is read.
 */
@Component
@Slf4j
public class XlsxRubricParser {

    // Pattern to match "Description (points)" at end of cell
    private static final Pattern POINTS_PATTERN = Pattern.compile("^(.+?)\\s*\\(([\\d.]+)\\)\\s*$");

    /**
     * Parse an XLSX input stream into a ParsedRubric.
     *
     * @param inputStream the XLSX file content
     * @return parsed rubric data
     * @throws RubricParseException if parsing fails
     */
    public ParsedRubric parse(InputStream inputStream) throws RubricParseException {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new RubricParseException("XLSX file has no data on the first sheet");
            }

            // Read header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null || headerRow.getLastCellNum() < 2) {
                throw new RubricParseException("XLSX header must have at least 2 columns (criterion title + at least one level)");
            }

            // Extract level labels from header
            List<String> levelLabels = new ArrayList<>();
            for (int i = 1; i < headerRow.getLastCellNum(); i++) {
                String label = getCellStringValue(headerRow.getCell(i)).trim();
                if (!label.isEmpty()) {
                    levelLabels.add(label);
                }
            }

            if (levelLabels.isEmpty()) {
                throw new RubricParseException("XLSX header contains no performance level labels after the criterion column");
            }

            // Parse data rows
            List<ParsedRubric.ParsedCriterion> criteria = new ArrayList<>();
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String title = getCellStringValue(row.getCell(0)).trim();
                if (title.isEmpty()) continue;

                ParsedRubric.ParsedCriterion criterion = parseCriterionRow(title, row, levelLabels);
                criteria.add(criterion);
            }

            if (criteria.isEmpty()) {
                throw new RubricParseException("No criteria found in XLSX — all data rows are empty");
            }

            return ParsedRubric.builder().criteria(criteria).build();

        } catch (IOException e) {
            throw new RubricParseException("Failed to read XLSX file: " + e.getMessage());
        }
    }

    private ParsedRubric.ParsedCriterion parseCriterionRow(String title, Row row,
                                                            List<String> levelLabels) {
        List<ParsedRubric.ParsedLevel> levels = new ArrayList<>();
        boolean requiresCompletion = false;
        BigDecimal maxPoints = null;

        for (int i = 0; i < levelLabels.size(); i++) {
            int colIdx = i + 1;
            Cell cell = colIdx < row.getLastCellNum() ? row.getCell(colIdx) : null;
            String cellValue = getCellStringValue(cell).trim();

            if (cellValue.isEmpty()) {
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
                    points = null;
                    requiresCompletion = true;
                }
            } else {
                // Check if the cell is purely numeric (POI may read it as a number)
                if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                    // Numeric cell — treat as points-only, description is empty
                    points = BigDecimal.valueOf(cell.getNumericCellValue());
                    description = cellValue;
                } else {
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

    /**
     * Extracts a string value from a cell, handling numeric and other types.
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                // If it's a whole number, format without decimal
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield "";
                    }
                }
            }
            default -> "";
        };
    }
}
