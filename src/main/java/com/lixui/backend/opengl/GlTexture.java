package com.lixui.backend.opengl;

import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;

/**
 * An OpenGL texture handle with an ownership flag.
 */
final class GlTexture implements Texture {
    private final int id;
    private final int width;
    private final int height;
    private final boolean owned;
    private final TextureOptions options;

    GlTexture(int id, int width, int height, boolean owned, TextureOptions options) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.owned = owned;
        this.options = options;
    }

    int id() {
        return id;
    }

    boolean owned() {
        return owned;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public TextureOptions options() {
        return options;
    }
}
