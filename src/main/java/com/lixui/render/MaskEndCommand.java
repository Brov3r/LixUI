package com.lixui.render;

import com.lixui.geometry.Rect;

import java.util.List;
import java.util.Objects;

/**
 * Ends the most recently pushed stencil mask scope.
 * The same mask geometry is submitted again with the inverse stencil operation.
 *
 * @param vertices mask triangle vertices in submission order
 * @param bounds axis-aligned bounds of the mask
 * @param clip clipping rectangle captured when the mask was pushed
 */
public record MaskEndCommand(List<MeshVertex> vertices, Rect bounds, Rect clip) implements DrawCommand {
    /**
     * Creates a mask end command and copies the vertex list.
     *
     * @param vertices mask triangle vertices in submission order
     * @param bounds axis-aligned bounds of the mask
     * @param clip clipping rectangle captured when the mask was pushed
     * @throws NullPointerException if any argument or vertex is null
     * @throws IllegalArgumentException if the vertex count is not a multiple of three
     */
    public MaskEndCommand {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(clip, "clip");
        vertices = List.copyOf(vertices);
        if (vertices.size() % 3 != 0) {
            throw new IllegalArgumentException("Mask vertices must contain complete triangles.");
        }
    }
}
