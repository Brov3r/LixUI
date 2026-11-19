package com.lixui.text;

import com.lixui.geometry.Rect;

import java.util.Objects;

/**
 * A glyph positioned by {@link Font#layout(String, float, float)}.
 * The bounds are in logical UI units and the UV region addresses the font atlas.
 *
 * @param bounds pixel-aligned destination rectangle
 * @param uv normalized atlas region
 */
public record TextGlyph(Rect bounds, Rect uv) {
    /**
     * Creates a positioned glyph.
     *
     * @param bounds pixel-aligned destination rectangle
     * @param uv normalized atlas region
     * @throws NullPointerException if an argument is null
     */
    public TextGlyph {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
    }

    /**
     * Returns a glyph with its destination rectangle moved by the supplied offset.
     *
     * @param dx horizontal offset in logical UI units
     * @param dy vertical offset in logical UI units
     * @return the translated glyph, or this glyph when the offset is zero
     */
    public TextGlyph translate(float dx, float dy) {
        if (dx == 0 && dy == 0) {
            return this;
        }
        return new TextGlyph(new Rect(bounds.x() + dx, bounds.y() + dy,
                bounds.width(), bounds.height()), uv);
    }
}
