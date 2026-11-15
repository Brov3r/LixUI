package com.lixui.graphics;

import java.util.Objects;

/**
 * A single color stop in a gradient.
 * Offsets are normalized to {@code [0, 1]}; equal offsets create a hard transition.
 *
 * @param offset position along the gradient in {@code [0, 1]}
 * @param color color at this position
 */
public record GradientStop(float offset, Color color) {
    /**
     * Creates a gradient stop, treating negative zero as zero.
     *
     * @param offset position along the gradient in {@code [0, 1]}
     * @param color color at this position
     * @throws NullPointerException if {@code color} is null
     * @throws IllegalArgumentException if the offset is non-finite or outside {@code [0, 1]}
     */
    public GradientStop {
        Objects.requireNonNull(color, "color");
        if (!Float.isFinite(offset) || offset < 0 || offset > 1) {
            throw new IllegalArgumentException("Gradient offset must be finite and between 0 and 1.");
        }
        offset = offset == 0 ? 0 : offset;
    }
}
