package com.lixui.geometry;

/**
 * An immutable point in logical UI units.
 * Coordinates are finite and negative zero is normalized to zero.
 *
 * @param x horizontal coordinate
 * @param y vertical coordinate
 */
public record Point(float x, float y) {
    /**
     * Creates a point, treating negative zero as zero.
     *
     * @param x horizontal coordinate
     * @param y vertical coordinate
     * @throws IllegalArgumentException if a coordinate is non-finite
     */
    public Point {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Point coordinates must be finite.");
        }
        x = x == 0 ? 0 : x;
        y = y == 0 ? 0 : y;
    }

    /**
     * Returns a point shifted by the supplied distances.
     *
     * @param dx horizontal displacement
     * @param dy vertical displacement
     * @return the shifted point
     * @throws IllegalArgumentException if a displacement or result is non-finite
     */
    public Point translate(float dx, float dy) {
        return new Point(x + dx, y + dy);
    }

    /**
     * Returns the distance to another point.
     *
     * @param other target point
     * @return the non-negative Euclidean distance
     * @throws NullPointerException if {@code other} is null
     */
    public float distance(Point other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return (float) Math.hypot(other.x - x, other.y - y);
    }

    /**
     * Linearly interpolates between this point and another point.
     *
     * @param other target point
     * @param t interpolation factor, typically in {@code [0, 1]}
     * @return the interpolated point
     * @throws NullPointerException if {@code other} is null
     * @throws IllegalArgumentException if {@code t} or the result is non-finite
     */
    public Point lerp(Point other, float t) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        if (!Float.isFinite(t)) {
            throw new IllegalArgumentException("Interpolation factor must be finite.");
        }
        return new Point(x + (other.x - x) * t, y + (other.y - y) * t);
    }
}
