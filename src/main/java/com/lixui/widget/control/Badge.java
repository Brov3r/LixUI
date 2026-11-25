package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

import java.util.Objects;

/**
 * A short pill with a counter or label, or a small dot when no text is assigned.
 * The badge sizes itself from the text and padding while auto-sizing is enabled. An empty text
 * measures as a square dot of {@link #dotSize()}. The widget is decorative and never captures
 * pointer input, so it can sit in the corner of another widget without blocking its interactions.
 * The default colors come from the current theme and can be overridden individually.
 */
public class Badge extends Widget {
    private static final float DEFAULT_PADDING = 6;
    private static final float DEFAULT_DOT_SIZE = 8;
    private static final TextStyle CENTERED = TextStyle.of(
            HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

    private String text;
    private Color backgroundColor;
    private Color textColor;
    private Color borderColor;
    private float borderWidth;
    private float padding = DEFAULT_PADDING;
    private float dotSize = DEFAULT_DOT_SIZE;

    /**
     * Creates an empty badge that draws a themed dot.
     */
    public Badge() {
        this("");
    }

    /**
     * Creates a badge with text.
     * An empty text creates a dot badge.
     *
     * @param text badge label
     * @throws NullPointerException if {@code text} is null
     */
    public Badge(String text) {
        this.text = Objects.requireNonNull(text, "text");
        setAutoSize(true);
    }

    /**
     * Returns the badge label.
     *
     * @return the label, or an empty string for a dot badge
     */
    public final String text() {
        return text;
    }

    /**
     * Replaces the badge label.
     * An empty string turns the badge into a dot.
     *
     * @param text badge label
     * @throws NullPointerException if {@code text} is null
     */
    public final void setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * Returns the explicit background color.
     *
     * @return the background color, or null when the theme primary color is used
     */
    public final Color backgroundColor() {
        return backgroundColor;
    }

    /**
     * Assigns an explicit background color.
     *
     * @param backgroundColor background color, or null to use the theme primary color
     */
    public final void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    /**
     * Returns the explicit text color.
     *
     * @return the text color, or null when the theme content color is used
     */
    public final Color textColor() {
        return textColor;
    }

    /**
     * Assigns an explicit text color.
     *
     * @param textColor text color, or null to use the theme content color
     */
    public final void setTextColor(Color textColor) {
        this.textColor = textColor;
    }

    /**
     * Returns the explicit border color.
     *
     * @return the border color, or null when the theme border color is used
     */
    public final Color borderColor() {
        return borderColor;
    }

    /**
     * Assigns an explicit border color.
     * The border is only drawn while {@link #borderWidth()} is positive.
     *
     * @param borderColor border color, or null to use the theme border color
     */
    public final void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
    }

    /**
     * Returns the border width.
     *
     * @return the border width in logical UI units
     */
    public final float borderWidth() {
        return borderWidth;
    }

    /**
     * Sets the border width.
     *
     * @param borderWidth non-negative border width in logical UI units
     * @throws IllegalArgumentException if the width is negative or non-finite
     */
    public final void setBorderWidth(float borderWidth) {
        if (!Float.isFinite(borderWidth) || borderWidth < 0) {
            throw new IllegalArgumentException("Border width must be finite and non-negative.");
        }
        this.borderWidth = borderWidth;
    }

    /**
     * Returns the inner padding around the label.
     *
     * @return the padding in logical UI units
     */
    public final float padding() {
        return padding;
    }

    /**
     * Sets the inner padding around the label.
     * The padding is applied on all sides when the badge measures itself.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public final void setPadding(float padding) {
        if (!Float.isFinite(padding) || padding < 0) {
            throw new IllegalArgumentException("Padding must be finite and non-negative.");
        }
        this.padding = padding;
    }

    /**
     * Returns the dot diameter used when the badge has no text.
     *
     * @return the dot diameter in logical UI units
     */
    public final float dotSize() {
        return dotSize;
    }

    /**
     * Sets the dot diameter used when the badge has no text.
     *
     * @param dotSize positive dot diameter in logical UI units
     * @throws IllegalArgumentException if the diameter is not positive or non-finite
     */
    public final void setDotSize(float dotSize) {
        if (!Float.isFinite(dotSize) || dotSize <= 0) {
            throw new IllegalArgumentException("Dot size must be finite and positive.");
        }
        this.dotSize = dotSize;
    }

    /**
     * Measures the label with padding, or a square dot when no text is assigned.
     *
     * @param constraints available and required dimensions
     * @return the natural badge size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        if (text.isEmpty()) {
            return new Size(dotSize, dotSize);
        }
        Font font = font();
        if (font == null) {
            return new Size(bounds().width(), bounds().height());
        }
        Size textSize = font.measure(text);
        return new Size(textSize.width() + padding * 2, textSize.height() + padding * 2);
    }

    /**
     * Paints the background, border and label.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        Color background = backgroundColor != null ? backgroundColor : theme.primary();
        if (text.isEmpty()) {
            drawDot(renderer, background, theme.border());
            return;
        }
        float width = localBounds().width();
        float height = localBounds().height();
        float radius = Math.min(width, height) / 2;
        renderer.roundedRect(localBounds(), radius, background);
        drawBorder(renderer, radius, borderColor != null ? borderColor : theme.border());
        Font font = font();
        if (font != null) {
            Color color = textColor != null ? textColor : theme.onPrimary();
            renderer.text(font, text, width / 2, height / 2, color, CENTERED);
        }
    }

    /**
     * Keeps the decorative badge from intercepting pointer input.
     *
     * @param x horizontal coordinate relative to the widget's top-left corner
     * @param y vertical coordinate relative to the widget's top-left corner
     * @return always null
     */
    @Override
    public Widget hitTest(float x, float y) {
        return null;
    }

    private void drawDot(RenderManager renderer, Color background, Color themeBorder) {
        float diameter = Math.min(localBounds().width(), localBounds().height());
        if (diameter <= 0) {
            return;
        }
        float radius = diameter / 2;
        Point center = new Point(localBounds().width() / 2, localBounds().height() / 2);
        renderer.fillCircle(center, radius, Paint.solid(background));
        Color border = borderColor != null ? borderColor : themeBorder;
        if (borderWidth > 0 && border.a() > 0) {
            renderer.strokeCircle(center, Math.max(0, radius - borderWidth / 2), borderWidth,
                    Paint.solid(border));
        }
    }

    private void drawBorder(RenderManager renderer, float radius, Color color) {
        if (borderWidth <= 0 || color.a() <= 0) {
            return;
        }
        float inset = borderWidth / 2;
        Rect stroke = new Rect(inset, inset,
                Math.max(0, localBounds().width() - borderWidth),
                Math.max(0, localBounds().height() - borderWidth));
        if (stroke.isEmpty()) {
            return;
        }
        renderer.strokeRoundedRect(stroke, Math.max(0, radius - inset), borderWidth,
                Paint.solid(color));
    }
}
