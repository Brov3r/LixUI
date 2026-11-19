package com.lixui.backend;

import com.lixui.geometry.Rect;
import com.lixui.render.ImageData;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.TextureFilter;
import com.lixui.render.TextureOptions;
import com.lixui.render.TextureWrap;

/**
 * Draws a stripe pattern whose base level and reduced levels have distinct intensities.
 */
public final class MipmapScenario {
    private MipmapScenario() {
    }

    /**
     * Uploads stripes that reduce to alternating 64/191 texels and then solid gray.
     *
     * @param renderer renderer that owns the test texture
     * @param filter sampling mode between texels and mip levels
     * @return the uploaded 64 by 3 texture
     */
    public static Texture stripes(RenderManager renderer, TextureFilter filter) {
        int[] stripe = {0, 128, 255, 127};
        byte[] pixels = new byte[64 * 3 * 4];
        for (int i = 0; i < pixels.length; i += 4) {
            byte intensity = (byte) stripe[(i / 4) % stripe.length];
            pixels[i] = intensity;
            pixels[i + 1] = intensity;
            pixels[i + 2] = intensity;
            pixels[i + 3] = -1;
        }
        return renderer.createTexture(new ImageData(64, 3, pixels),
                new TextureOptions(filter, TextureWrap.CLAMP, TextureWrap.CLAMP, true));
    }

    /**
     * Samples at a fractional LOD between levels one and two, then at level four.
     *
     * @param renderer renderer recording the frame
     * @param nearest mipmapped nearest-filtered stripes
     * @param linear mipmapped linearly filtered stripes
     */
    public static void draw(RenderManager renderer, Texture nearest, Texture linear) {
        renderer.image(nearest, new Rect(0, 0, 25, 8));
        renderer.image(linear, new Rect(0, 10, 4, 8));
    }
}
