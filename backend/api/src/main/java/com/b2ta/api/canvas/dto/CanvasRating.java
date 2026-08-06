package com.b2ta.api.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One achievement level within a Canvas rubric criterion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasRating {

    private String id;

    private Double points;

    /** Short label, e.g. "Proficient". */
    private String description;

    @JsonProperty("long_description")
    private String longDescription;
}
