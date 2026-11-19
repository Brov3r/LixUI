package com.lixui.backend;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.render.Viewport;

/**
 * Computes the six vertices of a textured, optionally rounded quad for both render backends.
 */
public final class QuadGeometry {
    /**
     * Receives one vertex in the interleaved backend vertex layout.
     */
    @FunctionalInterface
    public interface VertexSink {
        /**
         * Writes one vertex.
         *
         * @param x transformed X position
         * @param y transformed Y position
         * @param localX local X relative to the quad center
         * @param localY local Y relative to the quad center
         * @param halfWidth half of the destination width
         * @param halfHeight half of the destination height
         * @param radius corner radius in local units
         * @param red red component
         * @param green green component
         * @param blue blue component
         * @param alpha opacity
         * @param u horizontal texture coordinate
         * @param v vertical texture coordinate
         */
        void vertex(float x, float y, float localX, float localY, float halfWidth,
                    float halfHeight, float radius, float red, float green, float blue,
                    float alpha, float u, float v);
    }

    /**
     * Prevents instantiation of the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private QuadGeometry() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated.");
    }

    /**
     * Emits six triangle vertices for one quad.
     *
     * @param bounds destination rectangle
     * @param radius corner radius in local units
     * @param color vertex color
     * @param uv sampled texture region
     * @param transform affine transform applied to the quad corners
     * @param feather true to reserve one physical pixel outside the contour
     * @param viewport target viewport used for feathering
     * @param sink vertex writer
     */
    public static void append(Rect bounds, float radius, Color color, Rect uv,
                              Transform transform, boolean feather, Viewport viewport,
                              VertexSink sink) {
        float halfWidth = bounds.width() / 2;
        float halfHeight = bounds.height() / 2;
        float padX = 0;
        float padY = 0;
        float determinant = Math.abs(transform.m00() * transform.m11()
                - transform.m01() * transform.m10());
        if (feather && determinant > 0) {
            // Reserve a physical pixel outside the contour so the shader can cover both sides of an edge.
            padX = (Math.abs(transform.m11()) / viewport.scaleX()
                    + Math.abs(transform.m01()) / viewport.scaleY()) / determinant;
            padY = (Math.abs(transform.m10()) / viewport.scaleX()
                    + Math.abs(transform.m00()) / viewport.scaleY()) / determinant;
        }
        float localWidth = halfWidth + padX;
        float localHeight = halfHeight + padY;
        float x0 = bounds.x() - padX;
        float y0 = bounds.y() - padY;
        float x1 = bounds.right() + padX;
        float y1 = y0;
        float x2 = x1;
        float y2 = bounds.bottom() + padY;
        float x3 = x0;
        float y3 = y2;
        if (!transform.isIdentity()) {
            Point topLeft = transform.apply(new Point(x0, y0));
            Point topRight = transform.apply(new Point(x1, y1));
            Point bottomRight = transform.apply(new Point(x2, y2));
            Point bottomLeft = transform.apply(new Point(x3, y3));
            x0 = topLeft.x();
            y0 = topLeft.y();
            x1 = topRight.x();
            y1 = topRight.y();
            x2 = bottomRight.x();
            y2 = bottomRight.y();
            x3 = bottomLeft.x();
            y3 = bottomLeft.y();
        }

        float padU = bounds.width() > 0 ? padX * uv.width() / bounds.width() : 0;
        float padV = bounds.height() > 0 ? padY * uv.height() / bounds.height() : 0;
        float u0 = uv.x() - padU;
        float v0 = uv.y() - padV;
        float u1 = uv.right() + padU;
        float v1 = uv.bottom() + padV;
        float red = color.r();
        float green = color.g();
        float blue = color.b();
        float alpha = color.a();

        sink.vertex(x0, y0, -localWidth, -localHeight, halfWidth, halfHeight, radius,
                red, green, blue, alpha, u0, v0);
        sink.vertex(x1, y1, localWidth, -localHeight, halfWidth, halfHeight, radius,
                red, green, blue, alpha, u1, v0);
        sink.vertex(x2, y2, localWidth, localHeight, halfWidth, halfHeight, radius,
                red, green, blue, alpha, u1, v1);
        sink.vertex(x0, y0, -localWidth, -localHeight, halfWidth, halfHeight, radius,
                red, green, blue, alpha, u0, v0);
        sink.vertex(x2, y2, localWidth, localHeight, halfWidth, halfHeight, radius,
                red, green, blue, alpha, u1, v1);
        sink.vertex(x3, y3, -localWidth, localHeight, halfWidth, halfHeight, radius,
                red, green, blue, alpha, u0, v1);
    }
}
