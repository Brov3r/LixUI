package com.lixui.geometry;

import java.util.Objects;

/**
 * An immutable axis-aligned rectangle in logical UI units, with X increasing rightward and Y downward.
 * Coordinates are finite, dimensions are finite and non-negative, and negative zero is normalized to zero.
 *
 * @param x horizontal coordinate of the left edge
 * @param y vertical coordinate of the top edge
 * @param width horizontal extent
 * @param height vertical extent
 */
public record Rect(float x, float y, float width, float height) {
    /** An empty rectangle at the origin. */
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    /**
     * Creates a rectangle, treating negative zero as zero.
     *
     * @param x horizontal coordinate of the left edge
     * @param y vertical coordinate of the top edge
     * @param width horizontal extent
     * @param height vertical extent
     * @throws IllegalArgumentException if a component is non-finite or a dimension is negative
     */
    public Rect {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Rectangle coordinates and dimensions must be finite.");
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Rectangle dimensions must be non-negative.");
        }
        x = x == 0 ? 0 : x;
        y = y == 0 ? 0 : y;
        width = width == 0 ? 0 : width;
        height = height == 0 ? 0 : height;
    }

    /**
     * Returns the center point of this rectangle.
     *
     * @return the center point
     */
    public Point center() {
        return new Point(x + width / 2, y + height / 2);
    }

    /**
     * Returns the horizontal coordinate of the right edge.
     *
     * @return the left edge plus the width
     */
    public float right() {
        return x + width;
    }

    /**
     * Returns the vertical coordinate of the bottom edge.
     *
     * @return the top edge plus the height
     */
    public float bottom() {
        return y + height;
    }

    /**
     * Reports whether either dimension is zero.
     *
     * @return true if the rectangle has no area
     */
    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /**
     * Reports whether a point lies inside this rectangle using half-open edges.
     * Points on the right or bottom edge are outside, so adjacent rectangles never overlap.
     *
     * @param pointX horizontal coordinate in logical UI units
     * @param pointY vertical coordinate in logical UI units
     * @return true if the point is inside the rectangle
     */
    public boolean contains(float pointX, float pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    /**
     * Computes the overlap between this rectangle and another rectangle.
     * Rectangles that touch without overlapping produce an empty rectangle.
     *
     * @param other rectangle to intersect with
     * @return the overlapping rectangle, or an empty rectangle when there is no overlap
     * @throws NullPointerException if {@code other} is null
     */
    public Rect intersect(Rect other) {
        Objects.requireNonNull(other, "other");
        float left = Math.max(x, other.x);
        float top = Math.max(y, other.y);
        float right = Math.min(right(), other.right());
        float bottom = Math.min(bottom(), other.bottom());
        if (right <= left || bottom <= top) {
            return EMPTY;
        }
        return new Rect(left, top, right - left, bottom - top);
    }
}
