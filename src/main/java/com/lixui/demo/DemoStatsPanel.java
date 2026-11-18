package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.Panel;

/**
 * A compact panel that displays frame rate, draw calls and the number of visible widgets.
 * The panel sizes itself to the measured text of its columns.
 */
final class DemoStatsPanel extends Panel {
    private static final String[] LABELS = {"FPS", "DrawCall", "Widgets"};
    private static final float LABEL_GAP = 6;
    private static final float COLUMN_GAP = 18;
    private static final float PADDING_HORIZONTAL = 12;
    private static final float PADDING_VERTICAL = 8;

    private int fps;
    private int drawCalls;
    private int visibleWidgets;

    /**
     * Assigns the measured frame rate.
     *
     * @param fps frames per second
     */
    void setFps(int fps) {
        this.fps = Math.max(0, fps);
    }

    /**
     * Assigns the draw call count of the last rendered frame.
     *
     * @param drawCalls submitted draw calls
     */
    void setDrawCalls(int drawCalls) {
        this.drawCalls = Math.max(0, drawCalls);
    }

    /**
     * Assigns the number of widgets currently visible in the tree.
     *
     * @param visibleWidgets visible widget count
     */
    void setVisibleWidgets(int visibleWidgets) {
        this.visibleWidgets = Math.max(0, visibleWidgets);
    }

    /**
     * Resizes the panel to fit the measured metric columns.
     *
     * @param renderer drawing manager used to measure text
     */
    void resizeToContent(RenderManager renderer) {
        Font font = font();
        if (font == null) {
            return;
        }
        String[] values = values();
        float width = PADDING_HORIZONTAL * 2;
        for (int index = 0; index < LABELS.length; index++) {
            width += renderer.measureText(font, LABELS[index]).width() + LABEL_GAP
                    + renderer.measureText(font, values[index]).width();
            if (index < LABELS.length - 1) {
                width += COLUMN_GAP;
            }
        }
        float height = renderer.measureText(font, LABELS[0]).height() + PADDING_VERTICAL * 2;
        setSize(width, height);
    }

    /**
     * Paints the panel background and the three metric columns.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        Font font = font();
        WidgetStyle style = style();
        if (font == null || style == null) {
            return;
        }
        Color labelColor = context().theme().textMuted();
        Color valueColor = style.text();
        String[] values = values();
        float centerY = localBounds().height() / 2;
        float x = PADDING_HORIZONTAL;
        for (int index = 0; index < LABELS.length; index++) {
            renderer.text(font, LABELS[index], x, centerY, labelColor,
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            x += renderer.measureText(font, LABELS[index]).width() + LABEL_GAP;
            renderer.text(font, values[index], x, centerY, valueColor,
                    HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            x += renderer.measureText(font, values[index]).width() + COLUMN_GAP;
        }
    }

    private String[] values() {
        return new String[]{
                Integer.toString(fps),
                Integer.toString(drawCalls),
                Integer.toString(visibleWidgets)
        };
    }
}
