package com.lixui.widget.container;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A framed panel with a title placed directly on the upper border and a single content widget.
 * The frame uses the panel style, so it follows the current theme and inherited styling. The title
 * occupies a strip at the top of the widget and the border line crosses its vertical center;
 * the content is laid out inside the remaining area with {@link #contentPadding()}.
 */
public class GroupBox extends Panel {
    private static final float DEFAULT_TITLE_HEIGHT = 20;
    private static final float DEFAULT_TITLE_PADDING = 12;
    private static final float DEFAULT_TITLE_GAP = 5;
    private static final float DEFAULT_CONTENT_PADDING = 10;
    private static final float CORNER_TOLERANCE = 0.1f;
    private static final float MIN_CORNER_RADIUS = 0.0001f;

    private String title;
    private Widget content;
    private float titleHeight = DEFAULT_TITLE_HEIGHT;
    private float titlePadding = DEFAULT_TITLE_PADDING;
    private float titleGap = DEFAULT_TITLE_GAP;
    private float contentPadding = DEFAULT_CONTENT_PADDING;

    /**
     * Creates an empty group box without a title.
     */
    public GroupBox() {
        this("");
    }

    /**
     * Creates a group box with the supplied title.
     *
     * @param title title drawn on the upper border
     * @throws NullPointerException if {@code title} is null
     */
    public GroupBox(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    /**
     * Returns the title drawn on the upper border.
     *
     * @return the title, possibly empty
     */
    public String title() {
        return title;
    }

    /**
     * Replaces the title drawn on the upper border.
     *
     * @param title title text, possibly empty
     * @throws NullPointerException if {@code title} is null
     */
    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    /**
     * Returns the content widget.
     *
     * @return the content, or null when the group box is empty
     */
    public Widget content() {
        return content;
    }

    /**
     * Replaces the content widget.
     * The new content is attached before the previous one is detached, so a failed attachment
     * keeps the current content in place.
     *
     * @param content widget shown inside the frame
     * @throws NullPointerException if {@code content} is null
     * @throws IllegalArgumentException if the content already has a parent or manager
     */
    public void setContent(Widget content) {
        Objects.requireNonNull(content, "content");
        if (content == this.content) {
            return;
        }
        add(content);
        Widget previous = this.content;
        this.content = content;
        if (previous != null) {
            remove(previous);
        }
    }

    /**
     * Removes the content widget and leaves the frame empty.
     */
    public void clearContent() {
        Widget previous = content;
        content = null;
        if (previous != null) {
            remove(previous);
        }
    }

    /**
     * Returns the height of the title strip.
     *
     * @return the title strip height in logical UI units
     */
    public float titleHeight() {
        return titleHeight;
    }

    /**
     * Sets the height of the title strip.
     * The border line crosses the vertical center of the strip, so zero places it on the very top
     * edge of the widget. An empty title takes no strip.
     *
     * @param titleHeight non-negative title strip height in logical UI units
     * @throws IllegalArgumentException if the height is negative or non-finite
     */
    public void setTitleHeight(float titleHeight) {
        if (!Float.isFinite(titleHeight) || titleHeight < 0) {
            throw new IllegalArgumentException("Title height must be finite and non-negative.");
        }
        this.titleHeight = titleHeight;
    }

    /**
     * Returns the horizontal inset of the title from the left border.
     *
     * @return the inset in logical UI units
     */
    public float titlePadding() {
        return titlePadding;
    }

    /**
     * Sets the horizontal inset of the title from the left border.
     *
     * @param titlePadding non-negative inset in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setTitlePadding(float titlePadding) {
        if (!Float.isFinite(titlePadding) || titlePadding < 0) {
            throw new IllegalArgumentException("Title padding must be finite and non-negative.");
        }
        this.titlePadding = titlePadding;
    }

    /**
     * Returns the gap on both sides of the title where the border is interrupted.
     *
     * @return the gap in logical UI units
     */
    public float titleGap() {
        return titleGap;
    }

    /**
     * Sets the gap on both sides of the title where the border is interrupted.
     *
     * @param titleGap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public void setTitleGap(float titleGap) {
        if (!Float.isFinite(titleGap) || titleGap < 0) {
            throw new IllegalArgumentException("Title gap must be finite and non-negative.");
        }
        this.titleGap = titleGap;
    }

    /**
     * Returns the padding around the content inside the frame.
     *
     * @return the padding in logical UI units
     */
    public float contentPadding() {
        return contentPadding;
    }

    /**
     * Sets the padding around the content inside the frame.
     *
     * @param contentPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setContentPadding(float contentPadding) {
        if (!Float.isFinite(contentPadding) || contentPadding < 0) {
            throw new IllegalArgumentException("Content padding must be finite and non-negative.");
        }
        this.contentPadding = contentPadding;
    }

    /**
     * Measures the content plus the title strip and padding.
     * The width also fits the title so the text stays inside the frame.
     *
     * @param constraints available and required dimensions
     * @return the natural group box size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float strip = titleStripHeight();
        float availableWidth = Math.max(0, constraints.maxWidth() - contentPadding * 2);
        float availableHeight = Math.max(0, constraints.maxHeight() - strip - contentPadding * 2);
        Size natural = content == null || !content.isVisible() ? new Size(0, 0)
                : content.measure(LayoutConstraints.loose(availableWidth, availableHeight));
        float width = Math.max(natural.width() + contentPadding * 2, titleWidth());
        return new Size(width, strip + natural.height() + contentPadding * 2);
    }

    /**
     * Places the content inside the padded area below the title strip.
     * Invisible content keeps its current bounds.
     */
    @Override
    protected void onLayout() {
        if (content == null || !content.isVisible()) {
            return;
        }
        float top = titleStripHeight() + contentPadding;
        content.layout(new Rect(contentPadding, top,
                Math.max(0, bounds().width() - contentPadding * 2),
                Math.max(0, bounds().height() - top - contentPadding)));
    }

    /**
     * Paints the frame with a gap in the upper border for the title and then draws the title.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        WidgetStyle style = style();
        if (style == null || localBounds().isEmpty()) {
            return;
        }
        WidgetState state = state();
        float borderWidth = style.borderWidth();
        float radius = style.cornerRadius();
        float lineY = Math.max(borderWidth / 2,
                Math.min(titleStripHeight() / 2, bounds().height() / 2));
        Color background = style.background(state);
        if (background.a() > 0) {
            renderer.roundedRect(localBounds(), radius, background);
        }
        Font font = font();
        float textWidth = titleTextWidth(font);
        if (borderWidth > 0) {
            if (textWidth > 0) {
                float gapLeft = Math.max(0, titlePadding - titleGap);
                float gapRight = Math.min(bounds().width(),
                        titlePadding + textWidth + titleGap);
                drawInterruptedBorder(renderer, lineY, borderWidth, radius, gapLeft, gapRight,
                        style.border(state));
            } else {
                drawBorder(renderer, lineY, borderWidth, radius, style.border(state));
            }
        }
        if (textWidth > 0) {
            drawTitle(renderer, font, style, state, lineY, textWidth);
        }
    }

    private float titleStripHeight() {
        return title.isEmpty() ? 0 : titleHeight;
    }

    private float titleWidth() {
        Font font = font();
        if (title.isEmpty() || font == null) {
            return contentPadding * 2;
        }
        return font.measure(title).width() + titlePadding * 2;
    }

    private float titleTextWidth(Font font) {
        if (title.isEmpty() || font == null) {
            return 0;
        }
        float available = Math.max(0, bounds().width() - titlePadding * 2);
        return Math.min(font.measure(title).width(), available);
    }

    private void drawBorder(RenderManager renderer, float lineY, float borderWidth,
                            float radius, Color color) {
        Rect stroke = new Rect(borderWidth / 2, lineY,
                Math.max(0, bounds().width() - borderWidth),
                Math.max(0, bounds().height() - lineY - borderWidth / 2));
        if (!stroke.isEmpty()) {
            renderer.strokeRoundedRect(stroke, Math.max(0, radius - borderWidth / 2),
                    borderWidth, Paint.solid(color));
        }
    }

    private void drawInterruptedBorder(RenderManager renderer, float lineY, float borderWidth,
                                       float radius, float gapLeft, float gapRight,
                                       Color color) {
        float inset = borderWidth / 2;
        float left = inset;
        float top = lineY;
        float right = bounds().width() - inset;
        float bottom = bounds().height() - inset;
        if (right <= left || bottom <= top) {
            return;
        }
        float corner = Math.min(Math.max(0, radius - inset), Math.min(right - left, bottom - top) / 2);
        float first = Math.clamp(gapLeft, left + corner, right - corner);
        float second = Math.clamp(gapRight, first, right - corner);
        List<Point> points = new ArrayList<>();
        points.add(new Point(second, top));
        points.add(new Point(right - corner, top));
        if (corner > 0) {
            appendArc(points, right - corner, top + corner, corner, (float) (-Math.PI / 2));
        } else {
            points.add(new Point(right, top));
        }
        points.add(new Point(right, bottom - corner));
        if (corner > 0) {
            appendArc(points, right - corner, bottom - corner, corner, 0);
        } else {
            points.add(new Point(right, bottom));
        }
        points.add(new Point(left + corner, bottom));
        if (corner > 0) {
            appendArc(points, left + corner, bottom - corner, corner, (float) (Math.PI / 2));
        } else {
            points.add(new Point(left, bottom));
        }
        points.add(new Point(left, top + corner));
        if (corner > 0) {
            appendArc(points, left + corner, top + corner, corner, (float) Math.PI);
        } else {
            points.add(new Point(left, top));
        }
        points.add(new Point(first, top));
        renderer.strokePolyline(points, borderWidth, Paint.solid(color));
    }

    private static void appendArc(List<Point> points, float centerX, float centerY, float radius,
                                  float startAngle) {
        int segments = cornerSegments(radius);
        for (int index = 0; index <= segments; index++) {
            float angle = startAngle + (float) (Math.PI / 2) * index / segments;
            points.add(new Point(centerX + (float) Math.cos(angle) * radius,
                    centerY + (float) Math.sin(angle) * radius));
        }
    }

    private static int cornerSegments(float radius) {
        float ratio = Math.min(CORNER_TOLERANCE / Math.max(radius, MIN_CORNER_RADIUS), 0.9f);
        double step = 2 * Math.acos(1 - ratio);
        return Math.clamp((int) Math.ceil(Math.PI / 2 / step), 3, 48);
    }

    private void drawTitle(RenderManager renderer, Font font, WidgetStyle style,
                           WidgetState state, float lineY, float textWidth) {
        TextStyle textStyle = new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        renderer.text(font, title, titlePadding, lineY, style.text(state), textStyle);
    }
}
