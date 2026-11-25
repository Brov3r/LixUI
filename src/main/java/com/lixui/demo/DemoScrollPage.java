package com.lixui.demo;

import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.container.ScrollPanel;

import java.util.Objects;

/**
 * A scroll panel that sizes its page to the panel width and at least the panel height.
 * A page taller than the viewport keeps its natural height, so lower content stays reachable
 * through the scrollbar and the wheel; a shorter page fills the viewport, so growing children
 * receive the leftover space.
 */
final class DemoScrollPage extends ScrollPanel {
    private final Widget page;

    /**
     * Wraps a page in a vertically scrollable viewport.
     *
     * @param page page whose height defines the scroll range, filled to the viewport when shorter
     * @throws NullPointerException if {@code page} is null
     */
    DemoScrollPage(Widget page) {
        this.page = Objects.requireNonNull(page, "page");
        setContent(page);
    }

    /**
     * Sizes the page to the panel width and at least the panel height before scrolling it.
     */
    @Override
    protected void onLayout() {
        float width = Math.max(0, bounds().width());
        float height = Math.max(0, bounds().height());
        Size natural = page.measure(new LayoutConstraints(0, width, height, Float.MAX_VALUE));
        page.setSize(Math.max(width, natural.width()), natural.height());
        super.onLayout();
    }
}
