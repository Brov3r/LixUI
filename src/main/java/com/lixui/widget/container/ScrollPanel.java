package com.lixui.widget.container;

import com.lixui.widget.Widget;
import com.lixui.widget.Panel;
import com.lixui.widget.LayoutConstraints;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
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
    private static final float DEFAULT_BAR_MARGIN = 5;
    private static final float WHEEL_STEP = 48;
    private static final float SMOOTH_SCROLL_SECONDS = 0.18f;

    private final ScrollBar verticalBar = new ScrollBar(ScrollBar.Orientation.VERTICAL);
    private final ScrollBar horizontalBar = new ScrollBar(ScrollBar.Orientation.HORIZONTAL);
    private Widget content;
    private float scrollX;
    private float scrollY;
    private float targetScrollX;
    private float targetScrollY;
    private float scrollBarMargin = DEFAULT_BAR_MARGIN;
    private float contentInsetTop;
    private float contentInsetRight;
    private float contentInsetBottom;
    private float contentInsetLeft;
    private float scrollBarInsetTop;
    private float scrollBarInsetRight;
    private float scrollBarInsetBottom;
    private float scrollBarInsetLeft;
    private boolean verticalScrollBarVisible = true;
    private boolean horizontalScrollBarVisible = true;
    private boolean scrollBarsSuppressed;
    private boolean scrollEnabled = true;
    private final AnimationSlot horizontalAnimation = new AnimationSlot();
    private final AnimationSlot verticalAnimation = new AnimationSlot();

    /**
     * Creates an empty scroll panel with clipping enabled.
     */
    public ScrollPanel() {
        setClipToBounds(true);
        verticalBar.setValueListener(value -> {
            float target = value * maxScrollY();
            if (verticalBar.isPressed()) {
                setScroll(scrollX, target);
            } else {
                animateScrollTo(scrollX, target);
            }
        });
        horizontalBar.setValueListener(value -> {
            float target = value * maxScrollX();
            if (horizontalBar.isPressed()) {
                setScroll(target, scrollY);
            } else {
                animateScrollTo(target, scrollY);
            }
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
        if (content == this.content) {
            return;
        }
        // Validate and attach first so a failed replacement preserves the current content.
        add(content);
        remove(verticalBar);
        remove(horizontalBar);
        if (this.content != null) {
            remove(this.content);
        }
        cancelScrollAnimations();
        this.content = content;
        scrollX = 0;
        scrollY = 0;
        targetScrollX = 0;
        targetScrollY = 0;
        add(verticalBar);
        add(horizontalBar);
    }

    /**
     * Removes the content widget and hides the scrollbars.
     */
    public void clearContent() {
        cancelScrollAnimations();
        remove(verticalBar);
        remove(horizontalBar);
        if (content != null) {
            remove(content);
            content = null;
        }
        scrollX = 0;
        scrollY = 0;
        targetScrollX = 0;
        targetScrollY = 0;
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
     * @throws IllegalArgumentException if an offset is non-finite
     */
    public void setScroll(float scrollX, float scrollY) {
        if (!Float.isFinite(scrollX) || !Float.isFinite(scrollY)) {
            throw new IllegalArgumentException("Scroll offsets must be finite.");
        }
        cancelScrollAnimations();
        this.scrollX = Math.clamp(scrollX, 0, maxScrollX());
        this.scrollY = Math.clamp(scrollY, 0, maxScrollY());
        targetScrollX = this.scrollX;
        targetScrollY = this.scrollY;
        applyContentPosition();
        syncScrollbars();
    }

    /**
     * Scrolls the content by the supplied offsets.
     *
     * @param deltaX horizontal offset change
     * @param deltaY vertical offset change
     * @throws IllegalArgumentException if an offset or resulting position is non-finite
     */
    public void scrollBy(float deltaX, float deltaY) {
        setScroll(scrollX + deltaX, scrollY + deltaY);
    }

    /**
     * Sets the inset of the scrollable content area from this panel's edges.
     * The inset keeps fixed chrome outside the scrolled content and is used for scrollbar placement.
     *
     * @param top top inset in logical UI units
     * @param right right inset in logical UI units
     * @param bottom bottom inset in logical UI units
     * @param left left inset in logical UI units
     * @throws IllegalArgumentException if an inset is negative or non-finite
     */
    public void setContentInsets(float top, float right, float bottom, float left) {
        if (!Float.isFinite(top) || !Float.isFinite(right)
                || !Float.isFinite(bottom) || !Float.isFinite(left)
                || top < 0 || right < 0 || bottom < 0 || left < 0) {
            throw new IllegalArgumentException("Content insets must be finite and non-negative.");
        }
        contentInsetTop = top;
        contentInsetRight = right;
        contentInsetBottom = bottom;
        contentInsetLeft = left;
    }

    /**
     * Returns the top content inset.
     *
     * @return the top inset in logical UI units
     */
    public float contentInsetTop() {
        return contentInsetTop;
    }

    /**
     * Returns the right content inset.
     *
     * @return the right inset in logical UI units
     */
    public float contentInsetRight() {
        return contentInsetRight;
    }

    /**
     * Returns the bottom content inset.
     *
     * @return the bottom inset in logical UI units
     */
    public float contentInsetBottom() {
        return contentInsetBottom;
    }

    /**
     * Returns the left content inset.
     *
     * @return the left inset in logical UI units
     */
    public float contentInsetLeft() {
        return contentInsetLeft;
    }

    /**
     * Sets additional insets applied only to the scrollbar tracks.
     * This keeps scrollbars clear of resize handles or other fixed corner controls.
     *
     * @param top top scrollbar inset
     * @param right right scrollbar inset
     * @param bottom bottom scrollbar inset
     * @param left left scrollbar inset
     * @throws IllegalArgumentException if an inset is negative or non-finite
     */
    public void setScrollBarInsets(float top, float right, float bottom, float left) {
        if (!Float.isFinite(top) || !Float.isFinite(right)
                || !Float.isFinite(bottom) || !Float.isFinite(left)
                || top < 0 || right < 0 || bottom < 0 || left < 0) {
            throw new IllegalArgumentException("Scrollbar insets must be finite and non-negative.");
        }
        scrollBarInsetTop = top;
        scrollBarInsetRight = right;
        scrollBarInsetBottom = bottom;
        scrollBarInsetLeft = left;
    }

    /**
     * Hides the scrollbars without changing their configured visibility.
     *
     * @param suppressed true to suppress both scrollbars
     */
    protected void setScrollBarsSuppressed(boolean suppressed) {
        scrollBarsSuppressed = suppressed;
        verticalBar.setEnabled(scrollEnabled && !suppressed);
        horizontalBar.setEnabled(scrollEnabled && !suppressed);
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
        verticalBar.setEnabled(scrollEnabled && !scrollBarsSuppressed);
        horizontalBar.setEnabled(scrollEnabled && !scrollBarsSuppressed);
        if (!scrollEnabled) {
            cancelScrollAnimations();
            targetScrollX = scrollX;
            targetScrollY = scrollY;
        }
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
     * Measures the unclipped content plus the content insets, so an auto-sized panel wraps its
     * content instead of reserving the scrollbar thickness.
     *
     * @param constraints available and required dimensions
     * @return the natural panel size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = Math.max(0,
                constraints.maxWidth() - contentInsetLeft - contentInsetRight);
        float availableHeight = Math.max(0,
                constraints.maxHeight() - contentInsetTop - contentInsetBottom);
        Size contentSize = content == null ? new Size(0, 0)
                : content.measure(LayoutConstraints.loose(availableWidth, availableHeight));
        return new Size(contentSize.width() + contentInsetLeft + contentInsetRight,
                contentSize.height() + contentInsetTop + contentInsetBottom);
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
        scrollX = Math.clamp(scrollX, 0, maxScrollX());
        scrollY = Math.clamp(scrollY, 0, maxScrollY());
        targetScrollX = Math.clamp(targetScrollX, 0, maxScrollX());
        targetScrollY = Math.clamp(targetScrollY, 0, maxScrollY());
        applyContentPosition();
        updateScrollbars();
    }

    /**
     * Restricts content drawing and pointer input to the inset viewport.
     *
     * @param child direct child being drawn or hit-tested
     * @return the content viewport, or null for fixed controls
     */
    @Override
    protected Rect childClip(Widget child) {
        return child == content
                ? new Rect(contentInsetLeft, contentInsetTop, contentAreaWidth(), contentAreaHeight())
                : null;
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
        float nextX = targetScrollX;
        float nextY = targetScrollY;
        if (deltaY != 0) {
            if (maxY > 0) {
                nextY -= deltaY * WHEEL_STEP;
            } else if (maxX > 0) {
                nextX -= deltaY * WHEEL_STEP;
            }
        }
        if (deltaX != 0 && maxX > 0) {
            nextX += deltaX * WHEEL_STEP;
        }
        nextX = Math.clamp(nextX, 0, maxX);
        nextY = Math.clamp(nextY, 0, maxY);
        if (nextX == targetScrollX && nextY == targetScrollY) {
            return false;
        }
        animateScrollTo(nextX, nextY);
        return true;
    }

    private void animateScrollTo(float x, float y) {
        float targetX = Math.clamp(x, 0, maxScrollX());
        float targetY = Math.clamp(y, 0, maxScrollY());
        boolean changeX = targetX != targetScrollX || !horizontalAnimation.isActive();
        boolean changeY = targetY != targetScrollY || !verticalAnimation.isActive();
        targetScrollX = targetX;
        targetScrollY = targetY;
        Animator animator = context() == null ? null : context().animator();
        if (animator == null) {
            setScroll(targetX, targetY);
            return;
        }
        if (changeX) {
            horizontalAnimation.cancel();
        }
        if (changeX && scrollX != targetX) {
            horizontalAnimation.start(animator, scrollX, targetX, SMOOTH_SCROLL_SECONDS,
                    Easing.EASE_OUT, value -> {
                        scrollX = Math.clamp(value, 0, maxScrollX());
                        applyContentPosition();
                        syncScrollbars();
                    }, null);
        }
        if (changeY) {
            verticalAnimation.cancel();
        }
        if (changeY && scrollY != targetY) {
            verticalAnimation.start(animator, scrollY, targetY, SMOOTH_SCROLL_SECONDS,
                    Easing.EASE_OUT, value -> {
                        scrollY = Math.clamp(value, 0, maxScrollY());
                        applyContentPosition();
                        syncScrollbars();
                    }, null);
        }
    }

    private void cancelScrollAnimations() {
        horizontalAnimation.cancel();
        verticalAnimation.cancel();
    }

    private void updateScrollbars() {
        float width = contentAreaWidth();
        float height = contentAreaHeight();
        float contentWidth = content.bounds().width();
        float contentHeight = content.bounds().height();
        float maxX = maxScrollX();
        float maxY = maxScrollY();
        boolean horizontal = !scrollBarsSuppressed
                && scrollEnabled && horizontalScrollBarVisible && maxX > 0;
        boolean vertical = !scrollBarsSuppressed
                && scrollEnabled && verticalScrollBarVisible && maxY > 0;
        setVisibleIfChanged(horizontalBar, horizontal);
        setVisibleIfChanged(verticalBar, vertical);
        horizontalBar.setValue(maxX > 0 ? scrollX / maxX : 0);
        verticalBar.setValue(maxY > 0 ? scrollY / maxY : 0);
        horizontalBar.setVisibleFraction(contentWidth > 0 ? width / contentWidth : 1);
        verticalBar.setVisibleFraction(contentHeight > 0 ? height / contentHeight : 1);
        float verticalHeight = Math.max(0, height - scrollBarInsetTop - scrollBarInsetBottom
                - scrollBarMargin * 2 - (horizontal ? BAR_THICKNESS : 0));
        float horizontalWidth = Math.max(0, width - scrollBarInsetLeft - scrollBarInsetRight
                - scrollBarMargin * 2 - (vertical ? BAR_THICKNESS : 0));
        verticalBar.layout(new Rect(
                contentInsetLeft + width - scrollBarMargin - BAR_THICKNESS,
                contentInsetTop + scrollBarInsetTop + scrollBarMargin,
                BAR_THICKNESS, verticalHeight));
        horizontalBar.layout(new Rect(
                contentInsetLeft + scrollBarInsetLeft + scrollBarMargin,
                contentInsetTop + height - scrollBarMargin - BAR_THICKNESS,
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
            content.setPosition(contentInsetLeft - scrollX, contentInsetTop - scrollY);
        }
    }

    private float maxScrollX() {
        if (content == null) {
            return 0;
        }
        return Math.max(0, content.bounds().width() - contentAreaWidth());
    }

    private float maxScrollY() {
        if (content == null) {
            return 0;
        }
        return Math.max(0, content.bounds().height() - contentAreaHeight());
    }

    private float contentAreaWidth() {
        return Math.max(0, localBounds().width() - contentInsetLeft - contentInsetRight);
    }

    private float contentAreaHeight() {
        return Math.max(0, localBounds().height() - contentInsetTop - contentInsetBottom);
    }

    /**
     * Cancels smooth scrolling when the panel leaves the widget tree.
     */
    @Override
    protected void onDetached() {
        cancelScrollAnimations();
        targetScrollX = scrollX;
        targetScrollY = scrollY;
    }

}
