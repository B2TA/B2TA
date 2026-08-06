package com.b2ta.api.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One row of a Canvas rubric.
 *
 * <p>The {@code id} is an underscore-prefixed string such as {@code _1838}, not an
 * integer. It must be preserved verbatim: write-back keys {@code rubric_assessment} by
 * this id, and Canvas silently records nothing if it receives an id it does not
 * recognise (Requirement 1.5).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasRubricCriterion {

    private String id;

    private Double points;

    /** Short label, e.g. "Thesis Clarity". */
    private String description;

    /** Expanded explanation; may be absent. */
    @JsonProperty("long_description")
    private String longDescription;

    @JsonProperty("criterion_use_range")
    private Boolean criterionUseRange;

    private List<CanvasRating> ratings;
}
