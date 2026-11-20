package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
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
import com.lixui.widget.Widget;

import java.util.Objects;

/**
 * A form row with a label above, a single control in the middle and a hint or error below.
 * The control fills the width and the vertical space between the label and the message. The
 * natural height follows the label, the control and the message, so enabling
 * {@link #setAutoSize(boolean)} wraps the field around its content.
 * <p>
 * The error message takes precedence over the hint, which lets validation errors replace the
 * normal guidance without losing it. The field draws no surface and never blocks pointer input
 * outside its control.
 */
public final class FormField extends LayoutContainer {
    private static final Color DEFAULT_ERROR_COLOR = Color.hex("#E06C75");
    private static final float DEFAULT_GAP = 4;

    private String label = "";
    private String hint = "";
    private String error = "";
    private Color errorColor = DEFAULT_ERROR_COLOR;
    private Widget content;
    private float gap = DEFAULT_GAP;

    /**
     * Creates an empty form field without a label, control or message.
     */
    public FormField() {
    }

    /**
     * Creates a form field with a label, control and hint.
     *
     * @param label label drawn above the control, possibly empty
     * @param content control shown between the label and the message
     * @param hint hint drawn below the control, possibly empty
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the control already has a parent or manager
     */
    public FormField(String label, Widget content, String hint) {
        setLabel(label);
        setHint(hint);
        setContent(content);
    }

    /**
     * Returns the label drawn above the control.
     *
     * @return the label, possibly empty
     */
    public String label() {
        return label;
    }

    /**
     * Replaces the label drawn above the control.
     *
     * @param label label text, possibly empty
     * @throws NullPointerException if {@code label} is null
     */
    public void setLabel(String label) {
        this.label = Objects.requireNonNull(label, "label");
    }

    /**
     * Returns the control shown between the label and the message.
     *
     * @return the control, or null when the field has no content
     */
    public Widget content() {
        return content;
    }

    /**
     * Replaces the control shown between the label and the message.
     * The new control is attached before the previous one is detached, so a failed attachment
     * keeps the current control in place.
     *
     * @param content control to show
     * @throws NullPointerException if {@code content} is null
     * @throws IllegalArgumentException if the control already has a parent or manager
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
     * Removes the control and leaves the field with its label and message only.
     */
    public void clearContent() {
        Widget previous = content;
        content = null;
        if (previous != null) {
            remove(previous);
        }
    }

    /**
     * Returns the hint drawn below the control.
     *
     * @return the hint, possibly empty
     */
    public String hint() {
        return hint;
    }

    /**
     * Replaces the hint drawn below the control.
     * The hint is shown only while no error message is set.
     *
     * @param hint hint text, possibly empty
     * @throws NullPointerException if {@code hint} is null
     */
    public void setHint(String hint) {
        this.hint = Objects.requireNonNull(hint, "hint");
    }

    /**
     * Returns the error message drawn below the control.
     *
     * @return the error message, possibly empty
     */
    public String error() {
        return error;
    }

    /**
     * Replaces the error message drawn below the control.
     * A non-empty error message replaces the hint until it is cleared with an empty string.
     *
     * @param error error message, possibly empty
     * @throws NullPointerException if {@code error} is null
     */
    public void setError(String error) {
        this.error = Objects.requireNonNull(error, "error");
    }

    /**
     * Reports whether an error message is currently shown.
     *
     * @return true when the error message is not empty
     */
    public boolean isError() {
        return !error.isEmpty();
    }

    /**
     * Returns the color used for the error message.
     *
     * @return the error color, never null
     */
    public Color errorColor() {
        return errorColor;
    }

    /**
     * Replaces the color used for the error message.
     *
     * @param errorColor error color
     * @throws NullPointerException if {@code errorColor} is null
     */
    public void setErrorColor(Color errorColor) {
        this.errorColor = Objects.requireNonNull(errorColor, "errorColor");
    }

    /**
     * Returns the gap between the label, the control and the message.
     *
     * @return the gap in logical UI units
     */
    public float gap() {
        return gap;
    }

    /**
     * Sets the gap between the label, the control and the message.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public void setGap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Form field gap must be finite and non-negative.");
        }
        this.gap = gap;
    }

    /**
     * Measures the label, control and message plus gaps and padding.
     * An invisible control takes no space, matching the other layout containers.
     *
     * @param constraints available and required dimensions
     * @return the natural form field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = availableWidth(constraints);
        Size labelSize = measureText(label, availableWidth);
        Size messageSize = measureText(message(), availableWidth);
        boolean contentVisible = content != null && content.isVisible();
        float contentHeight = Math.max(0, availableHeight(constraints)
                - labelSize.height() - messageSize.height() - gaps(contentVisible));
        Size contentSize = contentVisible
                ? content.measure(LayoutConstraints.loose(availableWidth, contentHeight))
                : new Size(0, 0);
        float width = Math.max(Math.max(labelSize.width(), messageSize.width()),
                contentSize.width()) + padding() * 2;
        float height = labelSize.height() + contentSize.height() + messageSize.height()
                + gaps(contentVisible) + padding() * 2;
        return new Size(width, height);
    }

    /**
     * Places the control between the label and the message and stretches it over the remaining
     * content area. Invisible controls keep their bounds.
     */
    @Override
    protected void onLayout() {
        boolean contentVisible = content != null && content.isVisible();
        float contentWidth = contentWidth();
        Size labelSize = measureText(label, contentWidth);
        Size messageSize = measureText(message(), contentWidth);
        float top = padding();
        if (!label.isEmpty()) {
            top += labelSize.height();
            if (contentVisible || !message().isEmpty()) {
                top += gap;
            }
        }
        float bottom = bounds().height() - padding();
        if (!message().isEmpty()) {
            bottom -= messageSize.height();
            if (contentVisible || !label.isEmpty()) {
                bottom -= gap;
            }
        }
        if (contentVisible) {
            content.layout(new Rect(padding(), top, contentWidth,
                    Math.max(0, bottom - top)));
        }
    }

    /**
     * Draws the label above the control and the hint or error below it.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        Font font = font();
        WidgetStyle style = style();
        if (font == null || style == null) {
            return;
        }
        float contentWidth = contentWidth();
        Size labelSize = measureText(label, contentWidth);
        if (!label.isEmpty() && labelSize.height() > 0) {
            drawText(renderer, font, label, padding(), padding(), contentWidth,
                    style.text(state()));
        }
        String message = message();
        Size messageSize = measureText(message, contentWidth);
        if (!message.isEmpty() && messageSize.height() > 0) {
            float messageY = bounds().height() - padding() - messageSize.height();
            drawText(renderer, font, message, padding(), messageY, contentWidth,
                    isError() ? errorColor : hintColor(style));
        }
    }

    private String message() {
        return error.isEmpty() ? hint : error;
    }

    private float gaps(boolean contentVisible) {
        int count = 0;
        if (!label.isEmpty()) {
            count++;
        }
        if (contentVisible) {
            count++;
        }
        if (!message().isEmpty()) {
            count++;
        }
        return count > 1 ? gap * (count - 1) : 0;
    }

    private Color hintColor(WidgetStyle style) {
        return context() == null ? style.text(WidgetState.NORMAL) : context().theme().textMuted();
    }

    private Size measureText(String text, float maxWidth) {
        Font font = font();
        if (font == null || text.isEmpty()) {
            return new Size(0, 0);
        }
        return font.measure(text, textStyle(maxWidth));
    }

    private void drawText(RenderManager renderer, Font font, String text, float x, float y,
                          float width, Color color) {
        renderer.text(font, text, x, y, color, textStyle(width));
    }

    private static TextStyle textStyle(float maxWidth) {
        return new TextStyle(maxWidth, 0, 0, TextOverflow.CLIP,
                HorizontalAlignment.LEFT, VerticalAlignment.TOP);
    }
}
