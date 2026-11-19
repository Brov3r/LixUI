package com.lixui.render;

/**
 * Texel filtering used when a texture is sampled at a scale other than one-to-one.
 */
public enum TextureFilter {
    /**
     * Selects the nearest texel of the nearest mip level, preserving hard pixel edges.
     */
    NEAREST,

    /**
     * Blends adjacent texels and, when mipmaps are enabled, adjacent mip levels.
     */
    LINEAR
}
