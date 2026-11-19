package com.lixui.text;

import com.lixui.graphics.Color;

import java.util.Objects;

/**
 * An offset drop shadow drawn behind text.
 *
 * @param offsetX horizontal shadow offset in logical UI units
 * @param offsetY vertical shadow offset in logical UI units
 * @param color shadow color
 */
public record TextShadow(float offsetX, float offsetY, Color color) {
    /**
     * Creates a text shadow.
     *
     * @param offsetX horizontal shadow offset in logical UI units
     * @param offsetY vertical shadow offset in logical UI units
     * @param color shadow color
     * @throws IllegalArgumentException if an offset is non-finite
     * @throws NullPointerException if {@code color} is null
     */
    public TextShadow {
        if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new IllegalArgumentException("Shadow offsets must be finite.");
        }
        Objects.requireNonNull(color, "color");
    }
}
