package com.lixui.demo;

import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.container.ScrollPanel;

import java.util.Objects;

/**
 * A scroll panel that sizes its page to the panel width and the page's natural height.
 * The retained height makes lower page content reachable through the scrollbar and the wheel.
 */
final class DemoScrollPage extends ScrollPanel {
    private final Widget page;

    /**
     * Wraps a page in a vertically scrollable viewport.
     *
     * @param page page whose natural height defines the scroll range
     * @throws NullPointerException if {@code page} is null
     */
    DemoScrollPage(Widget page) {
        this.page = Objects.requireNonNull(page, "page");
        setContent(page);
    }

    /**
     * Sizes the page to the panel width and its natural height before scrolling it.
     */
    @Override
    protected void onLayout() {
        float width = Math.max(0, bounds().width());
        Size natural = page.measure(LayoutConstraints.loose(width, Float.MAX_VALUE));
        page.setSize(Math.max(width, natural.width()), natural.height());
        super.onLayout();
    }
}
