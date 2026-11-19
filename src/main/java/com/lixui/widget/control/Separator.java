package com.lixui.widget.control;

import com.lixui.widget.Widget;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;

import java.util.Objects;

/**
 * A thin line that visually separates groups of content.
 * The line runs along the configured orientation, keeps a fixed thickness and stretches along the
 * other axis. A horizontal separator therefore fills the available width and takes the thickness as
 * its height, while a vertical separator fills the available height and takes the thickness as its
 * width.
 * The line is painted with the resolved style border color unless an explicit color is assigned.
 */
public class Separator extends Widget {
    /** The direction of the separator line. */
    public enum Orientation {
        /** A horizontal line that stretches across the width. */
        HORIZONTAL,
        /** A vertical line that stretches across the height. */
        VERTICAL
    }

    private static final float DEFAULT_THICKNESS = 1;

    private Orientation orientation;
    private float thickness = DEFAULT_THICKNESS;
    private Color color;

    /**
     * Creates a horizontal separator with unit thickness.
     */
    public Separator() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a separator with the supplied orientation and unit thickness.
     *
     * @param orientation direction of the line
     * @throws NullPointerException if {@code orientation} is null
     */
    public Separator(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
    }

    /**
     * Returns the line orientation.
     *
     * @return the orientation
     */
    public final Orientation orientation() {
        return orientation;
    }

    /**
     * Replaces the line orientation.
     *
     * @param orientation direction of the line
     * @throws NullPointerException if {@code orientation} is null
     */
    public final void setOrientation(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
    }

    /**
     * Returns the line thickness.
     *
     * @return the thickness in logical UI units
     */
    public final float thickness() {
        return thickness;
    }

    /**
     * Replaces the line thickness.
     *
     * @param thickness positive thickness in logical UI units
     * @throws IllegalArgumentException if the thickness is not positive or is non-finite
     */
    public final void setThickness(float thickness) {
        if (!Float.isFinite(thickness) || thickness <= 0) {
            throw new IllegalArgumentException("Separator thickness must be finite and positive.");
        }
        this.thickness = thickness;
    }

    /**
     * Returns the explicit line color.
     *
     * @return the line color, or null when the style border color is used
     */
    public final Color color() {
        return color;
    }

    /**
     * Assigns an explicit line color.
     *
     * @param color line color, or null to use the style border color
     */
    public final void setColor(Color color) {
        this.color = color;
    }

    /**
     * Measures the line as a fixed thickness across the orientation and the available size along it.
     *
     * @param constraints available and required dimensions
     * @return the natural separator size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return orientation == Orientation.HORIZONTAL
                ? new Size(constraints.maxWidth(), thickness)
                : new Size(thickness, constraints.maxHeight());
    }

    /**
     * Paints the line centered across the assigned bounds.
     * A thickness larger than the assigned cross-axis size is clamped to keep the line visible.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        WidgetStyle style = style();
        Color lineColor = color != null ? color
                : style == null ? null : style.border(WidgetState.NORMAL);
        if (lineColor == null) {
            return;
        }
        Rect bounds = localBounds();
        if (orientation == Orientation.HORIZONTAL) {
            float height = Math.min(thickness, bounds.height());
            if (height > 0) {
                renderer.rect(new Rect(0, (bounds.height() - height) / 2, bounds.width(), height),
                        lineColor);
            }
        } else {
            float width = Math.min(thickness, bounds.width());
            if (width > 0) {
                renderer.rect(new Rect((bounds.width() - width) / 2, 0, width, bounds.height()),
                        lineColor);
            }
        }
    }
}
