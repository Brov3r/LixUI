package com.lixui.widget;

import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;

final class TestBackend implements RenderBackend {
    @Override
    public void resize(Viewport viewport) {
    }

    @Override
    public void draw(DrawList drawList) {
    }

    @Override
    public Texture createTexture(ImageData image) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void destroyTexture(Texture texture) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
    }
}
