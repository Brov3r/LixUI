package com.lixui.render.command;

import com.lixui.geometry.Rect;
import com.lixui.render.MeshVertex;

import java.util.List;
import java.util.Objects;

/**
 * Starts a stencil mask scope.
 * The mask geometry is a filled triangle mesh; it is rendered into the stencil buffer and content
 * submitted until the matching {@link MaskEndCommand} is clipped to that mask.
 *
 * @param vertices mask triangle vertices in submission order
 * @param bounds axis-aligned bounds of the mask
 * @param clip clipping rectangle captured when the mask was pushed
 */
public record MaskBeginCommand(List<MeshVertex> vertices, Rect bounds, Rect clip) implements DrawCommand {
    /**
     * Creates a mask begin command and copies the vertex list.
     *
     * @param vertices mask triangle vertices in submission order
     * @param bounds axis-aligned bounds of the mask
     * @param clip clipping rectangle captured when the mask was pushed
     * @throws NullPointerException if any argument or vertex is null
     * @throws IllegalArgumentException if the vertex count is not a multiple of three
     */
    public MaskBeginCommand {
        Objects.requireNonNull(vertices, "vertices");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(clip, "clip");
        vertices = List.copyOf(vertices);
        if (vertices.size() % 3 != 0) {
            throw new IllegalArgumentException("Mask vertices must contain complete triangles.");
        }
    }
}
