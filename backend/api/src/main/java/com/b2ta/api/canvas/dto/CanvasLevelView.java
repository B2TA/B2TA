package com.b2ta.api.canvas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One selectable performance level, in the shape the marking view renders.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasLevelView {

    /** Canvas rating id, preserved so a TA selection can name the rating it came from. */
    private String id;

    private Double pts;

    private String label;

    private String desc;
}
