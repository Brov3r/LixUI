package com.lixui.widget.testsupport;

import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;

/**
 * A render backend that accepts frames without allocating resources.
 */
public final class TestBackend implements RenderBackend {
    /**
     * Accepts the viewport without using it.
     *
     * @param viewport requested viewport
     */
    @Override
    public void resize(Viewport viewport) {
    }

    /**
     * Accepts a submitted frame without drawing it.
     *
     * @param drawList submitted commands
     */
    @Override
    public void draw(DrawList drawList) {
    }

    /**
     * Reports that test widgets must not create textures.
     *
     * @param image source pixels
     * @return never
     * @throws UnsupportedOperationException always
     */
    @Override
    public Texture createTexture(ImageData image) {
        throw new UnsupportedOperationException();
    }

    /**
     * Reports that test widgets must not own textures.
     *
     * @param texture released texture
     * @throws UnsupportedOperationException always
     */
    @Override
    public void destroyTexture(Texture texture) {
        throw new UnsupportedOperationException();
    }

    /**
     * Closes this resource-free backend.
     */
    @Override
    public void close() {
    }
}
