package com.b2ta.worker.parsing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Intermediate result DTO holding the parsed rubric data before persistence.
 * Produced by CSV, XLSX, and PDF parsers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedRubric {

    private List<ParsedCriterion> criteria;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedCriterion {
        private String title;
        private String description;
        private BigDecimal maxPoints;
        private boolean requiresCompletion;
        private List<ParsedLevel> levels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedLevel {
        private String label;
        private String description;
        private BigDecimal points;
    }
}
