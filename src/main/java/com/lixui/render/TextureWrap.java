package com.lixui.render;

/**
 * Addressing behavior for texture coordinates outside the normalized {@code [0, 1]} range.
 */
public enum TextureWrap {
    /**
     * Clamps coordinates to the edge texel.
     */
    CLAMP,

    /**
     * Repeats the texture periodically.
     */
    REPEAT,

    /**
     * Repeats the texture with alternating orientation.
     */
    MIRROR
}
