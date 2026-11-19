package com.lixui.widget.container;

import com.lixui.widget.Widget;
import com.lixui.widget.FrameInfo;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A minimal draggable scrollbar track with a normalized thumb position.
 * The thumb size reflects the visible fraction of the content. The scrollbar consumes pointer
 * drags and wheel input, but does not own or clip any content itself; use {@link ScrollPanel} for
 * a scrollable container.
 */
public class ScrollBar extends Widget {
    /** The axis along which the scrollbar moves. */
    public enum Orientation {
        /** A vertical bar with a thumb that moves along the Y axis. */
        VERTICAL,
        /** A horizontal bar with a thumb that moves along the X axis. */
        HORIZONTAL
    }

    private static final float MIN_THUMB_LENGTH = 24;
    private static final float WHEEL_FRACTION = 0.1f;

    private final Orientation orientation;
    private Consumer<Float> valueListener;
    private float value;
    private float visibleFraction = 1;
    private float dragOffset;

    /**
     * Creates a scrollbar for the supplied orientation.
     *
     * @param orientation scrollbar axis
     * @throws NullPointerException if {@code orientation} is null
     */
    public ScrollBar(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
    }

    /**
     * Returns the scrollbar axis.
     *
     * @return the orientation
     */
    public Orientation orientation() {
        return orientation;
    }

    /**
     * Returns the normalized thumb position.
     *
     * @return a value in the {@code [0, 1]} range
     */
    public float value() {
        return value;
    }

    /**
     * Sets the normalized thumb position without invoking the value listener.
     *
     * @param value thumb position, clamped to {@code [0, 1]}
     * @throws IllegalArgumentException if the position is non-finite
     */
    public void setValue(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Scrollbar value must be finite.");
        }
        this.value = Math.clamp(value, 0, 1);
    }

    /**
     * Returns the fraction of the content that is visible.
     *
     * @return a value in the {@code [0, 1]} range
     */
    public float visibleFraction() {
        return visibleFraction;
    }

    /**
     * Sets the visible content fraction, which controls the thumb length.
     *
     * @param visibleFraction visible fraction, clamped to {@code [0, 1]}
     * @throws IllegalArgumentException if the fraction is non-finite
     */
    public void setVisibleFraction(float visibleFraction) {
        if (!Float.isFinite(visibleFraction)) {
            throw new IllegalArgumentException("Visible fraction must be finite.");
        }
        this.visibleFraction = Math.clamp(visibleFraction, 0, 1);
    }

    /**
     * Returns the callback invoked when the user moves the scrollbar.
     *
     * @return the value listener, or null when none is assigned
     */
    public Consumer<Float> valueListener() {
        return valueListener;
    }

    /**
     * Assigns a callback invoked when the user moves the scrollbar.
     * Programmatic {@link #setValue(float)} calls do not invoke the listener.
     *
     * @param valueListener value listener, or null to clear it
     */
    public void setValueListener(Consumer<Float> valueListener) {
        this.valueListener = valueListener;
    }

    /**
     * Paints the scrollbar track and thumb.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (context() == null || localBounds().isEmpty() || visibleFraction >= 1) {
            return;
        }
        Theme theme = context().theme();
        float thickness = orientation == Orientation.VERTICAL
                ? localBounds().width() : localBounds().height();
        float radius = thickness / 2;
        float thumb = thumbLength();
        float start = thumbStart(thumb);
        Color thumbColor = !isEnabled() ? theme.textMuted().withAlpha(0.25f)
                : isPressed() ? theme.textMuted().withAlpha(0.85f)
                : isHovered() ? theme.textMuted().withAlpha(0.65f)
                : theme.textMuted().withAlpha(0.45f);
        renderer.roundedRect(localBounds(), radius, theme.border().withAlpha(0.35f));
        Rect thumbBounds = orientation == Orientation.VERTICAL
                ? new Rect(0, start, localBounds().width(), thumb)
                : new Rect(start, 0, thumb, localBounds().height());
        renderer.roundedRect(thumbBounds, radius, thumbColor);
    }

    /**
     * Scrolls the scrollbar with the mouse wheel.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        if (visibleFraction >= 1) {
            return false;
        }
        float delta = orientation == Orientation.VERTICAL
                ? -deltaY : (deltaX != 0 ? deltaX : -deltaY);
        if (delta == 0) {
            return false;
        }
        float next = Math.clamp(value + delta * WHEEL_FRACTION, 0, 1);
        if (next == value) {
            return false;
        }
        changeValue(next);
        return true;
    }

    /**
     * Starts a thumb drag or jumps to the pressed track position.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press is consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT || visibleFraction >= 1) {
            return false;
        }
        float position = orientation == Orientation.VERTICAL ? y : x;
        float thumb = thumbLength();
        float start = thumbStart(thumb);
        dragOffset = position >= start && position <= start + thumb
                ? position - start : thumb / 2;
        capturePointer(button);
        moveThumbTo(position);
        return true;
    }

    /**
     * Moves the thumb while the pointer is captured.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button dragged button
     * @param modifiers active modifiers
     * @return true when the drag is consumed
     */
    @Override
    protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        moveThumbTo(orientation == Orientation.VERTICAL ? y : x);
        return true;
    }

    private void moveThumbTo(float position) {
        float track = trackLength();
        float thumb = thumbLength();
        float range = track - thumb;
        changeValue(range <= 0 ? 0 : (position - dragOffset) / range);
    }

    private float trackLength() {
        return orientation == Orientation.VERTICAL ? localBounds().height() : localBounds().width();
    }

    private float thumbLength() {
        float track = trackLength();
        return Math.max(Math.min(MIN_THUMB_LENGTH, track), track * visibleFraction);
    }

    private float thumbStart(float thumb) {
        return (trackLength() - thumb) * value;
    }

    private void changeValue(float next) {
        float clamped = Math.clamp(next, 0, 1);
        if (clamped == value) {
            return;
        }
        value = clamped;
        if (valueListener != null) {
            valueListener.accept(value);
        }
    }

}
