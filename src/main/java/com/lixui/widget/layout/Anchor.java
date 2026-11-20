package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

import java.util.Objects;

/**
 * A layout container that places a single child at one of nine positions.
 * The child keeps its measured size unless {@link #setFillX(boolean)} or
 * {@link #setFillY(boolean)} stretches it across the available content area. Padding insets the
 * content area from the container edges, and the container never blocks pointer input outside its
 * child. Auto-sizing measures the child plus padding, so the anchor wraps its content.
 */
public final class Anchor extends LayoutContainer {
    /**
     * Placement of the child inside the content area.
     */
    public enum Alignment {
        /** Places the child against the top and left edges. */
        TOP_LEFT,
        /** Places the child against the top edge and centers it horizontally. */
        TOP_CENTER,
        /** Places the child against the top and right edges. */
        TOP_RIGHT,
        /** Centers the child vertically and places it against the left edge. */
        CENTER_LEFT,
        /** Centers the child in the content area. */
        CENTER,
        /** Centers the child vertically and places it against the right edge. */
        CENTER_RIGHT,
        /** Places the child against the bottom and left edges. */
        BOTTOM_LEFT,
        /** Places the child against the bottom edge and centers it horizontally. */
        BOTTOM_CENTER,
        /** Places the child against the bottom and right edges. */
        BOTTOM_RIGHT
    }

    private Widget child;
    private Alignment alignment = Alignment.TOP_LEFT;
    private boolean fillX;
    private boolean fillY;

    /**
     * Creates an empty anchor with no padding and top-left alignment.
     */
    public Anchor() {
    }

    /**
     * Creates an empty anchor with the supplied alignment and no padding.
     *
     * @param alignment placement of the child inside the content area
     * @throws NullPointerException if {@code alignment} is null
     */
    public Anchor(Alignment alignment) {
        setAlignment(alignment);
    }

    /**
     * Returns the anchored child.
     *
     * @return the child, or null when the anchor is empty
     */
    public Widget child() {
        return child;
    }

    /**
     * Replaces the anchored child.
     * The new child is attached before the previous one is detached, so a failed attachment keeps
     * the current content in place.
     *
     * @param child widget to place inside the content area
     * @throws NullPointerException if {@code child} is null
     * @throws IllegalArgumentException if the child already has a parent or manager
     */
    public void setChild(Widget child) {
        Objects.requireNonNull(child, "child");
        if (child == this.child) {
            return;
        }
        add(child);
        Widget previous = this.child;
        this.child = child;
        if (previous != null) {
            remove(previous);
        }
    }

    /**
     * Removes the anchored child and leaves the anchor empty.
     */
    public void clearChild() {
        Widget previous = child;
        child = null;
        if (previous != null) {
            remove(previous);
        }
    }

    /**
     * Returns the placement of the child inside the content area.
     *
     * @return the alignment
     */
    public Alignment alignment() {
        return alignment;
    }

    /**
     * Sets the placement of the child inside the content area.
     *
     * @param alignment placement of the child
     * @throws NullPointerException if {@code alignment} is null
     */
    public void setAlignment(Alignment alignment) {
        this.alignment = Objects.requireNonNull(alignment, "alignment");
    }

    /**
     * Reports whether the child is stretched across the content width.
     *
     * @return true when the child width fills the content width
     */
    public boolean isFillX() {
        return fillX;
    }

    /**
     * Enables or disables stretching the child across the content width.
     * Horizontal alignment is ignored while filling.
     *
     * @param fillX true to stretch the child horizontally
     */
    public void setFillX(boolean fillX) {
        this.fillX = fillX;
    }

    /**
     * Reports whether the child is stretched across the content height.
     *
     * @return true when the child height fills the content height
     */
    public boolean isFillY() {
        return fillY;
    }

    /**
     * Enables or disables stretching the child across the content height.
     * Vertical alignment is ignored while filling.
     *
     * @param fillY true to stretch the child vertically
     */
    public void setFillY(boolean fillY) {
        this.fillY = fillY;
    }

    /**
     * Measures the anchored child plus padding.
     * Invisible children take no space, matching the other layout containers.
     *
     * @param constraints available and required dimensions
     * @return the natural anchor size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float padding = padding();
        if (child == null || !child.isVisible()) {
            return new Size(padding * 2, padding * 2);
        }
        Size size = child.measure(LayoutConstraints.loose(
                availableWidth(constraints), availableHeight(constraints)));
        return new Size(size.width() + padding * 2, size.height() + padding * 2);
    }

    /**
     * Aligns and sizes the child inside the padded content area.
     * Invisible children keep their current bounds.
     */
    @Override
    protected void onLayout() {
        if (child == null || !child.isVisible()) {
            return;
        }
        float contentWidth = contentWidth();
        float contentHeight = contentHeight();
        Size natural = child.measure(LayoutConstraints.loose(contentWidth, contentHeight));
        float width = fillX ? contentWidth : Math.min(natural.width(), contentWidth);
        float height = fillY ? contentHeight : Math.min(natural.height(), contentHeight);
        float x = padding() + horizontalOffset(contentWidth - width);
        float y = padding() + verticalOffset(contentHeight - height);
        child.layout(new Rect(x, y, width, height));
    }

    private float horizontalOffset(float free) {
        return switch (alignment) {
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> free / 2;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> free;
            default -> 0;
        };
    }

    private float verticalOffset(float free) {
        return switch (alignment) {
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> free / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> free;
            default -> 0;
        };
    }
}
