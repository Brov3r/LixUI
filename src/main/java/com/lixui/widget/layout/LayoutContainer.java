package com.lixui.widget.layout;

import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;

/**
 * A base for widgets that only arrange children and draw no surface of their own.
 * Layout containers apply uniform padding and are pointer-transparent: a point inside the container
 * but outside every child does not hit the container, so it neither takes hover nor blocks the host.
 * Subclasses may override {@link #hitTest(float, float)} to handle input over their whole area.
 */
public abstract class LayoutContainer extends Widget {
    private float padding;

    /**
     * Creates an empty layout container without padding.
     */
    protected LayoutContainer() {
    }

    /**
     * Returns the uniform padding between the container edges and its children.
     *
     * @return the padding in logical UI units
     */
    public final float padding() {
        return padding;
    }

    /**
     * Sets the uniform padding between the container edges and its children.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public final void setPadding(float padding) {
        if (!Float.isFinite(padding) || padding < 0) {
            throw new IllegalArgumentException("Layout padding must be finite and non-negative.");
        }
        this.padding = padding;
    }

    /**
     * Returns the width available to children after subtracting padding from a measurement limit.
     *
     * @param constraints measurement constraints
     * @return the non-negative content width
     */
    protected final float availableWidth(LayoutConstraints constraints) {
        return Math.max(0, constraints.maxWidth() - padding * 2);
    }

    /**
     * Returns the height available to children after subtracting padding from a measurement limit.
     *
     * @param constraints measurement constraints
     * @return the non-negative content height
     */
    protected final float availableHeight(LayoutConstraints constraints) {
        return Math.max(0, constraints.maxHeight() - padding * 2);
    }

    /**
     * Returns the padded content width of the current bounds.
     *
     * @return the non-negative content width
     */
    protected final float contentWidth() {
        return Math.max(0, bounds().width() - padding * 2);
    }

    /**
     * Returns the padded content height of the current bounds.
     *
     * @return the non-negative content height
     */
    protected final float contentHeight() {
        return Math.max(0, bounds().height() - padding * 2);
    }

    /**
     * Returns the deepest child containing the point, or null when only this container itself does.
     * The container never reports itself as a hit, which keeps empty layout space transparent to
     * pointer input and to {@link WidgetManager#blocksPointer(float, float)}.
     *
     * @param x horizontal coordinate relative to the container's top-left corner
     * @param y vertical coordinate relative to the container's top-left corner
     * @return the hit child, or null when no child contains the point
     */
    @Override
    public Widget hitTest(float x, float y) {
        Widget hit = super.hitTest(x, y);
        return hit == this ? null : hit;
    }
}
