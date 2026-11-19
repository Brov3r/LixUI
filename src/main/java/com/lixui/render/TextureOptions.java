package com.lixui.render;

import java.util.Objects;

/**
 * Sampling settings applied to a texture when it is created.
 * Filtering controls how texels are blended, wrapping controls coordinates outside the normalized
 * range, and mipmaps enable a prefiltered chain of reduced copies for minification.
 *
 * @param filter texel filtering applied during sampling
 * @param wrapU horizontal addressing mode outside the normalized range
 * @param wrapV vertical addressing mode outside the normalized range
 * @param mipmaps whether a mipmap chain is generated and sampled when the texture is minified
 */
public record TextureOptions(TextureFilter filter, TextureWrap wrapU, TextureWrap wrapV, boolean mipmaps) {
    /**
     * Smooth filtering, clamped edges and no mipmaps; the default for ordinary UI content.
     */
    public static final TextureOptions DEFAULT =
            new TextureOptions(TextureFilter.LINEAR, TextureWrap.CLAMP, TextureWrap.CLAMP, false);

    /**
     * Smooth filtering with repeat addressing on both axes; suitable for tiled backgrounds.
     */
    public static final TextureOptions REPEAT =
            new TextureOptions(TextureFilter.LINEAR, TextureWrap.REPEAT, TextureWrap.REPEAT, false);

    /**
     * Nearest filtering with clamped edges; suitable for pixel art.
     */
    public static final TextureOptions PIXEL_ART =
            new TextureOptions(TextureFilter.NEAREST, TextureWrap.CLAMP, TextureWrap.CLAMP, false);

    /**
     * Smooth filtering with a full mipmap chain for heavily minified images.
     */
    public static final TextureOptions MIPMAPPED =
            new TextureOptions(TextureFilter.LINEAR, TextureWrap.CLAMP, TextureWrap.CLAMP, true);

    /**
     * Creates texture options from validated enum settings.
     *
     * @param filter texel filtering applied during sampling
     * @param wrapU horizontal addressing mode outside the normalized range
     * @param wrapV vertical addressing mode outside the normalized range
     * @param mipmaps whether a mipmap chain is generated and sampled when the texture is minified
     * @throws NullPointerException if a filter or wrap mode is null
     */
    public TextureOptions {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(wrapU, "wrapU");
        Objects.requireNonNull(wrapV, "wrapV");
    }
}
