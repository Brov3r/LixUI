package com.lixui.widget;

import com.lixui.geometry.Point;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;

import java.util.function.Consumer;

/**
 * A rotary value control drawn as a knob with an arc track.
 * Dragging vertically changes the value; upward movement increases it and downward movement
 * decreases it. The mouse wheel and the keyboard also adjust the value while the knob is hovered
 * or focused. A positive step snaps all values to the step grid. The current value is drawn in the
 * center, and compact min and max labels can be shown below the knob.
 */
public class KnobSlider extends Widget {
    private static final float DEFAULT_SIZE = 96;
    private static final float DEFAULT_TRACK_THICKNESS = 5;
    private static final float DRAG_MIN_RANGE = 80;
    private static final float LABEL_PAD = 6;
    private static final float ARC_DIAGONAL = 0.70710677f;
    private static final float POINTER_WIDTH = 2;
    private static final float START_ANGLE = (float) (Math.PI * 0.75);
    private static final float SWEEP_ANGLE = (float) (Math.PI * 1.5);

    private float minimum;
    private float maximum = 1;
    private float value;
    private float step;
    private float knobSize = DEFAULT_SIZE;
    private float trackThickness = DEFAULT_TRACK_THICKNESS;
    private boolean showRangeLabels = true;
    private Consumer<Float> onChange;
    private float dragStartY;
    private float dragStartValue;

    /**
     * Creates a knob over the {@code [0, 1]} range with an initial value of zero.
     */
    public KnobSlider() {
        setFocusable(true);
    }

    /**
     * Returns the range start.
     *
     * @return the minimum value
     */
    public final float minimum() {
        return minimum;
    }

    /**
     * Returns the range end.
     *
     * @return the maximum value
     */
    public final float maximum() {
        return maximum;
    }

    /**
     * Replaces the range and clamps the current value into it.
     *
     * @param minimum range start
     * @param maximum range end
     * @throws IllegalArgumentException if a bound is non-finite or the minimum is not below the
     *                                  maximum
     */
    public final void setRange(float minimum, float maximum) {
        SliderSupport.validateRange(minimum, maximum);
        this.minimum = minimum;
        this.maximum = maximum;
        changeValue(value);
    }

    /**
     * Returns the current value.
     *
     * @return the selected value
     */
    public final float value() {
        return value;
    }

    /**
     * Selects a value, clamping it into the range and snapping it to the step grid.
     * The change callback is invoked when the value actually changes.
     *
     * @param value new value
     * @throws IllegalArgumentException if the value is non-finite
     */
    public final void setValue(float value) {
        SliderSupport.finite(value, "Knob value");
        changeValue(value);
    }

    /**
     * Returns the step size.
     *
     * @return the step size, or zero when the range is continuous
     */
    public final float step() {
        return step;
    }

    /**
     * Sets the step size used to snap values.
     * Zero disables snapping. The current value is snapped immediately when the step changes.
     *
     * @param step non-negative step size
     * @throws IllegalArgumentException if the step is negative or non-finite
     */
    public final void setStep(float step) {
        if (!Float.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("Knob step must be finite and non-negative.");
        }
        this.step = step;
        changeValue(value);
    }

    /**
     * Returns the knob diameter.
     *
     * @return the knob size in logical UI units
     */
    public final float knobSize() {
        return knobSize;
    }

    /**
     * Sets the knob diameter used by measurement and drawing.
     *
     * @param knobSize positive diameter in logical UI units
     * @throws IllegalArgumentException if the size is not positive or is non-finite
     */
    public final void setKnobSize(float knobSize) {
        if (!Float.isFinite(knobSize) || knobSize <= 0) {
            throw new IllegalArgumentException("Knob size must be finite and positive.");
        }
        this.knobSize = knobSize;
    }

    /**
     * Returns the arc stroke thickness.
     *
     * @return the thickness in logical UI units
     */
    public final float trackThickness() {
        return trackThickness;
    }

    /**
     * Sets the arc stroke thickness.
     *
     * @param trackThickness positive thickness in logical UI units
     * @throws IllegalArgumentException if the thickness is not positive or is non-finite
     */
    public final void setTrackThickness(float trackThickness) {
        if (!Float.isFinite(trackThickness) || trackThickness <= 0) {
            throw new IllegalArgumentException("Track thickness must be finite and positive.");
        }
        this.trackThickness = trackThickness;
    }

    /**
     * Reports whether the min and max labels are shown.
     *
     * @return true when range labels are enabled
     */
    public final boolean isShowRangeLabels() {
        return showRangeLabels;
    }

    /**
     * Shows or hides the compact min and max labels.
     *
     * @param showRangeLabels true to show range labels
     */
    public final void setShowRangeLabels(boolean showRangeLabels) {
        this.showRangeLabels = showRangeLabels;
    }

    /**
     * Returns the callback invoked when the value changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public final Consumer<Float> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked when the value changes, including programmatic changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(Consumer<Float> onChange) {
        this.onChange = onChange;
    }

    /**
     * Requests the vertical resize cursor used for dragging.
     *
     * @return the vertical resize cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.RESIZE_VERTICAL;
    }

    /**
     * Measures the knob, including an optional row of range labels.
     *
     * @param constraints available and required dimensions
     * @return the natural knob size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float pad = showRangeLabels && font() != null ? LABEL_PAD : 0;
        return new Size(knobSize, knobSize + pad);
    }

    /**
     * Paints the knob body, the arc track, the active arc, the value pointer, the current value
     * and the range labels.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (context() == null || localBounds().isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        Font font = font();
        boolean labels = showRangeLabels && font != null;
        float contentHeight = Math.max(0, localBounds().height() - (labels ? LABEL_PAD : 0));
        float size = Math.min(localBounds().width(), contentHeight);
        float line = Math.min(trackThickness, size / 4);
        float radius = size / 2 - line / 2 - 2;
        if (radius <= 0 || line <= 0) {
            return;
        }
        Point center = new Point(localBounds().width() / 2, contentHeight / 2);
        float bodyRadius = radius - line * 1.6f;
        if (bodyRadius > 0) {
            renderer.fillCircle(center, bodyRadius,
                    Paint.solid(isEnabled() ? theme.surface() : theme.background()));
        }
        drawArc(renderer, center, radius, START_ANGLE, SWEEP_ANGLE, line,
                SliderSupport.trackColor(theme, isEnabled()));
        float fraction = SliderSupport.fraction(value, minimum, maximum);
        float activeSweep = SWEEP_ANGLE * fraction;
        if (activeSweep > 0.001f) {
            drawArc(renderer, center, radius, START_ANGLE, activeSweep, line,
                    SliderSupport.accentColor(theme, isEnabled()));
        }
        float angle = START_ANGLE + SWEEP_ANGLE * fraction;
        float pointerStart = bodyRadius * 0.55f;
        float pointerEnd = bodyRadius * 0.82f;
        if (pointerEnd > pointerStart) {
            renderer.strokeLine(
                    new Point(center.x() + (float) Math.cos(angle) * pointerStart,
                            center.y() + (float) Math.sin(angle) * pointerStart),
                    new Point(center.x() + (float) Math.cos(angle) * pointerEnd,
                            center.y() + (float) Math.sin(angle) * pointerEnd),
                    POINTER_WIDTH, Paint.solid(SliderSupport.thumbColor(theme, isEnabled(),
                            isHovered() || isPressed())));
        }
        drawValue(renderer, theme, font, center, bodyRadius);
        if (labels) {
            drawLabels(renderer, theme, font, center, radius);
        }
    }

    /**
     * Remembers the drag start so vertical movement can change the value.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press is consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        dragStartY = y;
        dragStartValue = value;
        return true;
    }

    /**
     * Changes the value from vertical pointer movement.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button dragged button
     * @param modifiers modifiers captured at press time
     * @return true when the drag is consumed
     */
    @Override
    protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        float range = maximum - minimum;
        float sensitivity = Math.max(DRAG_MIN_RANGE, knobSize * 2);
        setValue(dragStartValue + (dragStartY - y) / sensitivity * range);
        return true;
    }

    /**
     * Consumes the primary release that ends a knob drag.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button released button
     * @param modifiers active modifiers
     * @return true for the primary button
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button,
                                       Modifiers modifiers) {
        return button == MouseButton.LEFT;
    }

    /**
     * Changes the value with the mouse wheel.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        if (deltaY == 0) {
            return false;
        }
        setValue(value + deltaY * SliderSupport.keyboardStep(minimum, maximum, step));
        return true;
    }

    /**
     * Adjusts the value with arrow, Home, End and Page keys.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key is consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        float keyboardStep = SliderSupport.keyboardStep(minimum, maximum, step);
        float pageStep = SliderSupport.pageStep(minimum, maximum);
        return switch (key) {
            case LEFT, DOWN -> {
                setValue(value - keyboardStep);
                yield true;
            }
            case RIGHT, UP -> {
                setValue(value + keyboardStep);
                yield true;
            }
            case PAGE_DOWN -> {
                setValue(value - pageStep);
                yield true;
            }
            case PAGE_UP -> {
                setValue(value + pageStep);
                yield true;
            }
            case HOME -> {
                setValue(minimum);
                yield true;
            }
            case END -> {
                setValue(maximum);
                yield true;
            }
            default -> false;
        };
    }

    private void drawArc(RenderManager renderer, Point center, float radius,
                         float startAngle, float sweepAngle, float width, Color color) {
        float length = Math.abs(sweepAngle) * radius;
        float spacing = Math.max(1.5f, width * 0.5f);
        int steps = Math.max(1, (int) Math.ceil(length / spacing));
        float dot = width / 2;
        Paint paint = Paint.solid(color);
        for (int index = 0; index <= steps; index++) {
            float angle = startAngle + sweepAngle * index / steps;
            renderer.fillCircle(new Point(center.x() + (float) Math.cos(angle) * radius,
                    center.y() + (float) Math.sin(angle) * radius), dot, paint);
        }
    }

    private void drawLabels(RenderManager renderer, Theme theme, Font font, Point center,
                            float radius) {
        if (font == null) {
            return;
        }
        Color color = isEnabled() ? theme.textMuted() : theme.textMuted().withAlpha(0.35f);
        String minText = SliderSupport.formatCompact(minimum);
        String maxText = SliderSupport.formatCompact(maximum);
        float offset = radius * ARC_DIAGONAL;
        float y = localBounds().height() - font.lineHeight();
        float minWidth = font.measure(minText).width();
        float maxWidth = font.measure(maxText).width();
        float minX = SliderSupport.clamp(center.x() - offset, minWidth / 2,
                localBounds().width() - minWidth / 2);
        float maxX = SliderSupport.clamp(center.x() + offset, maxWidth / 2,
                localBounds().width() - maxWidth / 2);
        renderer.text(font, minText, minX, y, color,
                HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        renderer.text(font, maxText, maxX, y, color,
                HorizontalAlignment.CENTER, VerticalAlignment.TOP);
    }

    private void drawValue(RenderManager renderer, Theme theme, Font font, Point center,
                           float bodyRadius) {
        if (font == null || bodyRadius <= 0) {
            return;
        }
        String text = SliderSupport.formatFull(value);
        float available = bodyRadius * 1.3f;
        if (font.measure(text).width() > available) {
            text = SliderSupport.formatCompact(value);
        }
        if (font.measure(text).width() > available * 1.6f) {
            return;
        }
        renderer.text(font, text, center.x(), center.y(),
                isEnabled() ? theme.text() : theme.textMuted().withAlpha(0.45f),
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
    }

    private void changeValue(float next) {
        float quantized = SliderSupport.quantize(next, minimum, maximum, step);
        if (quantized == value) {
            return;
        }
        value = quantized;
        if (onChange != null) {
            onChange.accept(value);
        }
    }
}
