package com.lixui.widget;

import com.lixui.geometry.Size;

import java.util.Objects;

/**
 * Finite minimum and maximum dimensions supplied to a widget measurement pass.
 * A parent constrains the size it accepts from a child; the child returns its natural size and the
 * constraint clamps it. Minimum values are never greater than the matching maximum values.
 *
 * @param minWidth minimum accepted width in logical UI units
 * @param maxWidth maximum accepted width in logical UI units
 * @param minHeight minimum accepted height in logical UI units
 * @param maxHeight maximum accepted height in logical UI units
 */
public record LayoutConstraints(float minWidth, float maxWidth, float minHeight, float maxHeight) {
    /**
     * Creates a constraint set.
     *
     * @param minWidth minimum accepted width in logical UI units
     * @param maxWidth maximum accepted width in logical UI units
     * @param minHeight minimum accepted height in logical UI units
     * @param maxHeight maximum accepted height in logical UI units
     * @throws IllegalArgumentException if a value is negative or non-finite, or a minimum exceeds
     *                                  its maximum
     */
    public LayoutConstraints {
        if (!Float.isFinite(minWidth) || !Float.isFinite(maxWidth)
                || !Float.isFinite(minHeight) || !Float.isFinite(maxHeight)) {
            throw new IllegalArgumentException("Layout constraints must be finite.");
        }
        if (minWidth < 0 || minHeight < 0) {
            throw new IllegalArgumentException("Minimum layout constraints must be non-negative.");
        }
        if (minWidth > maxWidth || minHeight > maxHeight) {
            throw new IllegalArgumentException("Minimum layout constraints must not exceed maximums.");
        }
    }

    /**
     * Creates loose constraints that allow any size up to the supplied maximum.
     *
     * @param maxWidth maximum accepted width in logical UI units
     * @param maxHeight maximum accepted height in logical UI units
     * @return loose constraints with zero minimums
     * @throws IllegalArgumentException if a maximum is negative or non-finite
     */
    public static LayoutConstraints loose(float maxWidth, float maxHeight) {
        return new LayoutConstraints(0, maxWidth, 0, maxHeight);
    }

    /**
     * Creates tight constraints that force an exact size.
     *
     * @param width required width in logical UI units
     * @param height required height in logical UI units
     * @return constraints with equal minimums and maximums
     * @throws IllegalArgumentException if a dimension is negative or non-finite
     */
    public static LayoutConstraints tight(float width, float height) {
        return new LayoutConstraints(width, width, height, height);
    }

    /**
     * Clamps a measured size into this constraint range.
     *
     * @param size measured size
     * @return a new size within the accepted range
     * @throws NullPointerException if {@code size} is null
     */
    public Size constrain(Size size) {
        Objects.requireNonNull(size, "size");
        return constrain(size.width(), size.height());
    }

    /**
     * Clamps dimensions into this constraint range.
     *
     * @param width measured width in logical UI units
     * @param height measured height in logical UI units
     * @return a new size within the accepted range
     * @throws IllegalArgumentException if a dimension is negative or non-finite
     */
    public Size constrain(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0) {
            throw new IllegalArgumentException("Measured dimensions must be finite and non-negative.");
        }
        return new Size(
                Math.max(minWidth, Math.min(maxWidth, width)),
                Math.max(minHeight, Math.min(maxHeight, height)));
    }
}
