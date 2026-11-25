package com.lixui.demo;

import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.container.ScrollPanel;

import java.util.Objects;

/**
 * A scroll panel that sizes its content to the panel width before scrolling.
 * The default ScrollPanel treats the content bounds as already resolved; this variant measures the
 * page with the viewport width so wrapped labels reflow instead of overlapping.
 */
final class ShowcaseScroll extends ScrollPanel {
    private static final float SCROLLBAR_SPACE = 10;

    private final Widget page;

    ShowcaseScroll(Widget page) {
        this.page = Objects.requireNonNull(page, "page");
        setContent(page);
    }

    @Override
    protected void onLayout() {
        float width = Math.max(1, bounds().width() - SCROLLBAR_SPACE);
        float height = Math.max(0, bounds().height());
        Size natural = page.measure(new LayoutConstraints(0, width, height, Float.MAX_VALUE));
        page.setSize(width, Math.max(height, natural.height()));
        super.onLayout();
    }
}
