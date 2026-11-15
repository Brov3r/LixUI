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
}
