package com.b2ta.api.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorPaletteTest {

    @Test
    void palette_hasAtLeast30Colors() {
        assertThat(ColorPalette.size()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void allColors_areValidHexFormat() {
        for (String color : ColorPalette.COLORS) {
            assertThat(color).matches("^#[0-9A-Fa-f]{6}$");
        }
    }

    @Test
    void allColors_areDistinct() {
        Set<String> unique = new HashSet<>(ColorPalette.COLORS);
        assertThat(unique).hasSameSizeAs(ColorPalette.COLORS);
    }

    @Test
    void allColors_haveMinimumContrastAgainstWhite() {
        // Verify each color has at least 3:1 contrast ratio against #FFFFFF
        for (String color : ColorPalette.COLORS) {
            double contrast = calculateContrastRatio(color, "#FFFFFF");
            assertThat(contrast)
                    .as("Color %s should have >= 3:1 contrast against white, was %.2f:1", color, contrast)
                    .isGreaterThanOrEqualTo(3.0);
        }
    }

    @Test
    void getColor_returnsCorrectColorByIndex() {
        assertThat(ColorPalette.getColor(0)).isEqualTo(ColorPalette.COLORS.get(0));
        assertThat(ColorPalette.getColor(29)).isEqualTo(ColorPalette.COLORS.get(29));
    }

    @Test
    void getColor_throwsForNegativeIndex() {
        assertThatThrownBy(() -> ColorPalette.getColor(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void getColor_throwsForIndexAt30() {
        assertThatThrownBy(() -> ColorPalette.getColor(30))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    /**
     * Calculates WCAG 2.1 contrast ratio between two hex colors.
     */
    private double calculateContrastRatio(String hex1, String hex2) {
        double lum1 = relativeLuminance(hex1);
        double lum2 = relativeLuminance(hex2);
        double lighter = Math.max(lum1, lum2);
        double darker = Math.min(lum1, lum2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private double relativeLuminance(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);

        double rLinear = linearize(r / 255.0);
        double gLinear = linearize(g / 255.0);
        double bLinear = linearize(b / 255.0);

        return 0.2126 * rLinear + 0.7152 * gLinear + 0.0722 * bLinear;
    }

    private double linearize(double value) {
        return value <= 0.03928
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
