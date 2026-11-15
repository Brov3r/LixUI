package com.lixui.geometry;

/**
 * A mutable size in logical UI units, independent of framebuffer scale.
 * Dimensions are finite and non-negative.
 */
public final class Size {
    private float width;
    private float height;

    /**
     * Creates a size with both dimensions set to zero.
     */
    public Size() {
        this(0, 0);
    }

    /**
     * Creates a size, treating negative zero as zero.
     *
     * @param width horizontal extent in logical UI units
     * @param height vertical extent in logical UI units
     * @throws IllegalArgumentException if a dimension is negative or non-finite
     */
    public Size(float width, float height) {
        set(width, height);
    }

    /**
     * Returns the horizontal extent in logical UI units.
     *
     * @return the non-negative width
     */
    public float width() {
        return width;
    }

    /**
     * Returns the vertical extent in logical UI units.
     *
     * @return the non-negative height
     */
    public float height() {
        return height;
    }

    /**
     * Replaces both dimensions, treating negative zero as zero.
     * Invalid input leaves both dimensions unchanged.
     *
     * @param width horizontal extent in logical UI units
     * @param height vertical extent in logical UI units
     * @return this size
     * @throws IllegalArgumentException if a dimension is negative or non-finite
     */
    public Size set(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0) {
            throw new IllegalArgumentException("Size dimensions must be finite and non-negative.");
        }
        this.width = width == 0 ? 0 : width;
        this.height = height == 0 ? 0 : height;
        return this;
    }

    /**
     * Replaces the width while preserving the height.
     *
     * @param width horizontal extent in logical UI units
     * @return this size
     * @throws IllegalArgumentException if width is negative or non-finite; the size remains unchanged
     */
    public Size setWidth(float width) {
        return set(width, height);
    }

    /**
     * Replaces the height while preserving the width.
     *
     * @param height vertical extent in logical UI units
     * @return this size
     * @throws IllegalArgumentException if height is negative or non-finite; the size remains unchanged
     */
    public Size setHeight(float height) {
        return set(width, height);
    }

    /**
     * Reports whether either dimension is zero.
     *
     * @return true if the size has no area
     */
    public boolean isEmpty() {
        return width == 0 || height == 0;
    }
}
