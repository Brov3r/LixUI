package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;

import java.util.Objects;

/**
 * A textured rounded rectangle primitive.
 * Texture coordinates use a top-left origin and normalized {@code [0, 1]} values by convention.
 * The transform maps the destination rectangle and keeps corner radii and texture mapping in local
 * units, so translated, rotated, scaled and sheared images sample the same texture region.
 *
 * @param bounds the rectangle to fill before {@code transform} is applied
 * @param radius corner radius in logical UI units before {@code transform} is applied
 * @param uv the sampled texture region
 * @param texture the texture to sample
 * @param color straight-alpha tint multiplied with the sampled texel
 * @param clip clipping rectangle that applies to this command
 * @param transform affine transform applied to the destination rectangle
 */
public record ImageCommand(Rect bounds, float radius, Rect uv, Texture texture, Color color, Rect clip,
                           Transform transform) implements DrawCommand {
    /**
     * Creates an untransformed image command, clamping the radius to the bounds.
     *
     * @param bounds the rectangle to fill
     * @param radius corner radius in logical UI units
     * @param uv the sampled texture region
     * @param texture the texture to sample
     * @param color straight-alpha tint
     * @param clip clipping rectangle that applies to this command
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if radius is non-finite or negative
     */
    public ImageCommand(Rect bounds, float radius, Rect uv, Texture texture, Color color, Rect clip) {
        this(bounds, radius, uv, texture, color, clip, Transform.IDENTITY);
    }

    /**
     * Creates an image command, clamping the radius to the bounds.
     *
     * @param bounds the rectangle to fill before {@code transform} is applied
     * @param radius corner radius in logical UI units before {@code transform} is applied
     * @param uv the sampled texture region
     * @param texture the texture to sample
     * @param color straight-alpha tint
     * @param clip clipping rectangle that applies to this command
     * @param transform affine transform applied to the destination rectangle
     * @throws NullPointerException if any reference argument is null
     * @throws IllegalArgumentException if radius is non-finite or negative
     */
    public ImageCommand {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(transform, "transform");
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");
        }
        radius = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2);
    }
}
