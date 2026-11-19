package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.render.DrawList;
import com.lixui.render.Texture;

/**
 * Builds the sample draw list shared by the OpenGL and Vulkan demos.
 */
final class DemoScene {
    private DemoScene() {
    }

    /**
     * Creates a draw list with a few overlapping cards, a clipped row and textured quads.
     * The layout is authored at 1280x720 and scaled uniformly to fit the viewport.
     *
     * @param width viewport width in pixels
     * @param height viewport height in pixels
     * @param texture texture sampled by the first image commands
     * @param wrappedTexture texture sampled by the tinted image command, typically a host-owned texture
     * @return the populated draw list
     */
    static DrawList create(int width, int height, Texture texture, Texture wrappedTexture) {
        DrawList list = new DrawList(new Rect(0, 0, width, height));
        DesignLayout.Layout layout = DesignLayout.Layout.fit(new Rect(0, 0, width, height));

        list.roundedRect(layout.rect(80, 80, 420, 260), layout.scale(20), Color.hex("#23262E"));
        list.roundedRect(layout.rect(104, 104, 8, 212), layout.scale(4), Color.hex("#5B8CFF"));
        list.roundedRect(layout.rect(132, 104, 344, 56), layout.scale(12), Color.hex("#2C303A"));
        list.roundedRect(layout.rect(132, 176, 164, 56), layout.scale(12), Color.hex("#5B8CFF"));
        list.roundedRect(layout.rect(312, 176, 164, 56), layout.scale(12), Color.hex("#3A3F4B"));

        list.pushClip(layout.rect(540, 80, 660, 260));
        list.roundedRect(layout.rect(540, 80, 660, 260), layout.scale(20), Color.hex("#23262E"));
        for (int index = 0; index < 8; index++) {
            list.roundedRect(
                    layout.rect(500 + index * 84, 108, 132, 180),
                    layout.scale(24),
                    Color.rgba(91, 140, 255, 40 + index * 20));
        }
        list.popClip();

        list.roundedRect(layout.rect(80, 380, 1120, 180), layout.scale(20), Color.hex("#23262E"));
        list.roundedRect(layout.rect(104, 404, 1072, 24), layout.scale(12), Color.hex("#2C303A"));
        list.roundedRect(layout.rect(104, 404, 640, 24), layout.scale(12), Color.hex("#5B8CFF"));

        Rect fullUv = new Rect(0, 0, 1, 1);
        list.image(texture, layout.rect(104, 444, 160, 96), layout.scale(16), fullUv, Color.WHITE);
        list.pushClip(layout.rect(284, 444, 160, 96));
        list.image(texture, layout.rect(284, 444, 320, 192), layout.scale(16),
                new Rect(0, 0, 0.5f, 0.5f), Color.WHITE);
        list.popClip();
        list.image(texture, layout.rect(464, 444, 96, 96), layout.scale(16), fullUv,
                Color.rgba(1, 0.5f, 0.5f, 0.8f));
        list.image(wrappedTexture, layout.rect(580, 444, 96, 96), layout.scale(16), fullUv,
                Color.WHITE);
        Rect tilted = layout.rect(700, 444, 96, 96);
        float centerX = tilted.x() + tilted.width() / 2;
        float centerY = tilted.y() + tilted.height() / 2;
        Transform tilt = Transform.translation(centerX, centerY)
                .multiply(Transform.rotation(0.18f))
                .multiply(Transform.translation(-centerX, -centerY));
        list.image(texture, tilted, layout.scale(16), new Rect(0.5f, 0.5f, 0.5f, 0.5f),
                Color.WHITE, tilt);
        return list;
    }

}
