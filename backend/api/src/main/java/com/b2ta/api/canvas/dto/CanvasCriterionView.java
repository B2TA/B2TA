package com.b2ta.api.canvas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A rubric criterion in the shape the marking view renders, mirroring the {@code CRITERIA}
 * constant the prototype was built against so the UI needs no restructuring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasCriterionView {

    /** Canvas criterion id, verbatim (e.g. {@code _1838}). Required for write-back. */
    private String id;

    private String label;

    private String description;

    private Double maxPts;

    /** Hex color for this criterion's highlights, assigned by position. */
    private String color;

    /** Translucent fill derived from {@link #color}. */
    private String bg;

    /** Border color; same as {@link #color}. */
    private String border;

    /** Selectable levels, ordered by points descending. */
    private List<CanvasLevelView> levels;
}
