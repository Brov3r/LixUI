package com.lixui.geometry;

/**
 * Non-negative distances from the four edges of a rectangle, in logical units or source pixels.
 * Values are finite and negative zero is normalized to zero.
 *
 * @param left distance from the left edge
 * @param top distance from the top edge
 * @param right distance from the right edge
 * @param bottom distance from the bottom edge
 */
public record Insets(float left, float top, float right, float bottom) {
    /**
     * Zero insets on every edge.
     */
    public static final Insets NONE = new Insets(0, 0, 0, 0);

    /**
     * Creates insets, validating every side.
     *
     * @param left distance from the left edge
     * @param top distance from the top edge
     * @param right distance from the right edge
     * @param bottom distance from the bottom edge
     * @throws IllegalArgumentException if a side is non-finite or negative
     */
    public Insets {
        if (!Float.isFinite(left) || !Float.isFinite(top)
                || !Float.isFinite(right) || !Float.isFinite(bottom)) {
            throw new IllegalArgumentException("Inset sides must be finite.");
        }
        if (left < 0 || top < 0 || right < 0 || bottom < 0) {
            throw new IllegalArgumentException("Inset sides must be non-negative.");
        }
        left = left == 0 ? 0 : left;
        top = top == 0 ? 0 : top;
        right = right == 0 ? 0 : right;
        bottom = bottom == 0 ? 0 : bottom;
    }

    /**
     * Returns insets with the same value on every side.
     *
     * @param value non-negative side length
     * @return uniform insets
     * @throws IllegalArgumentException if the value is non-finite or negative
     */
    public static Insets uniform(float value) {
        return new Insets(value, value, value, value);
    }
}
