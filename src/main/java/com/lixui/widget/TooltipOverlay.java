package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.render.RenderManager;
import com.lixui.widget.tooltip.TooltipSettings;

/**
 * Presents the tooltip widget of the hovered subtree after a delay.
 * The tooltip is attached to the manager context while visible, fades in and out, slides next to the
 * pointer and is clamped to the viewport. It is drawn after the widget tree but is not part of it,
 * so it never takes hover, captures or blocks host input.
 */
final class TooltipOverlay {
    private static final float SLIDE_PIXELS = 4;

    private Widget owner;
    private Widget shown;
    private TooltipSettings active = TooltipSettings.defaults();
    private float dwellSeconds;
    private float opacity;
    private boolean leaving;

    /**
     * Advances the hover delay and the fade animation, then updates the visible tooltip widget.
     *
     * @param hovered widget under the pointer, or null
     * @param frame timing information for the current frame
     * @param pointerX pointer position in logical UI units
     * @param pointerY pointer position in logical UI units
     * @param defaults manager tooltip settings used when the owner has no override
     */
    void update(Widget hovered, FrameInfo frame, float pointerX, float pointerY,
                TooltipSettings defaults) {
        Widget nextOwner = tooltipOwner(hovered);
        Widget nextTooltip = nextOwner == null ? null : nextOwner.tooltip();
        if (nextOwner != owner || (shown != null && nextTooltip != shown)) {
            owner = nextOwner;
            dwellSeconds = 0;
            if (shown != null) {
                leaving = nextTooltip != shown;
            }
        }
        if (shown != null && owner != null && owner.tooltip() == shown) {
            active = settingsFor(owner, defaults);
        }
        float deltaSeconds = frame.deltaSeconds();
        if (shown != null && leaving) {
            opacity -= step(deltaSeconds, active.disappearSeconds());
            if (opacity <= 0) {
                detach();
            }
        }
        boolean started = false;
        if (shown == null && owner != null && nextTooltip != null
                && nextTooltip.parent() == null && owner.context() != null) {
            TooltipSettings settings = settingsFor(owner, defaults);
            dwellSeconds += deltaSeconds;
            if (dwellSeconds >= settings.delaySeconds()) {
                shown = nextTooltip;
                active = settings;
                opacity = 0;
                leaving = false;
                shown.attach(owner.context());
                started = true;
            }
        }
        if (shown != null) {
            if (!leaving && !started) {
                opacity = Math.min(1, opacity + step(deltaSeconds, active.appearSeconds()));
            }
            shown.update(frame);
        }
    }

    /**
     * Starts hiding the tooltip when the pointer moves to a subtree with a different tooltip owner.
     *
     * @param hovered widget under the pointer, or null
     */
    void hoverChanged(Widget hovered) {
        Widget nextOwner = tooltipOwner(hovered);
        if (nextOwner == owner) {
            return;
        }
        owner = nextOwner;
        dwellSeconds = 0;
        if (shown != null) {
            leaving = nextOwner == null || nextOwner.tooltip() != shown;
        }
    }

    /**
     * Draws the visible tooltip after the widget tree.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     * @param pointerX pointer position in logical UI units
     * @param pointerY pointer position in logical UI units
     */
    void draw(RenderManager renderer, FrameInfo frame, float pointerX, float pointerY) {
        if (shown == null || opacity <= 0.001f || shown.context() == null) {
            return;
        }
        Rect viewport = renderer.viewport();
        Size size = shown.measure(LayoutConstraints.loose(viewport.width(), viewport.height()));
        float slide = (1 - opacity) * SLIDE_PIXELS;
        float x = pointerX + active.offsetX();
        float y = pointerY + active.offsetY() + slide;
        if (x + size.width() > viewport.width()) {
            x = pointerX - active.offsetX() - size.width();
        }
        if (y + size.height() > viewport.height()) {
            y = pointerY - active.offsetY() - size.height() + slide;
        }
        x = Math.max(0, Math.min(x, Math.max(0, viewport.width() - size.width())));
        y = Math.max(0, Math.min(y, Math.max(0, viewport.height() - size.height())));
        shown.layout(new Rect(x, y, size.width(), size.height()));
        renderer.withOpacity(opacity, () -> shown.draw(renderer, frame));
    }

    /**
     * Hides and detaches the tooltip widget immediately.
     */
    void hide() {
        detach();
        owner = null;
        dwellSeconds = 0;
    }

    private void detach() {
        Widget previous = shown;
        shown = null;
        leaving = false;
        opacity = 0;
        if (previous != null) {
            previous.detach();
        }
    }

    private static TooltipSettings settingsFor(Widget owner, TooltipSettings defaults) {
        TooltipSettings override = owner.tooltipSettings();
        return override == null ? defaults : override;
    }

    private static float step(float deltaSeconds, float durationSeconds) {
        return durationSeconds <= 0 ? 1 : deltaSeconds / durationSeconds;
    }

    private static Widget tooltipOwner(Widget hovered) {
        for (Widget widget = hovered; widget != null; widget = widget.parent()) {
            if (!widget.isTooltipEnabled()) {
                return null;
            }
            if (widget.tooltip() != null) {
                return widget;
            }
        }
        return null;
    }
}
