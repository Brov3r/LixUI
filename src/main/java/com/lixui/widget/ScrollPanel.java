package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.render.RenderManager;
import com.lixui.theme.WidgetStyle;

import java.util.Objects;

/**
 * A clipped panel that scrolls a single content widget and shows minimal scrollbars when the
 * content overflows.
 * The content widget's size defines the scrollable area; its position is managed by this panel.
 * Use {@link #setContent(Widget)} and add child widgets to the content container.
 */
public class ScrollPanel extends Panel {
    private static final float BAR_THICKNESS = 8;
    private static final float DEFAULT_BAR_MARGIN = 3;
    private static final float WHEEL_STEP = 48;

    private final ScrollBar verticalBar = new ScrollBar(ScrollBar.Orientation.VERTICAL);
    private final ScrollBar horizontalBar = new ScrollBar(ScrollBar.Orientation.HORIZONTAL);
    private Widget content;
    private float scrollX;
    private float scrollY;
    private float scrollBarMargin = DEFAULT_BAR_MARGIN;
    private boolean verticalScrollBarVisible = true;
    private boolean horizontalScrollBarVisible = true;
    private boolean scrollEnabled = true;

    /**
     * Creates an empty scroll panel with clipping enabled.
     */
    public ScrollPanel() {
        setClipToBounds(true);
        verticalBar.setValueListener(value -> {
            scrollY = value * maxScrollY();
            applyContentPosition();
        });
        horizontalBar.setValueListener(value -> {
            scrollX = value * maxScrollX();
            applyContentPosition();
        });
    }

    /**
     * Returns the scrollable content widget.
     *
     * @return the content widget, or null when the panel is empty
     */
    public Widget content() {
        return content;
    }

    /**
     * Replaces the scrollable content widget and resets the scroll position.
     *
     * @param content content widget whose bounds define the scrollable size
     * @throws NullPointerException if {@code content} is null
     * @throws IllegalArgumentException if the content already has a parent or manager
     */
    public void setContent(Widget content) {
        Objects.requireNonNull(content, "content");
        if (content == this) {
            throw new IllegalArgumentException("A scroll panel cannot contain itself.");
        }
        if (content.parent() != null || content.context() != null) {
            throw new IllegalArgumentException("Content already belongs to a widget tree.");
        }
        remove(verticalBar);
        remove(horizontalBar);
        if (this.content != null) {
            remove(this.content);
        }
        this.content = content;
        scrollX = 0;
        scrollY = 0;
        add(content);
        add(verticalBar);
        add(horizontalBar);
    }

    /**
     * Removes the content widget and hides the scrollbars.
     */
    public void clearContent() {
        remove(verticalBar);
        remove(horizontalBar);
        if (content != null) {
            remove(content);
            content = null;
        }
        scrollX = 0;
        scrollY = 0;
    }

    /**
     * Returns the horizontal scroll offset.
     *
     * @return the offset in logical UI units
     */
    public float scrollX() {
        return scrollX;
    }

    /**
     * Returns the vertical scroll offset.
     *
     * @return the offset in logical UI units
     */
    public float scrollY() {
        return scrollY;
    }

    /**
     * Sets both scroll offsets, clamped to the available content range.
     *
     * @param scrollX horizontal offset
     * @param scrollY vertical offset
     */
    public void setScroll(float scrollX, float scrollY) {
        this.scrollX = clamp(scrollX, 0, maxScrollX());
        this.scrollY = clamp(scrollY, 0, maxScrollY());
        applyContentPosition();
        syncScrollbars();
    }

    /**
     * Scrolls the content by the supplied offsets.
     *
     * @param deltaX horizontal offset change
     * @param deltaY vertical offset change
     */
    public void scrollBy(float deltaX, float deltaY) {
        setScroll(scrollX + deltaX, scrollY + deltaY);
    }

    /**
     * Returns whether user scrolling is enabled.
     *
     * @return true when wheel and scrollbar dragging can change the scroll position
     */
    public boolean isScrollEnabled() {
        return scrollEnabled;
    }

    /**
     * Enables or disables wheel and scrollbar-drag scrolling.
     * Programmatic {@link #setScroll(float, float)} calls remain available.
     *
     * @param scrollEnabled true to allow user scrolling
     */
    public void setScrollEnabled(boolean scrollEnabled) {
        this.scrollEnabled = scrollEnabled;
    }

    /**
     * Returns whether the vertical scrollbar may be drawn.
     *
     * @return true when the vertical scrollbar is enabled
     */
    public boolean isVerticalScrollBarVisible() {
        return verticalScrollBarVisible;
    }

    /**
     * Shows or hides the vertical scrollbar. Overflow can still be scrolled with the wheel when
     * scrolling is enabled.
     *
     * @param visible true to show the vertical scrollbar
     */
    public void setVerticalScrollBarVisible(boolean visible) {
        this.verticalScrollBarVisible = visible;
    }

    /**
     * Returns whether the horizontal scrollbar may be drawn.
     *
     * @return true when the horizontal scrollbar is enabled
     */
    public boolean isHorizontalScrollBarVisible() {
        return horizontalScrollBarVisible;
    }

    /**
     * Shows or hides the horizontal scrollbar. Overflow can still be scrolled with the wheel when
     * scrolling is enabled.
     *
     * @param visible true to show the horizontal scrollbar
     */
    public void setHorizontalScrollBarVisible(boolean visible) {
        this.horizontalScrollBarVisible = visible;
    }

    /**
     * Returns the scrollbar inset from the panel edges.
     *
     * @return the inset in logical UI units
     */
    public float scrollBarMargin() {
        return scrollBarMargin;
    }

    /**
     * Sets the scrollbar inset from the panel edges.
     *
     * @param scrollBarMargin non-negative inset in logical UI units
     * @throws IllegalArgumentException if the margin is negative or non-finite
     */
    public void setScrollBarMargin(float scrollBarMargin) {
        if (!Float.isFinite(scrollBarMargin) || scrollBarMargin < 0) {
            throw new IllegalArgumentException("Scrollbar margin must be finite and non-negative.");
        }
        this.scrollBarMargin = scrollBarMargin;
    }

    /**
     * Lays out the content and positions the scrollbars.
     */
    @Override
    protected void onLayout() {
        if (content == null) {
            return;
        }
        Size size = content.size();
        content.layout(new Rect(0, 0, size.width(), size.height()));
        scrollX = clamp(scrollX, 0, maxScrollX());
        scrollY = clamp(scrollY, 0, maxScrollY());
        applyContentPosition();
        updateScrollbars();
    }

    /**
     * Clips children to the rounded panel shape.
     *
     * @param renderer drawing manager for the current frame
     * @return true when a rounded mask was pushed
     */
    @Override
    protected boolean pushChildMask(RenderManager renderer) {
        WidgetStyle style = style();
        if (!isClipToBounds() || style == null || style.cornerRadius() <= 0) {
            return false;
        }
        Rect bounds = localBounds();
        float inset = Math.min(style.borderWidth(),
                Math.min(bounds.width(), bounds.height()) / 2);
        Rect mask = new Rect(inset, inset, bounds.width() - inset * 2,
                bounds.height() - inset * 2);
        if (mask.isEmpty()) {
            return false;
        }
        renderer.pushMaskRoundedRect(mask, Math.max(0, style.cornerRadius() - inset));
        return true;
    }

    /**
     * Pops the rounded child mask.
     *
     * @param renderer drawing manager for the current frame
     */
    @Override
    protected void popChildMask(RenderManager renderer) {
        renderer.popMask();
    }

    /**
     * Handles wheel scrolling over the content.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        if (!scrollEnabled) {
            return false;
        }
        float maxX = maxScrollX();
        float maxY = maxScrollY();
        boolean scrolled = false;
        if (deltaY != 0) {
            if (maxY > 0) {
                scrollBy(0, -deltaY * WHEEL_STEP);
                scrolled = true;
            } else if (maxX > 0) {
                scrollBy(-deltaY * WHEEL_STEP, 0);
                scrolled = true;
            }
        }
        if (deltaX != 0 && maxX > 0) {
            scrollBy(deltaX * WHEEL_STEP, 0);
            scrolled = true;
        }
        return scrolled;
    }

    private void updateScrollbars() {
        float width = localBounds().width();
        float height = localBounds().height();
        float contentWidth = content.bounds().width();
        float contentHeight = content.bounds().height();
        float maxX = maxScrollX();
        float maxY = maxScrollY();
        boolean horizontal = scrollEnabled && horizontalScrollBarVisible && maxX > 0;
        boolean vertical = scrollEnabled && verticalScrollBarVisible && maxY > 0;
        setVisibleIfChanged(horizontalBar, horizontal);
        setVisibleIfChanged(verticalBar, vertical);
        horizontalBar.setValue(maxX > 0 ? scrollX / maxX : 0);
        verticalBar.setValue(maxY > 0 ? scrollY / maxY : 0);
        horizontalBar.setVisibleFraction(contentWidth > 0 ? width / contentWidth : 1);
        verticalBar.setVisibleFraction(contentHeight > 0 ? height / contentHeight : 1);
        float verticalHeight = Math.max(0,
                height - scrollBarMargin * 2 - (horizontal ? BAR_THICKNESS : 0));
        float horizontalWidth = Math.max(0,
                width - scrollBarMargin * 2 - (vertical ? BAR_THICKNESS : 0));
        verticalBar.layout(new Rect(width - scrollBarMargin - BAR_THICKNESS, scrollBarMargin,
                BAR_THICKNESS, verticalHeight));
        horizontalBar.layout(new Rect(scrollBarMargin, height - scrollBarMargin - BAR_THICKNESS,
                horizontalWidth, BAR_THICKNESS));
    }

    private void syncScrollbars() {
        float maxX = maxScrollX();
        float maxY = maxScrollY();
        horizontalBar.setValue(maxX > 0 ? scrollX / maxX : 0);
        verticalBar.setValue(maxY > 0 ? scrollY / maxY : 0);
    }

    private void applyContentPosition() {
        if (content != null) {
            content.setPosition(-scrollX, -scrollY);
        }
    }

    private float maxScrollX() {
        if (content == null) {
            return 0;
        }
        return Math.max(0, content.bounds().width() - localBounds().width());
    }

    private float maxScrollY() {
        if (content == null) {
            return 0;
        }
        return Math.max(0, content.bounds().height() - localBounds().height());
    }

    private static void setVisibleIfChanged(Widget widget, boolean visible) {
        if (widget.isVisible() != visible) {
            widget.setVisible(visible);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
