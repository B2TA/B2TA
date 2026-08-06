package com.b2ta.api.canvas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A Canvas user. Included on submissions via {@code include[]=user}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CanvasUser {

    private Long id;

    private String name;

    @JsonProperty("sortable_name")
    private String sortableName;

    @JsonProperty("short_name")
    private String shortName;
}
