package com.lixui.render;

import com.lixui.graphics.Color;

import java.util.Objects;

/**
 * A single vertex of a solid-color triangle mesh.
 * Coordinates are in logical UI units. Colors are straight-alpha and interpolated across the
 * triangle.
 *
 * @param x horizontal coordinate
 * @param y vertical coordinate
 * @param color vertex color
 */
public record MeshVertex(float x, float y, Color color) {
    /**
     * Creates a mesh vertex, treating negative zero as zero.
     *
     * @param x horizontal coordinate
     * @param y vertical coordinate
     * @param color vertex color
     * @throws NullPointerException if {@code color} is null
     * @throws IllegalArgumentException if a coordinate is non-finite
     */
    public MeshVertex {
        Objects.requireNonNull(color, "color");
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Mesh vertex coordinates must be finite.");
        }
        x = x == 0 ? 0 : x;
        y = y == 0 ? 0 : y;
    }
}
