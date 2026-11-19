package com.lixui.render;

/**
 * Texel filtering used when a texture is sampled at a scale other than one-to-one.
 */
public enum TextureFilter {
    /**
     * Selects the nearest texel, preserving hard pixel edges.
     */
    NEAREST,

    /**
     * Blends adjacent texels for smooth scaling.
     */
    LINEAR
}
