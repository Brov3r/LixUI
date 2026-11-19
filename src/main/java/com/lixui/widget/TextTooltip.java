package com.lixui.widget;

import com.lixui.geometry.Size;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;

import java.util.Objects;

/**
 * A ready-made tooltip that draws panel styling with a single text label.
 * The widget measures itself from the label with compact padding and uses the inherited font.
 * Assign it with {@link Widget#setTooltip(Widget)}; custom tooltips may use any other widget.
 */
public final class TextTooltip extends Panel {
    private static final float CORNER_RADIUS = 6;
    private static final float PADDING = 8;

    private String text;

    /**
     * Creates a tooltip without a label.
     */
    public TextTooltip() {
        this("");
    }

    /**
     * Creates a tooltip with a label.
     *
     * @param text label text
     * @throws NullPointerException if {@code text} is null
     */
    public TextTooltip(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Returns the tooltip label.
     *
     * @return the label text
     */
    public String text() {
        return text;
    }

    /**
     * Replaces the tooltip label.
     *
     * @param text label text
     * @throws NullPointerException if {@code text} is null
     */
    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Returns the panel style with compact tooltip padding and corner radius.
     *
     * @return the tooltip style, or null when the tooltip is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        WidgetStyle base = super.defaultStyle();
        if (base == null) {
            return null;
        }
        return new WidgetStyle(base.background(), base.hoverBackground(), base.pressedBackground(),
                base.disabledBackground(), base.text(), base.disabledText(), base.border(),
                base.focusBorder(), CORNER_RADIUS, PADDING, 1);
    }

    /**
     * Measures the label plus tooltip padding.
     *
     * @param constraints available and required dimensions
     * @return the natural tooltip size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        if (font == null || text.isEmpty()) {
            return super.onMeasure(constraints);
        }
        Size measured = font.measure(text);
        return new Size(measured.width() + PADDING * 2, measured.height() + PADDING * 2);
    }

    /**
     * Paints the panel surface and the centered label.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        Font font = font();
        WidgetStyle style = style();
        if (font == null || style == null || text.isEmpty()) {
            return;
        }
        renderer.text(font, text, PADDING, localBounds().height() / 2,
                style.text(WidgetState.NORMAL), HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    }
}
