package com.b2ta.api.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One criterion's score within a {@code rubric_assessment} map, both when reading an
 * existing assessment and when writing one back.
 *
 * <p>The write shape confirmed against the live instance is
 * {@code {"_4887": {"rating_id": "blank", "comments": "", "points": 5.0}}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasRubricAssessmentEntry {

    private Double points;

    /**
     * Canvas rating id. The literal {@code "blank"} tells Canvas the score was entered
     * as a raw point value rather than by picking a named rating.
     */
    @JsonProperty("rating_id")
    private String ratingId;

    private String comments;
}
