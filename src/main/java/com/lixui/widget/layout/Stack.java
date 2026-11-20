package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

/**
 * A container that keeps children in the same place and relies on drawing order for stacking.
 * By default every child keeps its own bounds, so children overlap freely and the last child is
 * drawn on top. In fill mode every visible child is stretched over the padded content area, which
 * is convenient for layering an overlay above a base surface.
 * <p>
 * Invisible children keep their bounds. Padding only affects fill mode and the measured size in
 * fill mode, because manually positioned children keep their own bounds.
 */
public final class Stack extends LayoutContainer {
    private boolean fill;

    /**
     * Creates an empty stack in manual positioning mode.
     */
    public Stack() {
    }

    /**
     * Reports whether children are stretched over the padded content area.
     *
     * @return true in fill mode, false when children keep their own bounds
     */
    public boolean isFill() {
        return fill;
    }

    /**
     * Selects whether children keep their own bounds or are stretched over the content area.
     *
     * @param fill true to stretch every visible child over the padded content area
     */
    public void setFill(boolean fill) {
        this.fill = fill;
    }

    /**
     * Measures the extent of manually positioned children or the largest visible child plus
     * padding in fill mode.
     *
     * @param constraints available and required dimensions
     * @return the natural stack size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = availableWidth(constraints);
        float availableHeight = availableHeight(constraints);
        float width = 0;
        float height = 0;
        for (Widget child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            Size size = child.measure(LayoutConstraints.loose(availableWidth, availableHeight));
            if (fill) {
                width = Math.max(width, size.width());
                height = Math.max(height, size.height());
            } else {
                width = Math.max(width, child.bounds().x() + size.width());
                height = Math.max(height, child.bounds().y() + size.height());
            }
        }
        float inset = fill ? padding() * 2 : 0;
        return new Size(width + inset, height + inset);
    }

    /**
     * Lays out every visible child either at its own bounds or over the padded content area.
     */
    @Override
    protected void onLayout() {
        if (!fill) {
            for (Widget child : children()) {
                if (child.isVisible()) {
                    child.layout(child.bounds());
                }
            }
            return;
        }
        Rect content = new Rect(padding(), padding(), contentWidth(), contentHeight());
        for (Widget child : children()) {
            if (child.isVisible()) {
                child.layout(content);
            }
        }
    }
}
