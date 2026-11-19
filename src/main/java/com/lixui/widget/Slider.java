package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.theme.Theme;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A slider that selects a single value from a range.
 * The slider can be horizontal or vertical. The value changes by dragging the thumb or clicking
 * anywhere on the track, and the mouse wheel adjusts it over the hovered widget. While focused,
 * arrow keys adjust the value, Home and End select the range bounds, and Page Up and Page Down move
 * by a tenth of the range. A positive step snaps all values to the step grid.
 * Optional labels show the compact range bounds, and a floating tooltip shows the exact value near
 * the hovered label or thumb.
 */
public class Slider extends Widget {
    /** The axis along which the slider moves. */
    public enum Orientation {
        /** The value increases from left to right. */
        HORIZONTAL,
        /** The value increases from bottom to top. */
        VERTICAL
    }

    private static final float DEFAULT_PREFERRED_WIDTH = 160;
    private static final float DEFAULT_PREFERRED_LENGTH = 120;
    private static final float DEFAULT_VERTICAL_WIDTH = 48;
    private static final float DEFAULT_THUMB_SIZE = 16;
    private static final float DEFAULT_TRACK_THICKNESS = 4;

    private final SliderTooltip tooltip = new SliderTooltip();
    private Orientation orientation = Orientation.HORIZONTAL;
    private float minimum;
    private float maximum = 1;
    private float value;
    private float step;
    private float thumbSize = DEFAULT_THUMB_SIZE;
    private float trackThickness = DEFAULT_TRACK_THICKNESS;
    private boolean showRangeLabels = true;
    private int tickCount;
    private int hoveredEnd = -1;
    private Consumer<Float> onChange;

    /**
     * Creates a horizontal slider over the {@code [0, 1]} range with an initial value of zero.
     */
    public Slider() {
        setFocusable(true);
    }

    /**
     * Creates a slider with the supplied orientation over the {@code [0, 1]} range.
     *
     * @param orientation slider axis
     * @throws NullPointerException if {@code orientation} is null
     */
    public Slider(Orientation orientation) {
        this();
        setOrientation(orientation);
    }

    /**
     * Returns the slider orientation.
     *
     * @return the orientation
     */
    public final Orientation orientation() {
        return orientation;
    }

    /**
     * Replaces the slider orientation.
     *
     * @param orientation slider axis
     * @throws NullPointerException if {@code orientation} is null
     */
    public final void setOrientation(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
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
        SliderSupport.finite(value, "Slider value");
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
            throw new IllegalArgumentException("Slider step must be finite and non-negative.");
        }
        this.step = step;
        changeValue(value);
    }

    /**
     * Returns the thumb diameter.
     *
     * @return the thumb size in logical UI units
     */
    public final float thumbSize() {
        return thumbSize;
    }

    /**
     * Sets the thumb diameter.
     *
     * @param thumbSize positive diameter in logical UI units
     * @throws IllegalArgumentException if the size is not positive or is non-finite
     */
    public final void setThumbSize(float thumbSize) {
        if (!Float.isFinite(thumbSize) || thumbSize <= 0) {
            throw new IllegalArgumentException("Thumb size must be finite and positive.");
        }
        this.thumbSize = thumbSize;
    }

    /**
     * Returns the track thickness.
     *
     * @return the track thickness in logical UI units
     */
    public final float trackThickness() {
        return trackThickness;
    }

    /**
     * Sets the track thickness.
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
     * Returns the number of tick intervals.
     *
     * @return the interval count, or zero when ticks are hidden
     */
    public final int tickCount() {
        return tickCount;
    }

    /**
     * Draws the supplied number of evenly spaced tick intervals, including both track ends.
     * Zero hides the ticks.
     *
     * @param tickCount non-negative interval count
     * @throws IllegalArgumentException if the count is negative
     */
    public final void setTickCount(int tickCount) {
        if (tickCount < 0) {
            throw new IllegalArgumentException("Tick count must be non-negative.");
        }
        this.tickCount = tickCount;
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
     * Requests the pointer cursor for the slider.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Measures the slider, including the track, thumb and optional labels.
     *
     * @param constraints available and required dimensions
     * @return the natural slider size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        float lineRow = SliderSupport.labelRow(font, showRangeLabels);
        float minLabelWidth = 0;
        float maxLabelWidth = 0;
        if (showRangeLabels && font != null) {
            minLabelWidth = font.measure(SliderSupport.formatCompact(minimum)).width();
            maxLabelWidth = font.measure(SliderSupport.formatCompact(maximum)).width();
        }
        if (orientation == Orientation.HORIZONTAL) {
            float width = showRangeLabels && font != null
                    ? Math.max(DEFAULT_PREFERRED_WIDTH, minLabelWidth + maxLabelWidth + 12)
                    : DEFAULT_PREFERRED_WIDTH;
            return new Size(width, Math.max(thumbSize, trackThickness) + lineRow);
        }
        return new Size(Math.max(DEFAULT_VERTICAL_WIDTH,
                Math.max(thumbSize, Math.max(minLabelWidth, maxLabelWidth))),
                DEFAULT_PREFERRED_LENGTH + lineRow * 2);
    }

    /**
     * Advances the tooltip fade.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        tooltip.update(frame, isEnabled() && (isHovered() || isPressed()));
    }

    /**
     * Paints the ticks, track, filled portion, thumb, labels and value tooltip.
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
        Rect bounds = localBounds();
        Font font = font();
        boolean vertical = orientation == Orientation.VERTICAL;
        boolean labels = showRangeLabels && font != null;
        float labelRow = SliderSupport.labelRow(font, labels);
        float thumb = effectiveThumbSize();
        float radius = thumb / 2;
        if (radius <= 0) {
            return;
        }
        float center = trackCenter();
        float start = trackStart();
        float length = trackLength();
        float cross = vertical ? bounds.width() : Math.max(0, bounds.height() - labelRow);
        float track = Math.min(trackThickness, cross);
        SliderSupport.drawTicks(renderer, theme, vertical, isEnabled(),
                center, start, length, track, tickCount);
        float position = positionForValue(value);
        if (track > 0 && length > 0) {
            Color trackColor = SliderSupport.trackColor(theme, isEnabled());
            Color accentColor = SliderSupport.accentColor(theme, isEnabled());
            if (vertical) {
                renderer.roundedRect(new Rect(center - track / 2, start, track, length),
                        track / 2, trackColor);
                float filled = start + length - position;
                if (filled > 0) {
                    renderer.roundedRect(new Rect(center - track / 2, position, track, filled),
                            track / 2, accentColor);
                }
            } else {
                renderer.roundedRect(new Rect(start, center - track / 2, length, track),
                        track / 2, trackColor);
                float filled = position - start;
                if (filled > 0) {
                    renderer.roundedRect(new Rect(start, center - track / 2, filled, track),
                            track / 2, accentColor);
                }
            }
        }
        float thumbX = vertical ? center : position;
        float thumbY = vertical ? position : center;
        if (isFocused()) {
            float focusRadius = radius + 3;
            renderer.roundedRect(new Rect(thumbX - focusRadius, thumbY - focusRadius,
                    focusRadius * 2, focusRadius * 2), focusRadius,
                    theme.focusRing().withAlpha(0.3f));
        }
        SliderSupport.drawThumb(renderer, theme, isEnabled(), isHovered() || isPressed(),
                thumbX, thumbY, thumb);
        if (labels) {
            SliderSupport.drawLabels(renderer, font, theme, vertical, isEnabled(), bounds,
                    SliderSupport.formatCompact(minimum),
                    SliderSupport.formatCompact(maximum));
        }
        drawTooltip(renderer, theme, font, bounds, thumbX, thumbY, radius);
    }

    /**
     * Tracks the hovered range label and the pointer position.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @return false to keep bubbling pointer movement
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        updateHoveredEnd(x, y);
        return false;
    }

    /**
     * Moves the value to the pressed pointer position.
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
        updateHoveredEnd(x, y);
        setValue(valueAtPosition(axisPosition(x, y)));
        return true;
    }

    /**
     * Moves the value while the pointer is captured.
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
        hoveredEnd = -1;
        setValue(valueAtPosition(axisPosition(x, y)));
        return true;
    }

    /**
     * Consumes the primary release that ends a slider drag.
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
        updateHoveredEnd(x, y);
        float delta = orientation == Orientation.HORIZONTAL && deltaX != 0 ? deltaX : deltaY;
        if (delta == 0) {
            return false;
        }
        setValue(value + delta * SliderSupport.keyboardStep(minimum, maximum, step));
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

    private void drawTooltip(RenderManager renderer, Theme theme, Font font, Rect bounds,
                             float thumbX, float thumbY, float radius) {
        float alpha = tooltip.alpha();
        if (alpha <= 0.01f || font == null || !isEnabled()) {
            return;
        }
        float tooltipValue;
        float anchorX;
        float anchorTop;
        float anchorBottom;
        if (hoveredEnd >= 0 && showRangeLabels) {
            boolean minimumEnd = hoveredEnd == 0;
            String text = SliderSupport.formatCompact(minimumEnd ? minimum : maximum);
            Rect label = minimumEnd
                    ? SliderSupport.minLabelBounds(bounds, font, isVertical(), text)
                    : SliderSupport.maxLabelBounds(bounds, font, isVertical(), text);
            anchorX = label.x() + label.width() / 2;
            anchorTop = label.y();
            anchorBottom = label.y() + label.height();
            tooltipValue = minimumEnd ? minimum : maximum;
        } else {
            anchorX = isVertical() ? bounds.width() / 2 : thumbX;
            anchorTop = thumbY - radius;
            anchorBottom = thumbY + radius;
            tooltipValue = value;
        }
        SliderSupport.drawTooltip(renderer, font, SliderSupport.formatFull(tooltipValue),
                anchorX, anchorTop, anchorBottom, alpha, theme, bounds);
    }

    private void updateHoveredEnd(float x, float y) {
        Font font = font();
        if (!showRangeLabels || font == null) {
            hoveredEnd = -1;
            return;
        }
        Rect bounds = localBounds();
        boolean vertical = isVertical();
        String minText = SliderSupport.formatCompact(minimum);
        String maxText = SliderSupport.formatCompact(maximum);
        if (SliderSupport.minLabelBounds(bounds, font, vertical, minText).contains(x, y)) {
            hoveredEnd = 0;
        } else if (SliderSupport.maxLabelBounds(bounds, font, vertical, maxText).contains(x, y)) {
            hoveredEnd = 1;
        } else {
            hoveredEnd = -1;
        }
    }

    private boolean isVertical() {
        return orientation == Orientation.VERTICAL;
    }

    private float axisPosition(float x, float y) {
        return isVertical() ? y : x;
    }

    private float effectiveThumbSize() {
        Rect bounds = localBounds();
        float labelRow = SliderSupport.labelRow(font(), showRangeLabels);
        float availableHeight = isVertical()
                ? Math.max(0, bounds.height() - labelRow * 2)
                : Math.max(0, bounds.height() - labelRow);
        return Math.min(thumbSize, Math.min(bounds.width(), availableHeight));
    }

    private float trackCenter() {
        float labelRow = SliderSupport.labelRow(font(), showRangeLabels);
        return isVertical() ? localBounds().width() / 2 : (localBounds().height() - labelRow) / 2;
    }

    private float trackStart() {
        float thumb = effectiveThumbSize();
        if (!isVertical()) {
            return thumb / 2;
        }
        return SliderSupport.labelRow(font(), showRangeLabels) + thumb / 2;
    }

    private float trackLength() {
        float thumb = effectiveThumbSize();
        if (!isVertical()) {
            return Math.max(0, localBounds().width() - thumb);
        }
        float labelRow = SliderSupport.labelRow(font(), showRangeLabels);
        return Math.max(0, localBounds().height() - labelRow * 2 - thumb);
    }

    private float positionForValue(float sliderValue) {
        float fraction = SliderSupport.fraction(sliderValue, minimum, maximum);
        float start = trackStart();
        float length = trackLength();
        return isVertical() ? start + length * (1 - fraction) : start + length * fraction;
    }

    private float valueAtPosition(float position) {
        float length = trackLength();
        if (length <= 0) {
            return minimum;
        }
        float fraction = isVertical()
                ? (trackStart() + length - position) / length
                : (position - trackStart()) / length;
        return SliderSupport.quantize(
                SliderSupport.valueAt(fraction, minimum, maximum), minimum, maximum, step);
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
