package com.lixui.render;

import com.lixui.geometry.Rect;

import java.util.Objects;

/**
 * Ends a texture mask scope started by {@link MaskTextureBeginCommand}.
 *
 * @param texture mask texture
 * @param bounds destination rectangle in logical UI units
 * @param uv sampled texture region
 * @param clip clipping rectangle captured when the mask was pushed
 */
public record MaskTextureEndCommand(Texture texture, Rect bounds, Rect uv, Rect clip)
        implements DrawCommand {
    /**
     * Creates a texture mask end command.
     *
     * @param texture mask texture
     * @param bounds destination rectangle in logical UI units
     * @param uv sampled texture region
     * @param clip clipping rectangle captured when the mask was pushed
     * @throws NullPointerException if any argument is null
     */
    public MaskTextureEndCommand {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(uv, "uv");
        Objects.requireNonNull(clip, "clip");
    }
}
