package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.render.DrawList;

/**
 * Builds the sample draw list shared by the OpenGL and Vulkan demos.
 */
final class DemoScene {
    private static final float DESIGN_WIDTH = 1280;
    private static final float DESIGN_HEIGHT = 720;

    private DemoScene() {
    }

    /**
     * Creates a draw list with a few overlapping cards and a clipped row.
     * The layout is authored at 1280x720 and scaled uniformly to fit the viewport.
     *
     * @param width viewport width in pixels
     * @param height viewport height in pixels
     * @return the populated draw list
     */
    static DrawList create(int width, int height) {
        DrawList list = new DrawList(new Rect(0, 0, width, height));
        Layout layout = Layout.fit(width, height);

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
        return list;
    }

    private record Layout(float scale, float offsetX, float offsetY) {
        static Layout fit(int width, int height) {
            float scale = Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT);
            return new Layout(
                    scale,
                    (width - DESIGN_WIDTH * scale) / 2,
                    (height - DESIGN_HEIGHT * scale) / 2);
        }

        Rect rect(float x, float y, float width, float height) {
            return new Rect(offsetX + x * scale, offsetY + y * scale, width * scale, height * scale);
        }

        float scale(float value) {
            return value * scale;
        }
    }
}
