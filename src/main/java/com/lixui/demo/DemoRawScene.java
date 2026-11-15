package com.lixui.demo;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.graphics.GradientStop;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;

import java.util.List;

/**
 * Demonstrates the raw fill and stroke API, multi-stop gradients, transforms and stencil masks.
 */
final class DemoRawScene {
    private static final float DESIGN_WIDTH = 1280;
    private static final float DESIGN_HEIGHT = 720;

    private DemoRawScene() {
    }

    /**
     * Draws the raw-shape demo scene into the supplied renderer.
     *
     * @param renderer drawing manager
     * @param texture checkerboard texture
     * @param wrappedTexture host-owned checkerboard texture
     * @param maskTexture circular alpha mask texture
     * @param fonts system font styles used for labels, or null when no system font is available
     * @param time elapsed time in seconds, used for rotation
     */
    static void draw(RenderManager renderer, Texture texture, Texture wrappedTexture,
                     Texture maskTexture, DemoFonts fonts, float time) {
        Layout layout = Layout.fit(renderer.viewport());
        renderer.fillRect(renderer.viewport(), Paint.solid(Color.hex("#12141A")));

        drawTopRow(renderer, layout);
        drawMaskRow(renderer, layout, texture, wrappedTexture);
        drawTransformRow(renderer, layout, maskTexture, time);
        drawText(renderer, layout, fonts);
    }

    private static void drawTopRow(RenderManager renderer, Layout layout) {
        renderer.fillRoundedRect(layout.rect(40, 40, 280, 160), layout.scale(28),
                Paint.linear(layout.point(40, 40), layout.point(320, 200),
                        new GradientStop(0, Color.hex("#5B8CFF")),
                        new GradientStop(0.45f, Color.hex("#A66CFF")),
                        new GradientStop(1, Color.hex("#FF6B8B"))));

        renderer.fillCircle(layout.point(430, 120), layout.scale(70),
                Paint.radial(layout.point(390, 90), layout.scale(90),
                        new GradientStop(0, Color.hex("#48C78E")),
                        new GradientStop(0.55f, Color.hex("#2C8CFF")),
                        new GradientStop(1, Color.hex("#12141A"))));

        renderer.strokeEllipse(layout.point(640, 120), layout.scale(80), layout.scale(58),
                layout.scale(8), Paint.linear(layout.point(560, 120), layout.point(720, 120),
                        new GradientStop(0, Color.hex("#FFD166")),
                        new GradientStop(1, Color.hex("#FF6B8B"))));

        renderer.fillArc(layout.point(840, 120), layout.scale(80), layout.scale(58),
                (float) (-Math.PI / 2), (float) (Math.PI * 1.5f),
                Paint.radial(layout.point(840, 120), layout.scale(90),
                        new GradientStop(0, Color.hex("#48C78E")),
                        new GradientStop(1, Color.hex("#5B8CFF"))));

        renderer.strokeArc(layout.point(1040, 120), layout.scale(80), layout.scale(58),
                0, (float) (Math.PI * 1.5f), layout.scale(8),
                Paint.linear(layout.point(960, 120), layout.point(1120, 120),
                        new GradientStop(0, Color.hex("#5B8CFF")),
                        new GradientStop(0.5f, Color.hex("#FFD166")),
                        new GradientStop(1, Color.hex("#FF6B8B"))));

        List<Point> polygon = List.of(
                layout.point(1180, 50),
                layout.point(1240, 90),
                layout.point(1220, 170),
                layout.point(1160, 190),
                layout.point(1130, 110));
        renderer.fillPolygon(polygon, Paint.linear(layout.point(1130, 50), layout.point(1240, 190),
                new GradientStop(0, Color.hex("#48C78E")),
                new GradientStop(1, Color.hex("#5B8CFF"))));
        renderer.strokePolygon(polygon, layout.scale(4), Paint.solid(Color.WHITE.withAlpha(0.55f)));
    }

    private static void drawMaskRow(RenderManager renderer, Layout layout,
                                    Texture texture, Texture wrappedTexture) {
        Rect firstCard = layout.rect(40, 240, 360, 220);
        Rect firstView = layout.rect(58, 258, 324, 184);
        renderer.fillRoundedRect(firstCard, layout.scale(20), Paint.solid(Color.hex("#1D2028")));
        renderer.pushMaskEllipse(layout.point(220, 350), layout.scale(150), layout.scale(85));
        renderer.image(texture, firstView);
        renderer.fillRect(firstView, Paint.linear(layout.point(58, 258), layout.point(382, 442),
                new GradientStop(0, Color.rgba(91, 140, 255, 40)),
                new GradientStop(1, Color.rgba(255, 107, 139, 100))));
        renderer.popMask();

        Rect secondCard = layout.rect(420, 240, 360, 220);
        Rect secondView = layout.rect(438, 258, 324, 184);
        renderer.fillRoundedRect(secondCard, layout.scale(20), Paint.solid(Color.hex("#1D2028")));
        renderer.pushMaskRoundedRect(secondView, layout.scale(28));
        for (int index = 0; index < 6; index++) {
            float height = 36 + index * 20;
            renderer.fillRoundedRect(layout.rect(456 + index * 52, 426 - height, 34, height),
                    layout.scale(7), Paint.linear(layout.point(456, 426), layout.point(780, 300),
                            new GradientStop(0, Color.hex("#48C78E")),
                            new GradientStop(1, Color.hex("#5B8CFF"))));
        }
        renderer.popMask();

        Rect thirdCard = layout.rect(800, 240, 440, 220);
        Rect thirdView = layout.rect(818, 258, 404, 184);
        renderer.fillRoundedRect(thirdCard, layout.scale(20), Paint.solid(Color.hex("#1D2028")));
        renderer.pushMaskEllipse(layout.point(1020, 350), layout.scale(190), layout.scale(92));
        renderer.pushMaskRoundedRect(thirdView, layout.scale(36));
        renderer.pushMaskPolygon(List.of(
                layout.point(880, 290),
                layout.point(1160, 290),
                layout.point(1020, 430)));
        renderer.pushClip(layout.rect(850, 280, 300, 140));
        renderer.image(wrappedTexture, layout.rect(790, 230, 460, 240));
        renderer.fillRect(layout.rect(790, 230, 460, 240),
                Paint.linear(layout.point(818, 258), layout.point(1222, 442),
                        new GradientStop(0, Color.rgba(72, 199, 142, 70)),
                        new GradientStop(1, Color.rgba(91, 140, 255, 110))));
        renderer.popClip();
        renderer.popMask();
        renderer.popMask();
        renderer.popMask();
    }

    private static void drawTransformRow(RenderManager renderer, Layout layout,
                                         Texture maskTexture, float time) {
        Rect rotatingRect = layout.rect(80, 510, 220, 150);
        Point rotatingCenter = center(rotatingRect);
        renderer.withTransform(rotateAround(rotatingCenter, time * 0.7f),
                () -> renderer.fillRoundedRect(rotatingRect, layout.scale(26),
                        Paint.linear(layout.point(80, 510), layout.point(300, 660),
                                new GradientStop(0, Color.hex("#FFD166")),
                                new GradientStop(0.5f, Color.hex("#FF6B8B")),
                                new GradientStop(1, Color.hex("#A66CFF")))));

        Point polygonCenter = layout.point(430, 585);
        List<Point> hexagon = List.of(
                layout.point(370, 530),
                layout.point(490, 530),
                layout.point(520, 585),
                layout.point(490, 640),
                layout.point(370, 640),
                layout.point(340, 585));
        renderer.withTransform(rotateAround(polygonCenter, -time * 0.5f), () -> {
            renderer.fillPolygon(hexagon, Paint.linear(layout.point(340, 530),
                    layout.point(520, 640),
                    new GradientStop(0, Color.hex("#48C78E")),
                    new GradientStop(1, Color.hex("#2C8CFF"))));
            renderer.strokePolygon(hexagon, layout.scale(4), Paint.solid(Color.WHITE.withAlpha(0.7f)));
        });

        renderer.strokePolyline(List.of(
                        layout.point(580, 640),
                        layout.point(620, 560),
                        layout.point(660, 630),
                        layout.point(700, 540),
                        layout.point(740, 620)),
                layout.scale(8), Paint.linear(layout.point(580, 540), layout.point(740, 640),
                        new GradientStop(0, Color.hex("#5B8CFF")),
                        new GradientStop(1, Color.hex("#48C78E"))));
        renderer.strokeLine(layout.point(780, 570), layout.point(860, 630), layout.scale(10),
                Paint.solid(Color.hex("#FFD166")));

        Rect maskCard = layout.rect(900, 510, 340, 160);
        Rect maskView = layout.rect(918, 528, 304, 124);
        renderer.fillRoundedRect(maskCard, layout.scale(20), Paint.solid(Color.hex("#1D2028")));
        renderer.pushMaskTexture(maskTexture, maskView);
        renderer.fillRect(maskView, Paint.linear(layout.point(918, 528), layout.point(1222, 652),
                new GradientStop(0, Color.hex("#48C78E")),
                new GradientStop(1, Color.hex("#FF6B8B"))));
        renderer.popMask();
        renderer.strokeRect(maskView, layout.scale(3), Paint.solid(Color.WHITE.withAlpha(0.25f)));
    }

    private static Point center(Rect rect) {
        return new Point(rect.x() + rect.width() / 2, rect.y() + rect.height() / 2);
    }

    private static void drawText(RenderManager renderer, Layout layout, DemoFonts fonts) {
        if (fonts == null) {
            return;
        }
        drawTextLine(renderer, layout, fonts.regular(), 40, 6, "LixUI · текст и шрифты", Color.WHITE);
        drawTextLine(renderer, layout, fonts.regular(), 440, 6,
                "Системный шрифт · кириллица · атлас глифов", Color.WHITE.withAlpha(0.6f));
        drawRotatedTextLine(renderer, layout, fonts.boldItalic(), 92, 118, -0.12f, "Поворот текста");
        drawTextLine(renderer, layout, fonts.regular(), 40, 205,
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 0123456789",
                Paint.linear(new Point(0, 0), new Point(900, 0),
                        new GradientStop(0, Color.hex("#5B8CFF")),
                        new GradientStop(0.5f, Color.hex("#A66CFF")),
                        new GradientStop(1, Color.hex("#FF6B8B"))));
        drawTextLine(renderer, layout, fonts.bold(), 640, 468,
                "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ абвгдеёжзийклмнопрстуфхцчшщъыьэюя",
                Color.hex("#48C78E").withAlpha(0.7f),
                HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        drawTextLine(renderer, layout, fonts.italic(), 1240, 695,
                "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~«»№—–…•€",
                Color.hex("#FFD166").withAlpha(0.7f),
                HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
    }

    private static void drawRotatedTextLine(RenderManager renderer, Layout layout, Font font,
                                            float x, float y, float angle, String text) {
        Point origin = layout.point(x, y);
        Transform transform = Transform.translation(origin.x(), origin.y())
                .multiply(Transform.rotation(angle))
                .multiply(Transform.scaling(layout.scale(), layout.scale()));
        renderer.withTransform(transform, () -> renderer.text(font, text, 0, 0, Color.WHITE));
    }

    private static void drawTextLine(RenderManager renderer, Layout layout, Font font,
                                     float x, float y, String text, Color color) {
        drawTextLine(renderer, layout, font, x, y, text, Paint.solid(color));
    }

    private static void drawTextLine(RenderManager renderer, Layout layout, Font font,
                                     float x, float y, String text, Paint paint) {
        drawTextLine(renderer, layout, font, x, y, text, paint,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);
    }

    private static void drawTextLine(RenderManager renderer, Layout layout, Font font,
                                     float x, float y, String text, Color color,
                                     HorizontalAlignment horizontal, VerticalAlignment vertical) {
        drawTextLine(renderer, layout, font, x, y, text, Paint.solid(color), horizontal, vertical);
    }

    private static void drawTextLine(RenderManager renderer, Layout layout, Font font,
                                     float x, float y, String text, Paint paint,
                                     HorizontalAlignment horizontal, VerticalAlignment vertical) {
        Point origin = layout.point(x, y);
        Transform transform = Transform.translation(origin.x(), origin.y())
                .multiply(Transform.scaling(layout.scale(), layout.scale()));
        renderer.withTransform(transform,
                () -> renderer.text(font, text, 0, 0, paint, horizontal, vertical));
    }

    private static Transform rotateAround(Point center, float angle) {
        return Transform.translation(center.x(), center.y())
                .multiply(Transform.rotation(angle))
                .multiply(Transform.translation(-center.x(), -center.y()));
    }

    private record Layout(float scale, float offsetX, float offsetY) {
        static Layout fit(Rect viewport) {
            float scale = Math.min(viewport.width() / DESIGN_WIDTH, viewport.height() / DESIGN_HEIGHT);
            return new Layout(scale,
                    viewport.x() + (viewport.width() - DESIGN_WIDTH * scale) / 2,
                    viewport.y() + (viewport.height() - DESIGN_HEIGHT * scale) / 2);
        }

        Point point(float x, float y) {
            return new Point(offsetX + x * scale, offsetY + y * scale);
        }

        Rect rect(float x, float y, float width, float height) {
            return new Rect(offsetX + x * scale, offsetY + y * scale, width * scale, height * scale);
        }

        float scale(float value) {
            return value * scale;
        }
    }
}
