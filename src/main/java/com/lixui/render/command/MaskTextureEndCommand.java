package com.lixui.render.command;

import com.lixui.geometry.Rect;
import com.lixui.render.Texture;
import com.lixui.geometry.Transform;

import java.util.Objects;

/**
 * Ends a texture mask scope started by {@link MaskTextureBeginCommand}.
 * The end command repeats the mask geometry, so it carries the same transform as the begin command.
 *
 * @param texture mask texture
 * @param bounds destination rectangle before {@code transform} is applied
 * @param uv sampled texture region
 * @param clip clipping rectangle captured when the mask was pushed
 * @param transform affine transform applied to the destination rectangle
 */
public record MaskTextureEndCommand(Texture texture, Rect bounds, Rect uv, Rect clip,
                                    Transform transform) implements DrawCommand {
    /**
     * Creates an untransformed texture mask end command.
     *
     * @param texture mask texture
     * @param bounds destination rectangle in logical UI units
     * @param uv sampled texture region
     * @param clip clipping rectangle captured when the mask was pushed
     * @throws NullPointerException if any argument is null
     */
    public MaskTextureEndCommand(Texture texture, Rect bounds, Rect uv, Rect clip) {
        this(texture, bounds, uv, clip, Transform.IDENTITY);
    }

    /**
     * Creates a texture mask end command.
     *
     * @param texture mask texture
     * @param bounds destination rectangle before {@code transform} is applied
     * @param uv sampled texture region
     * @param clip clipping rectangle captured when the mask was pushed
     * @param transform affine transform applied to the destination rectangle
     * @throws NullPointerException if any argument is null
     */
    public MaskTextureEndCommand {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(transform, "transform");
    }
}
