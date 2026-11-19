package com.lixui.widget.testsupport;

import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;

/**
 * A lightweight texture handle with explicit dimensions and sampling options.
 *
 * @param width texture width in pixels
 * @param height texture height in pixels
 * @param options sampling settings
 */
public record TestTexture(int width, int height, TextureOptions options) implements Texture {
    /**
     * Creates a texture handle with default sampling options.
     *
     * @param width texture width in pixels
     * @param height texture height in pixels
     */
    public TestTexture(int width, int height) {
        this(width, height, TextureOptions.DEFAULT);
    }

    /**
     * Creates a validated texture handle.
     *
     * @param width texture width in pixels
     * @param height texture height in pixels
     * @param options sampling settings
     * @throws IllegalArgumentException if a dimension is not positive or options is null
     */
    public TestTexture {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Texture dimensions must be positive.");
        }
        if (options == null) {
            throw new IllegalArgumentException("Texture options must not be null.");
        }
    }
}
