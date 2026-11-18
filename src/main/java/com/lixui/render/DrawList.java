package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.text.TextGlyph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * An ordered list of draw commands with a nested clipping stack.
 * The initial clip is the viewport, and every emitted command captures the current intersection.
 * Instances are reusable through {@link #reset(Rect)}.
 */
public final class DrawList {
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);

    private final List<DrawCommand> commands = new ArrayList<>();
    private final List<DrawCommand> commandView = Collections.unmodifiableList(commands);
    private final Deque<Rect> clips = new ArrayDeque<>();
    private final Deque<MaskState> masks = new ArrayDeque<>();
    private Rect viewport;

    /**
     * Creates an empty list clipped to the supplied viewport.
     *
     * @param viewport the full render target in logical UI units
     * @throws NullPointerException if {@code viewport} is null
     */
    public DrawList(Rect viewport) {
        reset(viewport);
    }

    /**
     * Clears all commands and restores the clip to a new viewport.
     *
     * @param viewport the full render target in logical UI units
     * @throws NullPointerException if {@code viewport} is null
     */
    public void reset(Rect viewport) {
        Objects.requireNonNull(viewport, "viewport");
        commands.clear();
        clips.clear();
        masks.clear();
        this.viewport = viewport;
        clips.push(viewport);
    }

    void shape(Rect bounds, float radius, boolean ellipse, Paint paint, Transform transform) {
        ShapeCommand command = new ShapeCommand(bounds, radius, ellipse, paint, clips.peek(), transform);
        if (!bounds.isEmpty() && !clips.peek().isEmpty()) {
            commands.add(command);
        }
    }

    /**
     * Returns the viewport supplied by the last reset.
     *
     * @return the current viewport
     */
    public Rect viewport() {
        return viewport;
    }

    /**
     * Returns the intersection of all active clips.
     *
     * @return the current clip rectangle
     */
    public Rect clip() {
        return clips.peek();
    }

    /**
     * Intersects the current clip with the supplied rectangle and pushes the result.
     * The clip may become empty, and subsequent commands are omitted until it is popped.
     *
     * @param clip the additional clip rectangle
     * @throws NullPointerException if {@code clip} is null
     */
    public void pushClip(Rect clip) {
        Objects.requireNonNull(clip, "clip");
        clips.push(clips.peek().intersect(clip));
    }

    /**
     * Restores the clip that was active before the matching {@link #pushClip(Rect)}.
     *
     * @throws IllegalStateException if only the viewport clip remains
     */
    public void popClip() {
        if (clips.size() == 1) {
            throw new IllegalStateException("Cannot pop the viewport clip.");
        }
        clips.pop();
    }

    /**
     * Pushes a stencil mask built from filled triangles.
     * Content submitted until the matching {@link #popMask()} is clipped to the mask.
     *
     * @param vertices mask triangle vertices in submission order
     * @throws NullPointerException if {@code vertices} or a vertex is null
     * @throws IllegalArgumentException if the vertex count is not a multiple of three
     */
    public void pushMask(List<MeshVertex> vertices) {
        Objects.requireNonNull(vertices, "vertices");
        if (!vertices.isEmpty() && vertices.size() % 3 != 0) {
            throw new IllegalArgumentException("Mask vertices must contain complete triangles.");
        }
        Rect clip = clips.peek();
        Rect bounds = boundsOf(vertices);
        MaskState state = new MaskState(List.copyOf(vertices), null, bounds, Rect.EMPTY,
                Transform.IDENTITY, clip);
        masks.push(state);
        commands.add(new MaskBeginCommand(state.vertices(), state.bounds(), state.clip()));
    }

    /**
     * Pushes a stencil mask driven by a texture's alpha channel.
     * Content submitted until the matching {@link #popMask()} is clipped to the non-transparent
     * parts of the sampled texture region.
     *
     * @param texture mask texture
     * @param bounds destination rectangle in logical UI units
     * @param uv sampled texture region
     * @throws NullPointerException if any argument is null
     */
    public void pushMask(Texture texture, Rect bounds, Rect uv) {
        pushMask(texture, bounds, uv, Transform.IDENTITY);
    }

    /**
     * Pushes a stencil mask driven by a texture's alpha channel with an affine transform.
     * Content submitted until the matching {@link #popMask()} is clipped to the non-transparent
     * parts of the sampled texture region, mapped by the transform.
     *
     * @param texture mask texture
     * @param bounds destination rectangle before {@code transform} is applied
     * @param uv sampled texture region
     * @param transform affine transform applied to the destination rectangle
     * @throws NullPointerException if any argument is null
     */
    public void pushMask(Texture texture, Rect bounds, Rect uv, Transform transform) {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(transform, "transform");
        MaskState state = new MaskState(null, texture, bounds, uv, transform, clips.peek());
        masks.push(state);
        commands.add(new MaskTextureBeginCommand(texture, bounds, uv, state.clip(), transform));
    }

    /**
     * Ends the most recently pushed stencil mask scope.
     *
     * @throws IllegalStateException if no mask is active
     */
    public void popMask() {
        if (masks.isEmpty()) {
            throw new IllegalStateException("Cannot pop without an active mask.");
        }
        MaskState state = masks.pop();
        if (state.vertices() != null) {
            commands.add(new MaskEndCommand(state.vertices(), state.bounds(), state.clip()));
        } else {
            commands.add(new MaskTextureEndCommand(
                    state.texture(), state.bounds(), state.uv(), state.clip(), state.transform()));
        }
    }

    /**
     * Appends a rectangle with sharp corners using the current clip.
     *
     * @param bounds the rectangle to fill
     * @param color the fill color
     * @throws NullPointerException if {@code bounds} or {@code color} is null
     */
    public void rect(Rect bounds, Color color) {
        roundedRect(bounds, 0, color);
    }

    /**
     * Appends a rounded rectangle using the current clip.
     * Commands with empty bounds or an empty clip are omitted.
     *
     * @param bounds the rectangle to fill
     * @param radius corner radius in logical UI units
     * @param color the fill color
     * @throws NullPointerException if {@code bounds} or {@code color} is null
     * @throws IllegalArgumentException if radius is non-finite or negative
     */
    public void roundedRect(Rect bounds, float radius, Color color) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(color, "color");
        Rect clip = clips.peek();
        if (bounds.isEmpty() || clip.isEmpty()) {
            return;
        }
        commands.add(new RoundedRectCommand(bounds, radius, color, clip));
    }

    /**
     * Appends a texture with the full normalized UV range and no tint.
     *
     * @param texture the texture to draw
     * @param bounds the destination rectangle
     * @throws NullPointerException if {@code texture} or {@code bounds} is null
     */
    public void image(Texture texture, Rect bounds) {
        image(texture, bounds, 0, FULL_UV, Color.WHITE, Transform.IDENTITY);
    }

    /**
     * Appends a texture with an explicit UV region and no tint.
     * The UV region selects a normalized top-left sub-rectangle of the texture, so atlases can be
     * sampled by supplying the sprite region.
     *
     * @param texture the texture to draw
     * @param bounds the destination rectangle
     * @param uv the sampled texture region
     * @throws NullPointerException if any argument is null
     */
    public void image(Texture texture, Rect bounds, Rect uv) {
        image(texture, bounds, 0, uv, Color.WHITE, Transform.IDENTITY);
    }

    /**
     * Appends a textured rounded rectangle using the current clip.
     * Commands with empty bounds or an empty clip are omitted.
     *
     * @param texture the texture to draw
     * @param bounds the destination rectangle
     * @param radius corner radius in logical UI units
     * @param uv the sampled texture region
     * @param tint straight-alpha tint multiplied with the sampled texel
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if radius is non-finite or negative
     */
    public void image(Texture texture, Rect bounds, float radius, Rect uv, Color tint) {
        image(texture, bounds, radius, uv, tint, Transform.IDENTITY);
    }

    /**
     * Appends a textured rounded rectangle with an affine transform using the current clip.
     * The transform maps {@code bounds}, so the corner radius and texture coordinates stay local.
     * Commands with empty bounds or an empty clip are omitted.
     *
     * @param texture the texture to draw
     * @param bounds the destination rectangle before {@code transform} is applied
     * @param radius corner radius in logical UI units before {@code transform} is applied
     * @param uv the sampled texture region
     * @param tint straight-alpha tint multiplied with the sampled texel
     * @param transform affine transform applied to the destination rectangle
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if radius is non-finite or negative
     */
    public void image(Texture texture, Rect bounds, float radius, Rect uv, Color tint,
                      Transform transform) {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(tint, "tint");
        Objects.requireNonNull(transform, "transform");
        Rect clip = clips.peek();
        if (bounds.isEmpty() || clip.isEmpty()) {
            return;
        }
        commands.add(new ImageCommand(bounds, radius, uv, texture, tint, clip, transform));
    }

    /**
     * Appends a batched text block using the current clip.
     * Commands with no glyphs or an empty clip are omitted.
     *
     * @param atlas font atlas texture
     * @param glyphs positioned glyph quads
     * @param colors tint per glyph, aligned with {@code glyphs}
     * @param transform affine transform applied to every glyph
     * @throws NullPointerException if an argument or list element is null
     * @throws IllegalArgumentException if the color count differs from the glyph count
     */
    public void text(Texture atlas, List<TextGlyph> glyphs, List<Color> colors,
                     Transform transform) {
        Objects.requireNonNull(atlas, "atlas");
        Objects.requireNonNull(glyphs, "glyphs");
        Objects.requireNonNull(colors, "colors");
        Objects.requireNonNull(transform, "transform");
        Rect clip = clips.peek();
        if (glyphs.isEmpty() || clip.isEmpty()) {
            return;
        }
        commands.add(new TextCommand(atlas, glyphs, colors, clip, transform));
    }

    /**
     * Appends a triangle mesh using the current clip.
     * Commands with empty bounds or an empty clip are omitted.
     *
     * @param vertices triangle vertices in submission order
     * @throws NullPointerException if {@code vertices} or a vertex is null
     * @throws IllegalArgumentException if the vertex count is not a non-zero multiple of three
     */
    public void mesh(List<MeshVertex> vertices) {
        appendMesh(vertices, false);
    }

    void meshOwned(List<MeshVertex> vertices) {
        appendMesh(vertices, true);
    }

    private void appendMesh(List<MeshVertex> vertices, boolean owned) {
        Objects.requireNonNull(vertices, "vertices");
        if (vertices.isEmpty()) {
            return;
        }
        if (vertices.size() % 3 != 0) {
            throw new IllegalArgumentException("Mesh vertices must contain complete triangles.");
        }
        Rect clip = clips.peek();
        if (clip.isEmpty()) {
            return;
        }
        Rect bounds = boundsOf(vertices);
        if (bounds.isEmpty()) {
            return;
        }
        commands.add(owned ? MeshCommand.owned(vertices, bounds, clip)
                : new MeshCommand(vertices, bounds, clip));
    }

    /**
     * Returns an unmodifiable view of the emitted commands in submission order.
     *
     * @return the commands captured since the last reset
     */
    public List<DrawCommand> commands() {
        return commandView;
    }

    private static Rect boundsOf(List<MeshVertex> vertices) {
        if (vertices.isEmpty()) {
            return Rect.EMPTY;
        }
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (MeshVertex vertex : vertices) {
            left = Math.min(left, vertex.x());
            top = Math.min(top, vertex.y());
            right = Math.max(right, vertex.x());
            bottom = Math.max(bottom, vertex.y());
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private record MaskState(List<MeshVertex> vertices, Texture texture,
                             Rect bounds, Rect uv, Transform transform, Rect clip) {
    }
}
