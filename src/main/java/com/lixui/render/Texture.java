package com.lixui.render;

/**
 * A backend-independent texture used by image draw commands.
 * Instances are created by a render backend, either from {@link ImageData} or from an existing
 * texture owned by the host application.
 */
public interface Texture {
    /**
     * Returns the texture width in pixels.
     *
     * @return the positive texture width
     */
    int width();

    /**
     * Returns the texture height in pixels.
     *
     * @return the positive texture height
     */
    int height();

    /**
     * Returns the sampling settings of this texture.
     * Wrapped host textures that do not expose sampling settings report {@link TextureOptions#DEFAULT}.
     *
     * @return the texture sampling options
     */
    default TextureOptions options() {
        return TextureOptions.DEFAULT;
    }
}
