package com.lixui.render;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;

import java.util.Objects;

/**
 * Starts a stencil mask scope whose coverage comes from a texture's alpha channel.
 * Fragments with zero alpha are discarded; non-zero alpha marks the pixel as inside the mask.
 * The transform maps the destination rectangle, so the mask can be translated, rotated, scaled
 * or sheared.
 *
 * @param texture mask texture
 * @param bounds destination rectangle before {@code transform} is applied
 * @param uv sampled texture region
 * @param clip clipping rectangle captured when the mask was pushed
 * @param transform affine transform applied to the destination rectangle
 */
public record MaskTextureBeginCommand(Texture texture, Rect bounds, Rect uv, Rect clip,
                                      Transform transform) implements DrawCommand {
    /**
     * Creates an untransformed texture mask begin command.
     *
     * @param texture mask texture
     * @param bounds destination rectangle in logical UI units
     * @param uv sampled texture region
     * @param clip clipping rectangle captured when the mask was pushed
     * @throws NullPointerException if any argument is null
     */
    public MaskTextureBeginCommand(Texture texture, Rect bounds, Rect uv, Rect clip) {
        this(texture, bounds, uv, clip, Transform.IDENTITY);
    }

    /**
     * Creates a texture mask begin command.
     *
     * @param texture mask texture
     * @param bounds destination rectangle before {@code transform} is applied
     * @param uv sampled texture region
     * @param clip clipping rectangle captured when the mask was pushed
     * @param transform affine transform applied to the destination rectangle
     * @throws NullPointerException if any argument is null
     */
    public MaskTextureBeginCommand {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(transform, "transform");
    }
}
