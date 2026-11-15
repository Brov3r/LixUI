package com.lixui.render;

import com.lixui.geometry.Rect;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A triangle mesh primitive with a color per vertex.
 * The vertex list contains complete triangles, so its size is a non-zero multiple of three.
 */
public final class MeshCommand implements DrawCommand {
    private final List<MeshVertex> vertices;
    private final Rect bounds;
    private final Rect clip;

    /**
     * Creates a mesh command and defensively copies the vertex list.
     *
     * @param vertices triangle vertices in submission order
     * @param bounds axis-aligned bounds of the mesh
     * @param clip clipping rectangle that applies to this command
     * @throws NullPointerException if any argument or vertex is null
     * @throws IllegalArgumentException if the vertex count is not a non-zero multiple of three
     */
    public MeshCommand(List<MeshVertex> vertices, Rect bounds, Rect clip) {
        this(vertices, bounds, clip, true);
    }

    static MeshCommand owned(List<MeshVertex> vertices, Rect bounds, Rect clip) {
        return new MeshCommand(vertices, bounds, clip, false);
    }

    private MeshCommand(List<MeshVertex> vertices, Rect bounds, Rect clip, boolean copy) {
        this.vertices = copy ? List.copyOf(Objects.requireNonNull(vertices, "vertices"))
                : Collections.unmodifiableList(Objects.requireNonNull(vertices, "vertices"));
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.clip = Objects.requireNonNull(clip, "clip");
        if (this.vertices.isEmpty() || this.vertices.size() % 3 != 0) {
            throw new IllegalArgumentException("Mesh vertices must contain complete triangles.");
        }
    }

    /**
     * Returns the vertices in submission order.
     *
     * @return an unmodifiable vertex list
     */
    public List<MeshVertex> vertices() {
        return vertices;
    }

    /**
     * Returns the axis-aligned bounds of the mesh.
     *
     * @return the mesh bounds
     */
    @Override
    public Rect bounds() {
        return bounds;
    }

    /**
     * Returns the clipping rectangle captured when the command was submitted.
     *
     * @return the active clipping rectangle
     */
    @Override
    public Rect clip() {
        return clip;
    }
}
