package com.lixui.widget;

import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;

/**
 * An active drag gesture shared through {@link UiContext}.
 * A session stores the source widget, the initiating pointer button, the payload kind and the
 * payload object, follows the pointer and reports the drop zone currently under it. The session
 * ends when its button is released, the gesture is cancelled or the source is detached; after that
 * {@link #isActive()} returns false.
 * Sessions are created by {@link UiContext#startDrag(Widget, MouseButton, String, Object, String)}.
 */
public final class DragSession {
    private final Widget source;
    private final MouseButton button;
    private final String kind;
    private final Object payload;
    private float x;
    private float y;
    private DropZone target;
    private boolean active = true;
    private boolean handled;
    private boolean dropAttempted;

    DragSession(Widget source, MouseButton button, String kind, Object payload) {
        this.source = source;
        this.button = button;
        this.kind = kind;
        this.payload = payload;
    }

    /**
     * Returns the widget that started the drag.
     *
     * @return the drag source
     */
    public Widget source() {
        return source;
    }

    /**
     * Returns the pointer button that started the gesture.
     * Only releases and cancellations for this button may finish the session.
     *
     * @return the initiating pointer button
     */
    public MouseButton button() {
        return button;
    }

    /**
     * Returns the payload kind used by drop zones to accept or reject the drag.
     *
     * @return the non-null payload kind
     */
    public String kind() {
        return kind;
    }

    /**
     * Returns the value carried by the drag.
     *
     * @return the payload object, or null when the source carries no value
     */
    public Object payload() {
        return payload;
    }

    /**
     * Returns the last horizontal pointer position in viewport coordinates.
     *
     * @return the pointer x coordinate in logical UI units
     */
    public float x() {
        return x;
    }

    /**
     * Returns the last vertical pointer position in viewport coordinates.
     *
     * @return the pointer y coordinate in logical UI units
     */
    public float y() {
        return y;
    }

    /**
     * Returns the drop zone under the pointer, even when it rejects the payload kind.
     *
     * @return the current drop zone, or null when the pointer is outside every zone
     */
    public DropZone target() {
        return target;
    }

    /**
     * Reports whether the gesture is still running.
     *
     * @return true until the drag is dropped, cancelled or the source is detached
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Reports whether the current target accepts the payload kind.
     * A target that became hidden or disabled mid-drag no longer accepts anything, because a drop
     * would be skipped by hit testing.
     *
     * @return true when a reachable drop zone is under the pointer and accepts {@link #kind()}
     */
    public boolean isAccepted() {
        return active && target != null && target.isReachable() && target.accepts(kind);
    }

    /**
     * Reports whether a drop zone handled the completed drop.
     * The value stays available after the session ends so the source can inspect it from
     * {@link Widget#onDragEnd(float, float, MouseButton, Modifiers)}.
     *
     * @return true when the drop callback returned true
     */
    public boolean isHandled() {
        return handled;
    }

    void move(float x, float y, DropZone target) {
        this.x = x;
        this.y = y;
        this.target = target;
    }

    boolean beginDrop() {
        if (!active || dropAttempted) {
            return false;
        }
        dropAttempted = true;
        return true;
    }

    void markHandled(boolean handled) {
        this.handled = handled;
    }

    void finish() {
        active = false;
        target = null;
    }
}
