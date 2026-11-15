package com.lixui.geometry;

/**
 * A mutable position in logical UI units, with X increasing rightward and Y downward.
 * Coordinates are finite and may be negative; the caller determines the origin and coordinate space.
 */
public final class Position {
    private float x;
    private float y;

    /**
     * Creates a position at the origin of the caller's coordinate space.
     */
    public Position() {
        this(0, 0);
    }

    /**
     * Creates a position, treating negative zero as zero.
     *
     * @param x horizontal coordinate in logical UI units
     * @param y vertical coordinate in logical UI units
     * @throws IllegalArgumentException if a coordinate is non-finite
     */
    public Position(float x, float y) {
        set(x, y);
    }

    /**
     * Returns the horizontal coordinate in logical UI units.
     *
     * @return the finite horizontal coordinate
     */
    public float x() {
        return x;
    }

    /**
     * Returns the vertical coordinate in logical UI units.
     *
     * @return the finite vertical coordinate
     */
    public float y() {
        return y;
    }

    /**
     * Replaces both coordinates, treating negative zero as zero.
     * Invalid input leaves both coordinates unchanged.
     *
     * @param x horizontal coordinate in logical UI units
     * @param y vertical coordinate in logical UI units
     * @return this position
     * @throws IllegalArgumentException if a coordinate is non-finite
     */
    public Position set(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Position coordinates must be finite.");
        }
        this.x = x == 0 ? 0 : x;
        this.y = y == 0 ? 0 : y;
        return this;
    }

    /**
     * Replaces the horizontal coordinate while preserving the vertical coordinate.
     *
     * @param x horizontal coordinate in logical UI units
     * @return this position
     * @throws IllegalArgumentException if x is non-finite; the position remains unchanged
     */
    public Position setX(float x) {
        return set(x, y);
    }

    /**
     * Replaces the vertical coordinate while preserving the horizontal coordinate.
     *
     * @param y vertical coordinate in logical UI units
     * @return this position
     * @throws IllegalArgumentException if y is non-finite; the position remains unchanged
     */
    public Position setY(float y) {
        return set(x, y);
    }

    /**
     * Shifts this position by the supplied distances.
     * Invalid input or overflow leaves both coordinates unchanged.
     *
     * @param dx horizontal displacement in logical UI units
     * @param dy vertical displacement in logical UI units
     * @return this position
     * @throws IllegalArgumentException if a displacement or resulting coordinate is non-finite
     */
    public Position offset(float dx, float dy) {
        return set(x + dx, y + dy);
    }
}
