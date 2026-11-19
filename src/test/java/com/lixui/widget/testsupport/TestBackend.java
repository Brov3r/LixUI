package com.lixui.widget.testsupport;

import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;

/**
 * A render backend that records the last frame and hands out lightweight texture handles.
 */
public final class TestBackend implements RenderBackend {
    private DrawList lastDrawList;

    /**
     * Returns the last submitted draw list.
     *
     * @return the last draw list, or null before the first frame
     */
    public DrawList lastDrawList() {
        return lastDrawList;
    }

    /**
     * Accepts the viewport without using it.
     *
     * @param viewport requested viewport
     */
    @Override
    public void resize(Viewport viewport) {
    }

    /**
     * Records a submitted frame.
     *
     * @param drawList submitted commands
     */
    @Override
    public void draw(DrawList drawList) {
        lastDrawList = drawList;
    }

    /**
     * Creates a texture handle with the source dimensions.
     *
     * @param image source pixels
     * @return a lightweight texture handle
     */
    @Override
    public Texture createTexture(ImageData image) {
        return new TextureHandle(image.width(), image.height());
    }

    /**
     * Ignores texture disposal.
     *
     * @param texture released texture
     */
    @Override
    public void destroyTexture(Texture texture) {
    }

    /**
     * Closes this resource-free backend.
     */
    @Override
    public void close() {
    }

    private record TextureHandle(int width, int height) implements Texture {
    }
}
