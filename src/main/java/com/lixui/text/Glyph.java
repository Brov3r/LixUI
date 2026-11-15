package com.lixui.text;

import com.lixui.geometry.Rect;

import java.util.Objects;

/**
 * A glyph baked into a font atlas.
 * The offset is relative to the text origin and the baseline: {@code y} is negative for glyphs that
 * rise above the baseline. Width and height are the bitmap size in logical UI units.
 *
 * @param codepoint Unicode code point
 * @param uv normalized atlas region, or an empty rectangle for glyphs without a bitmap
 * @param offsetX horizontal offset from the pen position to the bitmap's left edge
 * @param offsetY vertical offset from the baseline to the bitmap's top edge
 * @param width bitmap width in logical UI units
 * @param height bitmap height in logical UI units
 * @param advance horizontal pen advance in logical UI units, which may be negative for marks
 */
public record Glyph(int codepoint, Rect uv, float offsetX, float offsetY,
                    float width, float height, float advance) {
    /**
     * Creates a baked glyph.
     *
     * @param codepoint Unicode code point
     * @param uv normalized atlas region
     * @param offsetX horizontal offset from the pen position
     * @param offsetY vertical offset from the baseline
     * @param width bitmap width in logical UI units
     * @param height bitmap height in logical UI units
     * @param advance horizontal pen advance in logical UI units
     * @throws NullPointerException if {@code uv} is null
     * @throws IllegalArgumentException if the code point or a metric is invalid
     */
    public Glyph {
        Objects.requireNonNull(uv, "uv");
        if (codepoint < 0 || codepoint > Character.MAX_CODE_POINT) {
            throw new IllegalArgumentException("Glyph code point is out of range.");
        }
        if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY)
                || !Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(advance)) {
            throw new IllegalArgumentException("Glyph metrics must be finite.");
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Glyph dimensions must be non-negative.");
        }
    }
}
