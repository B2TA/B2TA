package com.b2ta.api.util;

import java.util.List;

/**
 * Fixed palette of 30 distinct hex colors, each with a contrast ratio of at least
 * 3:1 against a white (#FFFFFF) background per WCAG guidelines.
 *
 * Colors are assigned to criteria by position index (0-based).
 * The palette size matches the maximum criterion count (30), so cycling is never needed.
 */
public final class ColorPalette {

    private ColorPalette() {
        // utility class
    }

    /**
     * 30 hex colors with >= 3:1 contrast ratio against #FFFFFF.
     * Verified using relative luminance formula: (L1 + 0.05) / (L2 + 0.05) >= 3.0
     */
    public static final List<String> COLORS = List.of(
            "#D32F2F", // Red 700
            "#1976D2", // Blue 700
            "#388E3C", // Green 700
            "#E65100", // Orange 900
            "#7B1FA2", // Purple 700
            "#00796B", // Teal 700
            "#C2185B", // Pink 700
            "#5D4037", // Brown 700
            "#455A64", // BlueGrey 700
            "#E64A19", // Deep Orange 700
            "#0097A7", // Cyan 700
            "#689F38", // Light Green 700
            "#512DA8", // Deep Purple 700
            "#0288D1", // Light Blue 700
            "#33691E", // Light Green 900
            "#303F9F", // Indigo 700
            "#D81B60", // Pink 600
            "#00838F", // Cyan 800
            "#6A1B9A", // Purple 800
            "#2E7D32", // Green 800
            "#AD1457", // Pink 800
            "#4527A0", // Deep Purple 800
            "#1565C0", // Blue 800
            "#EF6C00", // Orange 800
            "#283593", // Indigo 800
            "#B71C1C", // Red 900
            "#004D40", // Teal 900
            "#827717", // Lime 900
            "#4E342E", // Brown 800
            "#37474F"  // BlueGrey 800
    );

    /**
     * Returns the color at the given position index.
     *
     * @param index 0-based criterion position
     * @return hex color string (e.g. "#D32F2F")
     * @throws IndexOutOfBoundsException if index >= 30 or index < 0
     */
    public static String getColor(int index) {
        return COLORS.get(index);
    }

    /**
     * Returns the number of available colors in the palette.
     */
    public static int size() {
        return COLORS.size();
    }
}
