package com.lixui.widget.control;

import com.lixui.widget.Widget;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
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
 * A slider that selects a low and a high value from a range.
 * The slider can be horizontal or vertical. Dragging either thumb adjusts that end of the range,
 * and the thumbs cannot cross. The mouse wheel moves the nearest thumb. While focused, arrow, Home,
 * End and Page keys adjust the thumb that was used last. A positive step snaps all values to the
 * step grid. Optional labels show the compact range bounds, and a floating tooltip shows the exact
 * value near the hovered label or thumb.
 */
public class RangeSlider extends Widget {
    /** The axis along which the slider moves. */
    public enum Orientation {
        /** Values increase from left to right. */
        HORIZONTAL,
        /** Values increase from bottom to top. */
        VERTICAL
    }

    private static final float DEFAULT_PREFERRED_WIDTH = 160;
    private static final float DEFAULT_PREFERRED_LENGTH = 120;
    private static final float DEFAULT_VERTICAL_WIDTH = 48;
    private static final float DEFAULT_THUMB_SIZE = 14;
    private static final float DEFAULT_TRACK_THICKNESS = 4;

    private final SliderTooltip tooltip = new SliderTooltip();
    private Orientation orientation = Orientation.HORIZONTAL;
    private float minimum;
    private float maximum = 1;
    private float lowValue;
    private float highValue = 1;
    private float step;
    private float thumbSize = DEFAULT_THUMB_SIZE;
    private float trackThickness = DEFAULT_TRACK_THICKNESS;
    private boolean showRangeLabels = true;
    private int tickCount;
    private Consumer<Range> onChange;
    private int activeThumb;
    private float dragOffset;
    private float pointerX;
    private float pointerY;

    /**
     * Creates a horizontal range slider over the {@code [0, 1]} range with both bounds selected.
     */
    public RangeSlider() {
        setFocusable(true);
    }

    /**
     * Creates a range slider with the supplied orientation over the {@code [0, 1]} range.
     *
     * @param orientation slider axis
     * @throws NullPointerException if {@code orientation} is null
     */
    public RangeSlider(Orientation orientation) {
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
     * Replaces the range and clamps both selected values into it.
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
        changeValues(lowValue, highValue);
    }

    /**
     * Returns the low selected value.
     *
     * @return the low value
     */
    public final float lowValue() {
        return lowValue;
    }

    /**
     * Selects the low value, clamping it to the range and the high value.
     * The change callback is invoked when the value actually changes.
     *
     * @param lowValue new low value
     * @throws IllegalArgumentException if the value is non-finite
     */
    public final void setLowValue(float lowValue) {
        SliderSupport.finite(lowValue, "Low value");
        changeValues(Math.clamp(lowValue, minimum, highValue), highValue);
    }

    /**
     * Returns the high selected value.
     *
     * @return the high value
     */
    public final float highValue() {
        return highValue;
    }

    /**
     * Selects the high value, clamping it to the range and the low value.
     * The change callback is invoked when the value actually changes.
     *
     * @param highValue new high value
     * @throws IllegalArgumentException if the value is non-finite
     */
    public final void setHighValue(float highValue) {
        SliderSupport.finite(highValue, "High value");
        changeValues(lowValue, Math.clamp(highValue, lowValue, maximum));
    }

    /**
     * Selects both values at once.
     * Values outside the range are clamped, and the low value must not exceed the high value.
     *
     * @param lowValue new low value
     * @param highValue new high value
     * @throws IllegalArgumentException if a value is non-finite, or the low value exceeds the high
     *                                  value after clamping
     */
    public final void setValues(float lowValue, float highValue) {
        SliderSupport.finite(lowValue, "Low value");
        SliderSupport.finite(highValue, "High value");
        float clampedLow = Math.clamp(lowValue, minimum, maximum);
        float clampedHigh = Math.clamp(highValue, minimum, maximum);
        if (clampedLow > clampedHigh) {
            throw new IllegalArgumentException("Low value must not exceed the high value.");
        }
        changeValues(clampedLow, clampedHigh);
    }

    /**
     * Returns both selected values.
     *
     * @return the selected range
     */
    public final Range range() {
        return new Range(lowValue, highValue);
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
     * Zero disables snapping. The current values are snapped immediately when the step changes.
     *
     * @param step non-negative step size
     * @throws IllegalArgumentException if the step is negative or non-finite
     */
    public final void setStep(float step) {
        if (!Float.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("Range slider step must be finite and non-negative.");
        }
        this.step = step;
        changeValues(lowValue, highValue);
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
     * Returns the callback invoked when either value changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public final Consumer<Range> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked when either value changes, including programmatic changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(Consumer<Range> onChange) {
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
     * Measures the slider, including the track, thumbs and optional labels.
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
     * Paints the ticks, track, selected span, thumbs, labels and value tooltip.
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
        SliderSupport.Geometry geometry = geometry();
        float thumb = geometry.effectiveThumbSize();
        float radius = thumb / 2;
        if (radius <= 0) {
            return;
        }
        float center = geometry.trackCenter();
        float start = geometry.trackStart();
        float length = geometry.trackLength();
        float cross = vertical ? bounds.width()
                : Math.max(0, bounds.height() - geometry.labelRow());
        float track = Math.min(trackThickness, cross);
        SliderSupport.drawTicks(renderer, theme, vertical, isEnabled(),
                center, start, length, track, tickCount);
        float lowPosition = geometry.positionForValue(lowValue, minimum, maximum);
        float highPosition = geometry.positionForValue(highValue, minimum, maximum);
        if (track > 0 && length > 0) {
            Color trackColor = SliderSupport.trackColor(theme, isEnabled());
            Color accentColor = SliderSupport.accentColor(theme, isEnabled());
            if (vertical) {
                renderer.roundedRect(new Rect(center - track / 2, start, track, length),
                        track / 2, trackColor);
                float filled = lowPosition - highPosition;
                if (filled > 0) {
                    renderer.roundedRect(new Rect(center - track / 2, highPosition,
                            track, filled), track / 2, accentColor);
                }
            } else {
                renderer.roundedRect(new Rect(start, center - track / 2, length, track),
                        track / 2, trackColor);
                float filled = highPosition - lowPosition;
                if (filled > 0) {
                    renderer.roundedRect(new Rect(lowPosition, center - track / 2,
                            filled, track), track / 2, accentColor);
                }
            }
        }
        if (isFocused()) {
            float focusPosition = activeThumb == 0 ? lowPosition : highPosition;
            float focusX = vertical ? center : focusPosition;
            float focusY = vertical ? focusPosition : center;
            float focusRadius = radius + 3;
            renderer.roundedRect(new Rect(focusX - focusRadius, focusY - focusRadius,
                    focusRadius * 2, focusRadius * 2), focusRadius,
                    theme.focusRing().withAlpha(0.3f));
        }
        int highlightedThumb = highlightedThumb();
        drawThumb(renderer, theme, vertical, center, lowPosition, thumb, highlightedThumb == 0);
        drawThumb(renderer, theme, vertical, center, highPosition, thumb, highlightedThumb == 1);
        if (labels) {
            SliderSupport.drawLabels(renderer, font, theme, vertical, isEnabled(), bounds,
                    SliderSupport.formatCompact(minimum),
                    SliderSupport.formatCompact(maximum));
        }
        drawTooltips(renderer, theme, font, bounds, vertical, center, lowPosition, highPosition,
                radius);
    }

    /**
     * Tracks the pointer position for nearest-thumb selection.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @return false to keep bubbling pointer movement
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        pointerX = x;
        pointerY = y;
        return false;
    }

    /**
     * Selects the nearest thumb and starts dragging it.
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
        pointerX = x;
        pointerY = y;
        SliderSupport.Geometry geometry = geometry();
        float position = geometry.axisPosition(x, y);
        float thumb = geometry.effectiveThumbSize();
        float lowPosition = geometry.positionForValue(lowValue, minimum, maximum);
        float highPosition = geometry.positionForValue(highValue, minimum, maximum);
        float lowDistance = Math.abs(position - lowPosition);
        float highDistance = Math.abs(position - highPosition);
        if (lowValue == highValue) {
            if (lowValue <= minimum) {
                activeThumb = 1;
            } else if (highValue >= maximum) {
                activeThumb = 0;
            } else {
                activeThumb = position >= lowPosition ? 1 : 0;
            }
        } else {
            activeThumb = lowDistance <= highDistance ? 0 : 1;
        }
        if (Math.min(lowDistance, highDistance) <= thumb) {
            dragOffset = (activeThumb == 0 ? lowPosition : highPosition) - position;
        } else {
            dragOffset = 0;
            setActiveValue(geometry.valueAtPosition(position, minimum, maximum, step));
        }
        return true;
    }

    /**
     * Moves the active thumb while the pointer is captured.
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
        pointerX = x;
        pointerY = y;
        SliderSupport.Geometry geometry = geometry();
        float target = geometry.valueAtPosition(geometry.axisPosition(x, y) + dragOffset,
                minimum, maximum, step);
        if (lowValue == highValue) {
            if (target > lowValue) {
                activeThumb = 1;
            } else if (target < highValue) {
                activeThumb = 0;
            }
        }
        setActiveValue(target);
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
     * Moves the nearest thumb with the mouse wheel.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        pointerX = x;
        pointerY = y;
        float delta = orientation == Orientation.HORIZONTAL && deltaX != 0 ? deltaX : deltaY;
        if (delta == 0) {
            return false;
        }
        activeThumb = nearestThumb(geometry().axisPosition(x, y));
        setActiveValue(activeValue() + delta * SliderSupport.keyboardStep(minimum, maximum, step));
        return true;
    }

    /**
     * Adjusts the active thumb with arrow, Home, End and Page keys.
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
        float activeValue = activeValue();
        return switch (key) {
            case LEFT, DOWN -> {
                if (lowValue == highValue) {
                    activeThumb = 0;
                }
                setActiveValue(activeValue - keyboardStep);
                yield true;
            }
            case RIGHT, UP -> {
                if (lowValue == highValue) {
                    activeThumb = 1;
                }
                setActiveValue(activeValue + keyboardStep);
                yield true;
            }
            case PAGE_DOWN -> {
                if (lowValue == highValue) {
                    activeThumb = 0;
                }
                setActiveValue(activeValue - pageStep);
                yield true;
            }
            case PAGE_UP -> {
                if (lowValue == highValue) {
                    activeThumb = 1;
                }
                setActiveValue(activeValue + pageStep);
                yield true;
            }
            case HOME -> {
                if (lowValue == highValue) {
                    activeThumb = 0;
                }
                setActiveValue(activeThumb == 0 ? minimum : lowValue);
                yield true;
            }
            case END -> {
                if (lowValue == highValue) {
                    activeThumb = 1;
                }
                setActiveValue(activeThumb == 0 ? highValue : maximum);
                yield true;
            }
            default -> false;
        };
    }

    private void drawThumb(RenderManager renderer, Theme theme, boolean vertical, float center,
                           float position, float thumb, boolean highlighted) {
        SliderSupport.drawThumb(renderer, theme, isEnabled(), highlighted,
                vertical ? center : position, vertical ? position : center, thumb);
    }

    private void drawTooltips(RenderManager renderer, Theme theme, Font font, Rect bounds,
                              boolean vertical, float center, float lowPosition,
                              float highPosition, float radius) {
        float alpha = tooltip.alpha();
        if (alpha <= 0.01f || font == null || !isEnabled()) {
            return;
        }
        drawThumbTooltip(renderer, theme, font, bounds, vertical, center, lowPosition, radius,
                lowValue, alpha);
        drawThumbTooltip(renderer, theme, font, bounds, vertical, center, highPosition, radius,
                highValue, alpha);
    }

    private void drawThumbTooltip(RenderManager renderer, Theme theme, Font font, Rect bounds,
                                  boolean vertical, float center, float position, float radius,
                                  float tooltipValue, float alpha) {
        float thumbX = vertical ? center : position;
        float thumbY = vertical ? position : center;
        float anchorX = vertical ? bounds.width() / 2 : thumbX;
        SliderSupport.drawTooltip(renderer, font, SliderSupport.formatFull(tooltipValue),
                anchorX, thumbY - radius, thumbY + radius, alpha, theme, bounds);
    }

    private int highlightedThumb() {
        if (isPressed() || isFocused()) {
            return activeThumb;
        }
        return isHovered() ? nearestThumb(geometry().axisPosition(pointerX, pointerY)) : -1;
    }

    private int nearestThumb(float position) {
        SliderSupport.Geometry geometry = geometry();
        float lowPosition = geometry.positionForValue(lowValue, minimum, maximum);
        float highPosition = geometry.positionForValue(highValue, minimum, maximum);
        return Math.abs(position - lowPosition) <= Math.abs(position - highPosition) ? 0 : 1;
    }

    private float activeValue() {
        return activeThumb == 0 ? lowValue : highValue;
    }

    private void setActiveValue(float next) {
        if (activeThumb == 0) {
            setLowValue(next);
        } else {
            setHighValue(next);
        }
    }

    private SliderSupport.Geometry geometry() {
        return new SliderSupport.Geometry(localBounds(), orientation == Orientation.VERTICAL,
                thumbSize, SliderSupport.labelRow(font(), showRangeLabels));
    }

    private void changeValues(float nextLow, float nextHigh) {
        float quantizedLow = SliderSupport.quantize(nextLow, minimum, maximum, step);
        float quantizedHigh = SliderSupport.quantize(nextHigh, minimum, maximum, step);
        if (quantizedLow == lowValue && quantizedHigh == highValue) {
            return;
        }
        lowValue = quantizedLow;
        highValue = quantizedHigh;
        if (onChange != null) {
            onChange.accept(range());
        }
    }

    /**
     * A pair of selected low and high values.
     *
     * @param low low selected value
     * @param high high selected value
     */
    public record Range(float low, float high) {
        /**
         * Creates a validated value range.
         *
         * @param low low selected value
         * @param high high selected value
         * @throws IllegalArgumentException if a value is non-finite or the low value exceeds the
         *                                  high value
         */
        public Range {
            if (!Float.isFinite(low) || !Float.isFinite(high) || low > high) {
                throw new IllegalArgumentException(
                        "Range values must be finite with low not exceeding high.");
            }
        }
    }
}
