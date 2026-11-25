package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.LayoutContainer;

/**
 * A layout slot that pins one or both axes of a child while leaving the remaining axis natural.
 * The wrapper reports the pinned axes to its parent and stretches the child over the assigned area,
 * so cards keep a deterministic size inside boxes and grids.
 */
final class ShowcaseSized extends LayoutContainer {
    private static final float NATURAL = -1;

    private final Widget content;
    private final float width;
    private final float height;

    private ShowcaseSized(Widget content, float width, float height) {
        this.content = content;
        this.width = width;
        this.height = height;
        add(content);
    }

    static Widget width(Widget content, float width) {
        return new ShowcaseSized(content, width, NATURAL);
    }

    static Widget height(Widget content, float height) {
        return new ShowcaseSized(content, NATURAL, height);
    }

    static Widget size(Widget content, float width, float height) {
        return new ShowcaseSized(content, width, height);
    }

    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float slotWidth = width == NATURAL ? constraints.maxWidth() : width;
        float slotHeight = height == NATURAL ? constraints.maxHeight() : height;
        Size natural = content.measure(LayoutConstraints.loose(slotWidth, slotHeight));
        return new Size(width == NATURAL ? natural.width() : width,
                height == NATURAL ? natural.height() : height);
    }

    @Override
    protected void onLayout() {
        content.layout(new Rect(0, 0, contentWidth(), contentHeight()));
    }
}
