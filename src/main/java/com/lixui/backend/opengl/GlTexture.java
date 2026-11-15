package com.lixui.backend.opengl;

import com.lixui.render.Texture;

/**
 * An OpenGL texture handle with an ownership flag.
 */
final class GlTexture implements Texture {
    private final int id;
    private final int width;
    private final int height;
    private final boolean owned;

    GlTexture(int id, int width, int height, boolean owned) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.owned = owned;
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
}
