package com.lixui.backend.opengl;

import com.lixui.render.Texture;
import com.lixui.render.TextureOptions;

/**
 * An OpenGL texture handle that records its owning backend and whether the name is owned.
 */
final class GlTexture implements Texture {
    private final OpenGlBackend owner;
    private final int id;
    private final int width;
    private final int height;
    private final boolean owned;
    private final TextureOptions options;

    GlTexture(OpenGlBackend owner, int id, int width, int height, boolean owned, TextureOptions options) {
        this.owner = owner;
        this.id = id;
        this.width = width;
        this.height = height;
        this.owned = owned;
        this.options = options;
    }

    OpenGlBackend owner() {
        return owner;
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
