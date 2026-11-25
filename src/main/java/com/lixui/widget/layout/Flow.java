package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * A container that places children left to right and wraps them onto a new line when they no
 * longer fit. Children keep their natural size, so the container never stretches them across a
 * line; {@link Widget#grow()} is ignored. Each line is as tall as its tallest child, and the gap is
 * applied both between children on a line and between lines.
 * <p>
 * When an axis has no space, the layout keeps the current child bounds instead of shrinking every
 * child to zero, so preset sizes survive transient hidden or collapsed layouts. Invisible children
 * take no space and keep their bounds.
 */
public final class Flow extends LayoutContainer {
    private float gap;

    /**
     * Creates an empty flow container without a gap.
     */
    public Flow() {
    }

    /**
     * Returns the gap between adjacent children and lines.
     *
     * @return the gap in logical UI units
     */
    public float gap() {
        return gap;
    }

    /**
     * Sets the gap between adjacent children and lines.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public void setGap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Flow gap must be finite and non-negative.");
        }
        this.gap = gap;
    }

    /**
     * Measures the wrapped lines using the available width.
     * The natural width is the widest line and the natural height is the sum of all line heights
     * plus the gaps between lines.
     *
     * @param constraints available and required dimensions
     * @return the natural flow size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = availableWidth(constraints);
        float availableHeight = availableHeight(constraints);
        float lineWidth = 0;
        float lineHeight = 0;
        float widestLine = 0;
        float totalHeight = 0;
        boolean lineEmpty = true;
        for (Widget child : visibleChildren()) {
            Size size = child.measure(LayoutConstraints.loose(availableWidth, availableHeight));
            if (!lineEmpty && lineWidth + gap + size.width() > availableWidth) {
                widestLine = Math.max(widestLine, lineWidth);
                totalHeight += lineHeight + gap;
                lineWidth = 0;
                lineHeight = 0;
                lineEmpty = true;
            }
            if (!lineEmpty) {
                lineWidth += gap;
            }
            lineWidth += size.width();
            lineHeight = Math.max(lineHeight, size.height());
            lineEmpty = false;
        }
        widestLine = Math.max(widestLine, lineWidth);
        totalHeight += lineHeight;
        return new Size(widestLine + padding() * 2, totalHeight + padding() * 2);
    }

    /**
     * Places children left to right inside the padded content area and wraps them when the next
     * child does not fit on the current line.
     */
    @Override
    protected void onLayout() {
        float contentWidth = contentWidth();
        float contentHeight = contentHeight();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return;
        }
        float x = 0;
        float y = 0;
        float lineHeight = 0;
        boolean lineEmpty = true;
        for (Widget child : visibleChildren()) {
            Size size = child.measure(LayoutConstraints.loose(contentWidth, contentHeight));
            if (!lineEmpty && x + gap + size.width() > contentWidth) {
                x = 0;
                y += lineHeight + gap;
                lineHeight = 0;
                lineEmpty = true;
            }
            if (!lineEmpty) {
                x += gap;
            }
            child.layout(new Rect(padding() + x, padding() + y, size.width(), size.height()));
            x += size.width();
            lineHeight = Math.max(lineHeight, size.height());
            lineEmpty = false;
        }
    }

    private List<Widget> visibleChildren() {
        List<Widget> visible = new ArrayList<>();
        for (Widget child : children()) {
            if (child.isVisible()) {
                visible.add(child);
            }
        }
        return visible;
    }
}
