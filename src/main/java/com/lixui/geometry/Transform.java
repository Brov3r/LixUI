package com.lixui.geometry;

import java.util.Objects;

/**
 * An immutable 2D affine transform stored as a 3x2 matrix:
 * <pre>
 * [ m00 m01 m02 ]
 * [ m10 m11 m12 ]
 * [  0   0   1  ]
 * </pre>
 * All components are finite. Composition follows standard matrix multiplication, so
 * {@code parent.multiply(child)} applies {@code child} first and then {@code parent}.
 *
 * @param m00 horizontal scale and rotation component
 * @param m01 horizontal shear and rotation component
 * @param m02 horizontal translation
 * @param m10 vertical shear and rotation component
 * @param m11 vertical scale and rotation component
 * @param m12 vertical translation
 */
public record Transform(float m00, float m01, float m02, float m10, float m11, float m12) {
    /** The identity transform. */
    public static final Transform IDENTITY = new Transform(1, 0, 0, 0, 1, 0);

    /**
     * Creates a transform, treating negative zero as zero.
     *
     * @param m00 horizontal scale and rotation component
     * @param m01 horizontal shear and rotation component
     * @param m02 horizontal translation
     * @param m10 vertical shear and rotation component
     * @param m11 vertical scale and rotation component
     * @param m12 vertical translation
     * @throws IllegalArgumentException if a component is non-finite
     */
    public Transform {
        if (!Float.isFinite(m00) || !Float.isFinite(m01) || !Float.isFinite(m02)
                || !Float.isFinite(m10) || !Float.isFinite(m11) || !Float.isFinite(m12)) {
            throw new IllegalArgumentException("Transform components must be finite.");
        }
        m00 = normalize(m00);
        m01 = normalize(m01);
        m02 = normalize(m02);
        m10 = normalize(m10);
        m11 = normalize(m11);
        m12 = normalize(m12);
    }

    /**
     * Creates a translation transform.
     *
     * @param dx horizontal translation
     * @param dy vertical translation
     * @return the translation transform
     * @throws IllegalArgumentException if a component is non-finite
     */
    public static Transform translation(float dx, float dy) {
        return new Transform(1, 0, dx, 0, 1, dy);
    }

    /**
     * Creates a scaling transform around the origin.
     *
     * @param sx horizontal scale
     * @param sy vertical scale
     * @return the scaling transform
     * @throws IllegalArgumentException if a component is non-finite
     */
    public static Transform scaling(float sx, float sy) {
        return new Transform(sx, 0, 0, 0, sy, 0);
    }

    /**
     * Creates a rotation transform around the origin.
     *
     * @param radians rotation angle in radians
     * @return the rotation transform
     * @throws IllegalArgumentException if the angle is non-finite
     */
    public static Transform rotation(float radians) {
        if (!Float.isFinite(radians)) {
            throw new IllegalArgumentException("Rotation angle must be finite.");
        }
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        return new Transform(cosine, -sine, 0, sine, cosine, 0);
    }

    /**
     * Multiplies this transform by another transform.
     * The other transform is applied first.
     *
     * @param other transform to apply before this one
     * @return the composed transform
     * @throws NullPointerException if {@code other} is null
     * @throws IllegalArgumentException if a resulting component is non-finite
     */
    public Transform multiply(Transform other) {
        Objects.requireNonNull(other, "other");
        return new Transform(
                m00 * other.m00 + m01 * other.m10,
                m00 * other.m01 + m01 * other.m11,
                m00 * other.m02 + m01 * other.m12 + m02,
                m10 * other.m00 + m11 * other.m10,
                m10 * other.m01 + m11 * other.m11,
                m10 * other.m02 + m11 * other.m12 + m12);
    }

    /**
     * Returns a transform translated after this transform is applied.
     *
     * @param dx horizontal translation
     * @param dy vertical translation
     * @return the translated transform
     */
    public Transform translate(float dx, float dy) {
        return translation(dx, dy).multiply(this);
    }

    /**
     * Returns a transform scaled after this transform is applied.
     *
     * @param sx horizontal scale
     * @param sy vertical scale
     * @return the scaled transform
     */
    public Transform scale(float sx, float sy) {
        return scaling(sx, sy).multiply(this);
    }

    /**
     * Returns a transform rotated after this transform is applied.
     *
     * @param radians rotation angle in radians
     * @return the rotated transform
     */
    public Transform rotate(float radians) {
        return rotation(radians).multiply(this);
    }

    /**
     * Applies this transform to a point.
     *
     * @param point point to transform
     * @return the transformed point
     * @throws NullPointerException if {@code point} is null
     * @throws IllegalArgumentException if the result is non-finite
     */
    public Point apply(Point point) {
        Objects.requireNonNull(point, "point");
        return new Point(
                m00 * point.x() + m01 * point.y() + m02,
                m10 * point.x() + m11 * point.y() + m12);
    }

    /**
     * Reports whether this transform leaves every point unchanged.
     *
     * @return true for the identity matrix
     */
    public boolean isIdentity() {
        return m00 == 1 && m01 == 0 && m02 == 0 && m10 == 0 && m11 == 1 && m12 == 0;
    }

    private static float normalize(float value) {
        return value == 0 ? 0 : value;
    }
}
