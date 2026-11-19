package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;

import java.util.Objects;

/**
 * A one-dimensional container that stacks children along the main axis with an optional gap.
 * Children keep their measured main-axis size and are stretched across the cross axis by default.
 * Invisible children are ignored and keep their bounds.
 */
public abstract class Box extends LayoutContainer {
    /**
     * Placement of a child across the box's cross axis.
     */
    public enum CrossAlignment {
        /** Aligns the child with the start of the cross axis. */
        START,
        /** Centers the child across the cross axis. */
        CENTER,
        /** Aligns the child with the end of the cross axis. */
        END,
        /** Stretches the child across the available cross-axis size. */
        STRETCH
    }

    private float gap;
    private CrossAlignment crossAlignment = CrossAlignment.STRETCH;

    /**
     * Creates an empty box without a gap.
     */
    protected Box() {
    }

    /**
     * Reports whether children are stacked vertically.
     *
     * @return true for a vertical box, false for a horizontal box
     */
    protected abstract boolean vertical();

    /**
     * Returns the space between adjacent children.
     *
     * @return the gap in logical UI units
     */
    public final float gap() {
        return gap;
    }

    /**
     * Sets the space between adjacent children.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public final void setGap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Box gap must be finite and non-negative.");
        }
        this.gap = gap;
    }

    /**
     * Returns the placement of children across the cross axis.
     *
     * @return the cross-axis alignment
     */
    public final CrossAlignment crossAlignment() {
        return crossAlignment;
    }

    /**
     * Sets the placement of children across the cross axis.
     *
     * @param crossAlignment cross-axis alignment
     * @throws NullPointerException if the alignment is null
     */
    public final void setCrossAlignment(CrossAlignment crossAlignment) {
        this.crossAlignment = Objects.requireNonNull(crossAlignment, "crossAlignment");
    }

    /**
     * Measures the stacked children plus gaps and padding.
     * The main axis follows the sum of the children's natural sizes and the cross axis follows the
     * largest one.
     *
     * @param constraints available and required dimensions
     * @return the natural box size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = Math.max(0, constraints.maxWidth() - padding() * 2);
        float availableHeight = Math.max(0, constraints.maxHeight() - padding() * 2);
        float main = 0;
        float cross = 0;
        int visible = 0;
        for (Widget child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            Size size = child.measure(LayoutConstraints.loose(availableWidth, availableHeight));
            main += vertical() ? size.height() : size.width();
            cross = Math.max(cross, vertical() ? size.width() : size.height());
            visible++;
        }
        if (visible > 1) {
            main += gap * (visible - 1);
        }
        return vertical()
                ? new Size(cross + padding() * 2, main + padding() * 2)
                : new Size(main + padding() * 2, cross + padding() * 2);
    }

    /**
     * Places children in insertion order along the main axis.
     */
    @Override
    protected void onLayout() {
        float padding = padding();
        float contentWidth = Math.max(0, bounds().width() - padding * 2);
        float contentHeight = Math.max(0, bounds().height() - padding * 2);
        float position = padding;
        for (Widget child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            Size preferred = child.measure(LayoutConstraints.loose(contentWidth, contentHeight));
            if (vertical()) {
                float width = crossAlignment == CrossAlignment.STRETCH
                        ? contentWidth : Math.min(preferred.width(), contentWidth);
                float x = padding + crossOffset(contentWidth - width);
                child.layout(new Rect(x, position, width, preferred.height()));
                position += preferred.height() + gap;
            } else {
                float height = crossAlignment == CrossAlignment.STRETCH
                        ? contentHeight : Math.min(preferred.height(), contentHeight);
                float y = padding + crossOffset(contentHeight - height);
                child.layout(new Rect(position, y, preferred.width(), height));
                position += preferred.width() + gap;
            }
        }
    }

    private float crossOffset(float free) {
        return switch (crossAlignment) {
            case CENTER -> free / 2;
            case END -> free;
            default -> 0;
        };
    }
}
