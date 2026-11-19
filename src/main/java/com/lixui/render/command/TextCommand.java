package com.lixui.render.command;

import com.lixui.geometry.Rect;
import com.lixui.render.Texture;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.text.TextGlyph;

import java.util.List;
import java.util.Objects;

/**
 * A batch of textured glyph quads sampled from a single font atlas.
 * Every glyph keeps its own destination rectangle and tint, so one command carries a whole text
 * block, including gradient-colored text. The transform is applied to each quad by the backend.
 */
public final class TextCommand implements DrawCommand {
    private final Texture texture;
    private final List<TextGlyph> glyphs;
    private final List<Color> colors;
    private final Rect bounds;
    private final Rect clip;
    private final Transform transform;

    /**
     * Creates a text batch and computes its local bounds.
     *
     * @param texture font atlas texture
     * @param glyphs positioned glyph quads
     * @param colors straight-alpha tint per glyph, in the same order as {@code glyphs}
     * @param clip clipping rectangle captured when the text was submitted
     * @param transform affine transform applied to every glyph quad
     * @throws NullPointerException if an argument or list element is null
     * @throws IllegalArgumentException if the lists are empty or their sizes differ
     */
    public TextCommand(Texture texture, List<TextGlyph> glyphs, List<Color> colors,
                       Rect clip, Transform transform) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.glyphs = List.copyOf(Objects.requireNonNull(glyphs, "glyphs"));
        this.colors = List.copyOf(Objects.requireNonNull(colors, "colors"));
        this.clip = Objects.requireNonNull(clip, "clip");
        this.transform = Objects.requireNonNull(transform, "transform");
        if (this.glyphs.isEmpty()) {
            throw new IllegalArgumentException("A text command requires at least one glyph.");
        }
        if (this.glyphs.size() != this.colors.size()) {
            throw new IllegalArgumentException("Every glyph requires exactly one tint.");
        }
        this.bounds = boundsOf(this.glyphs);
    }

    /**
     * Returns the font atlas texture.
     *
     * @return the atlas texture
     */
    public Texture texture() {
        return texture;
    }

    /**
     * Returns the positioned glyph quads in drawing order.
     *
     * @return an unmodifiable glyph list
     */
    public List<TextGlyph> glyphs() {
        return glyphs;
    }

    /**
     * Returns the straight-alpha tint of every glyph.
     *
     * @return an unmodifiable color list aligned with {@link #glyphs()}
     */
    public List<Color> colors() {
        return colors;
    }

    /**
     * Returns the affine transform applied to every glyph quad.
     *
     * @return the text transform
     */
    public Transform transform() {
        return transform;
    }

    /**
     * Returns the local union of all glyph bounds, before the transform is applied.
     *
     * @return the local text bounds
     */
    @Override
    public Rect bounds() {
        return bounds;
    }

    /**
     * Returns the clipping rectangle captured when the text was submitted.
     *
     * @return the active clipping rectangle
     */
    @Override
    public Rect clip() {
        return clip;
    }

    private static Rect boundsOf(List<TextGlyph> glyphs) {
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (TextGlyph glyph : glyphs) {
            Rect bounds = glyph.bounds();
            left = Math.min(left, bounds.x());
            top = Math.min(top, bounds.y());
            right = Math.max(right, bounds.right());
            bottom = Math.max(bottom, bounds.bottom());
        }
        return new Rect(left, top, right - left, bottom - top);
    }
}
