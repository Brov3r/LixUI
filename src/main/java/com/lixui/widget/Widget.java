package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.RenderManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A retained UI node with bounds, children, drawing, logic and input hooks.
 * Coordinates are absolute logical UI units; adding a child does not translate its bounds.
 * Children are drawn after their parent and hit-tested in reverse insertion order, so the last
 * child is visually on top.
 */
public abstract class Widget {
    private final List<Widget> children = new ArrayList<>();
    private Widget parent;
    private Rect bounds = Rect.EMPTY;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean focusable;
    private boolean hovered;
    private boolean pressed;
    private boolean focused;

    /**
     * Creates a widget with empty bounds and no children.
     */
    protected Widget() {
    }

    /**
     * Returns the bounds assigned to this widget.
     *
     * @return the bounds in logical UI units
     */
    public final Rect bounds() {
        return bounds;
    }

    /**
     * Replaces the widget bounds.
     *
     * @param bounds new bounds in logical UI units
     * @throws NullPointerException if {@code bounds} is null
     */
    public final void setBounds(Rect bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    /**
     * Returns the parent widget.
     *
     * @return the parent, or null for a top-level widget
     */
    public final Widget parent() {
        return parent;
    }

    /**
     * Returns the children in drawing order, with later children drawn on top.
     *
     * @return an unmodifiable view of the children
     */
    public final List<Widget> children() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Appends a child widget.
     *
     * @param child widget to append
     * @return this widget
     * @throws NullPointerException if {@code child} is null
     * @throws IllegalArgumentException if the child already has a parent, is this widget, or is an ancestor
     */
    public final Widget add(Widget child) {
        Objects.requireNonNull(child, "child");
        if (child == this) {
            throw new IllegalArgumentException("A widget cannot contain itself.");
        }
        if (child.parent != null) {
            throw new IllegalArgumentException("Widget already has a parent.");
        }
        for (Widget ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) {
                throw new IllegalArgumentException("A widget cannot contain its ancestor.");
            }
        }
        children.add(child);
        child.setParent(this);
        return this;
    }

    /**
     * Removes a direct child widget and detaches it.
     *
     * @param child widget to remove
     * @return true when the child was present
     * @throws NullPointerException if {@code child} is null
     */
    public final boolean remove(Widget child) {
        Objects.requireNonNull(child, "child");
        for (int index = 0; index < children.size(); index++) {
            if (children.get(index) == child) {
                children.remove(index);
                child.setParent(null);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every child and detaches all of them.
     */
    public final void removeAll() {
        for (Widget child : children) {
            child.setParent(null);
        }
        children.clear();
    }

    /**
     * Reports whether this widget is drawn and hit-tested.
     *
     * @return true when the widget is visible
     */
    public final boolean isVisible() {
        return visible;
    }

    /**
     * Sets whether this widget is drawn and hit-tested.
     *
     * @param visible true to show the widget
     */
    public final void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Reports whether this widget accepts pointer and keyboard input.
     * Disabled widgets are still drawn, so subclasses can dim them.
     *
     * @return true when input is accepted
     */
    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this widget accepts pointer and keyboard input.
     *
     * @param enabled true to accept input
     */
    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Reports whether this widget can receive keyboard focus.
     *
     * @return true when the widget can be focused
     */
    public final boolean isFocusable() {
        return focusable;
    }

    /**
     * Sets whether this widget can receive keyboard focus.
     *
     * @param focusable true to allow focus
     */
    public final void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    /**
     * Reports whether the pointer is currently over this widget.
     *
     * @return true when the widget is hovered
     */
    public final boolean isHovered() {
        return hovered;
    }

    /**
     * Reports whether a pointer button is currently held on this widget.
     *
     * @return true while the widget is pressed
     */
    public final boolean isPressed() {
        return pressed;
    }

    /**
     * Reports whether this widget currently has keyboard focus.
     *
     * @return true when the widget is focused
     */
    public final boolean isFocused() {
        return focused;
    }

    /**
     * Reports whether a point can interact with this widget.
     * Invisible and disabled widgets never contain a point.
     *
     * @param x horizontal coordinate in logical UI units
     * @param y vertical coordinate in logical UI units
     * @return true when the widget is visible, enabled and contains the point
     */
    public final boolean contains(float x, float y) {
        return visible && enabled && bounds.contains(x, y);
    }

    /**
     * Returns the deepest visible and enabled widget containing the supplied point.
     * Children are tested in reverse drawing order.
     *
     * @param x horizontal coordinate in logical UI units
     * @param y vertical coordinate in logical UI units
     * @return the hit widget, or null when the point is outside
     */
    public Widget hitTest(float x, float y) {
        if (!contains(x, y)) {
            return null;
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            Widget hit = children.get(index).hitTest(x, y);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    /**
     * Updates this widget and its children.
     * Invisible widgets still receive updates, so animations and timers can continue.
     *
     * @param frame timing information for the current frame
     * @throws NullPointerException if {@code frame} is null
     */
    public final void update(FrameInfo frame) {
        Objects.requireNonNull(frame, "frame");
        onUpdate(frame);
        for (Widget child : children) {
            child.update(frame);
        }
    }

    /**
     * Draws this widget and its children into the supplied renderer.
     * Invisible widgets and their subtrees are skipped.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     * @throws NullPointerException if {@code renderer} or {@code frame} is null
     */
    public final void draw(RenderManager renderer, FrameInfo frame) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(frame, "frame");
        if (!visible) {
            return;
        }
        onDraw(renderer, frame);
        for (Widget child : children) {
            child.draw(renderer, frame);
        }
    }

    /**
     * Called once per frame before the children are updated.
     *
     * @param frame timing information for the current frame
     */
    protected void onUpdate(FrameInfo frame) {
    }

    /**
     * Paints this widget before its children are drawn.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
    }

    /**
     * Called when the pointer enters this widget.
     */
    protected void onPointerEnter() {
    }

    /**
     * Called when the pointer leaves this widget.
     */
    protected void onPointerExit() {
    }

    /**
     * Called for pointer movement over this widget or while it captures a pressed button.
     *
     * @param x horizontal pointer position in logical UI units
     * @param y vertical pointer position in logical UI units
     */
    protected void onPointerMove(float x, float y) {
    }

    /**
     * Called when a pointer button is pressed on this widget.
     *
     * @param x horizontal pointer position in logical UI units
     * @param y vertical pointer position in logical UI units
     * @param button pressed button
     * @param modifiers active keyboard modifiers
     */
    protected void onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
    }

    /**
     * Called when a captured pointer button is released.
     *
     * @param x horizontal pointer position in logical UI units
     * @param y vertical pointer position in logical UI units
     * @param button released button
     * @param modifiers active keyboard modifiers
     */
    protected void onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
    }

    /**
     * Called when the pointer scrolls while this widget is hovered.
     *
     * @param x horizontal pointer position in logical UI units
     * @param y vertical pointer position in logical UI units
     * @param deltaX horizontal scroll amount in wheel steps
     * @param deltaY vertical scroll amount in wheel steps
     */
    protected void onPointerScroll(float x, float y, float deltaX, float deltaY) {
    }

    /**
     * Called when a key is pressed while this widget is focused.
     *
     * @param key pressed key
     * @param modifiers active keyboard modifiers
     * @param repeat true for a repeated key press
     */
    protected void onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
    }

    /**
     * Called when a key is released while this widget is focused.
     *
     * @param key released key
     * @param modifiers active keyboard modifiers
     */
    protected void onKeyReleased(Key key, Modifiers modifiers) {
    }

    /**
     * Called when a Unicode code point is typed while this widget is focused.
     *
     * @param codepoint typed code point
     */
    protected void onCharTyped(int codepoint) {
    }

    /**
     * Called when keyboard focus is gained or lost.
     *
     * @param focused true when this widget gained focus
     */
    protected void onFocusChanged(boolean focused) {
    }

    final void setParent(Widget parent) {
        this.parent = parent;
    }

    final void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    final void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    final void setFocused(boolean focused) {
        this.focused = focused;
    }
}
