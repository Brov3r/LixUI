package com.lixui.demo;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;

/**
 * Maps the design canvas onto a viewport with a uniform fit and centered offsets.
 */
final class DesignLayout {
    static final float DESIGN_WIDTH = 1280;
    static final float DESIGN_HEIGHT = 720;

    private DesignLayout() {
    }

    /**
     * A uniform scale and offset that fit the design canvas into a viewport.
     *
     * @param scale uniform scale factor
     * @param offsetX horizontal offset in viewport units
     * @param offsetY vertical offset in viewport units
     */
    record Layout(float scale, float offsetX, float offsetY) {
        /**
         * Fits the design canvas into the supplied viewport.
         *
         * @param viewport target viewport
         * @return the fitted layout
         */
        static Layout fit(Rect viewport) {
            float scale = Math.min(viewport.width() / DESIGN_WIDTH,
                    viewport.height() / DESIGN_HEIGHT);
            return new Layout(scale,
                    viewport.x() + (viewport.width() - DESIGN_WIDTH * scale) / 2,
                    viewport.y() + (viewport.height() - DESIGN_HEIGHT * scale) / 2);
        }

        /**
         * Maps a design-space point into the viewport.
         *
         * @param x design-space X
         * @param y design-space Y
         * @return the mapped point
         */
        Point point(float x, float y) {
            return new Point(offsetX + x * scale, offsetY + y * scale);
        }

        /**
         * Maps a design-space rectangle into the viewport.
         *
         * @param x design-space X
         * @param y design-space Y
         * @param width design-space width
         * @param height design-space height
         * @return the mapped rectangle
         */
        Rect rect(float x, float y, float width, float height) {
            return new Rect(offsetX + x * scale, offsetY + y * scale, width * scale, height * scale);
        }

        /**
         * Scales a design-space length.
         *
         * @param value design-space length
         * @return the scaled length
         */
        float scale(float value) {
            return value * scale;
        }
    }
}
