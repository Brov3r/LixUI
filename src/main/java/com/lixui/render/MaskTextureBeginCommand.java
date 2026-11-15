package com.lixui.render;

import com.lixui.geometry.Rect;

import java.util.Objects;

/**
 * Starts a stencil mask scope whose coverage comes from a texture's alpha channel.
 * Fragments with zero alpha are discarded; non-zero alpha marks the pixel as inside the mask.
 *
 * @param texture mask texture
 * @param bounds destination rectangle in logical UI units
 * @param uv sampled texture region
 * @param clip clipping rectangle captured when the mask was pushed
 */
public record MaskTextureBeginCommand(Texture texture, Rect bounds, Rect uv, Rect clip)
        implements DrawCommand {
    /**
     * Creates a texture mask begin command.
     *
     * @param texture mask texture
     * @param bounds destination rectangle in logical UI units
     * @param uv sampled texture region
     * @param clip clipping rectangle captured when the mask was pushed
     * @throws NullPointerException if any argument is null
     */
    public MaskTextureBeginCommand {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(clip, "clip");
    }
}
