package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;

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
    private final Deque<Rect> clips = new ArrayDeque<>();
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
        this.viewport = viewport;
        clips.push(viewport);
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
        image(texture, bounds, 0, FULL_UV, Color.WHITE);
    }

    /**
     * Appends a texture with an explicit UV region and no tint.
     *
     * @param texture the texture to draw
     * @param bounds the destination rectangle
     * @param uv the sampled texture region
     * @throws NullPointerException if any argument is null
     */
    public void image(Texture texture, Rect bounds, Rect uv) {
        image(texture, bounds, 0, uv, Color.WHITE);
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
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(tint, "tint");
        Rect clip = clips.peek();
        if (bounds.isEmpty() || clip.isEmpty()) {
            return;
        }
        commands.add(new ImageCommand(bounds, radius, uv, texture, tint, clip));
    }

    /**
     * Returns an unmodifiable view of the emitted commands in submission order.
     *
     * @return the commands captured since the last reset
     */
    public List<DrawCommand> commands() {
        return Collections.unmodifiableList(commands);
    }
}
