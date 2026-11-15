package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.input.CharTyped;
import com.lixui.input.FocusChanged;
import com.lixui.input.InputEvent;
import com.lixui.input.InputState;
import com.lixui.input.KeyPressed;
import com.lixui.input.KeyReleased;
import com.lixui.input.MouseButton;
import com.lixui.input.PointerEntered;
import com.lixui.input.PointerExited;
import com.lixui.input.PointerMoved;
import com.lixui.input.PointerPressed;
import com.lixui.input.PointerReleased;
import com.lixui.input.PointerScrolled;
import com.lixui.render.Antialiasing;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Viewport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registers an input queue and a render backend, owns top-level widgets, and drives their logic
 * and drawing.
 * Widgets added to this manager have no parent. Add a widget to another widget to build a hierarchy.
 * Top-level widgets are drawn in insertion order and hit-tested in reverse order, so the last
 * widget is visually on top. Instances are single-threaded and are intended to be updated once
 * per frame.
 */
public final class WidgetManager {
    private final InputState input;
    private final RenderManager renderer;
    private final List<Widget> widgets = new ArrayList<>();
    private final EnumMap<MouseButton, Widget> captures = new EnumMap<>(MouseButton.class);
    private Widget hovered;
    private Widget focused;
    private FrameInfo frame = FrameInfo.ZERO;
    private float pointerX;
    private float pointerY;
    private boolean pointerInside;

    /**
     * Creates a manager for the supplied input queue and backend.
     *
     * @param input queue drained by {@link #update(float)}
     * @param backend backend that receives completed draw lists
     * @throws NullPointerException if {@code input} or {@code backend} is null
     */
    public WidgetManager(InputState input, RenderBackend backend) {
        this.input = Objects.requireNonNull(input, "input");
        renderer = new RenderManager(Objects.requireNonNull(backend, "backend"));
    }

    /**
     * Returns the drawing manager used by widgets and custom draw code.
     *
     * @return the render manager registered with this widget manager
     */
    public RenderManager renderer() {
        return renderer;
    }

    /**
     * Returns the current antialiasing setting used by widget drawing.
     *
     * @return the current antialiasing setting
     */
    public Antialiasing antialiasing() {
        return renderer.antialiasing();
    }

    /**
     * Sets tessellation quality and geometry feathering for widget drawing.
     * Smoothing does not depend on MSAA being enabled in the host window.
     *
     * @param antialiasing antialiasing setting
     * @throws NullPointerException if {@code antialiasing} is null
     */
    public void setAntialiasing(Antialiasing antialiasing) {
        renderer.setAntialiasing(antialiasing);
    }

    /**
     * Returns the top-level widgets in drawing order.
     *
     * @return an unmodifiable view of the top-level widgets
     */
    public List<Widget> widgets() {
        return Collections.unmodifiableList(widgets);
    }

    /**
     * Adds a top-level widget.
     * The widget must not already have a parent.
     *
     * @param widget widget to add
     * @return this manager
     * @throws NullPointerException if {@code widget} is null
     * @throws IllegalArgumentException if the widget has a parent or is already registered
     */
    public WidgetManager add(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        if (widget.parent() != null) {
            throw new IllegalArgumentException("Only widgets without a parent can be added.");
        }
        if (containsIdentity(widgets, widget)) {
            throw new IllegalArgumentException("Widget is already registered.");
        }
        widgets.add(widget);
        return this;
    }

    /**
     * Removes a top-level widget.
     * Hover, focus and pointer capture that refer to the removed subtree are cleared.
     *
     * @param widget widget to remove
     * @return true when the widget was registered
     * @throws NullPointerException if {@code widget} is null
     */
    public boolean remove(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        for (int index = 0; index < widgets.size(); index++) {
            if (widgets.get(index) == widget) {
                widgets.remove(index);
                pruneInteraction();
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the logical viewport and delegates the physical viewport to the backend.
     *
     * @param viewport target viewport
     * @throws NullPointerException if {@code viewport} is null
     */
    public void resize(Viewport viewport) {
        renderer.resize(viewport);
    }

    /**
     * Returns the logical viewport used by widgets.
     *
     * @return the current logical viewport, or an empty rectangle before the first resize
     */
    public Rect viewport() {
        return renderer.viewport();
    }

    /**
     * Returns timing information for the current frame.
     * The value advances on each {@link #update(float)} call and is also passed to widget drawing.
     *
     * @return the current frame timing, or {@link FrameInfo#ZERO} before the first update
     */
    public FrameInfo frame() {
        return frame;
    }

    /**
     * Drains queued input events, then updates every top-level widget and its children.
     *
     * @param deltaSeconds elapsed time in seconds
     * @throws IllegalArgumentException if {@code deltaSeconds} is negative or non-finite
     */
    public void update(float deltaSeconds) {
        frame = new FrameInfo(deltaSeconds, frame.elapsedSeconds() + deltaSeconds, frame.frameIndex() + 1);
        pruneInteraction();
        InputEvent event;
        while ((event = input.poll()) != null) {
            dispatch(event);
        }
        for (Widget widget : widgets) {
            widget.update(frame);
        }
    }

    /**
     * Dispatches a single input event without draining the registered queue.
     *
     * @param event event to dispatch
     * @throws NullPointerException if {@code event} is null
     */
    public void handle(InputEvent event) {
        Objects.requireNonNull(event, "event");
        pruneInteraction();
        dispatch(event);
    }

    /**
     * Draws every top-level widget and submits the completed list to the backend.
     * Widgets receive the current {@link #frame()} in their draw callbacks.
     *
     * @throws IllegalStateException if the viewport has not been set with
     *                               {@link #resize(Viewport)}
     */
    public void render() {
        pruneInteraction();
        renderer.begin();
        for (Widget widget : widgets) {
            widget.draw(renderer, frame);
        }
        renderer.end();
    }

    /**
     * Returns the widget currently under the pointer.
     *
     * @return the hovered widget, or null when the pointer is outside
     */
    public Widget hovered() {
        return hovered;
    }

    /**
     * Returns the widget that owns keyboard focus.
     *
     * @return the focused widget, or null when nothing is focused
     */
    public Widget focused() {
        return focused;
    }

    /**
     * Moves keyboard focus to the supplied widget or clears it.
     * The widget must belong to a registered top-level subtree.
     *
     * @param widget widget to focus, or null to clear focus
     * @throws IllegalArgumentException if the widget is not focusable or is not registered
     */
    public void focus(Widget widget) {
        if (widget == focused) {
            return;
        }
        if (widget != null) {
            if (!widget.isFocusable()) {
                throw new IllegalArgumentException("Widget is not focusable.");
            }
            if (!isAttached(widget)) {
                throw new IllegalArgumentException("Widget is not registered with the manager.");
            }
        }
        Widget previous = focused;
        focused = null;
        if (previous != null) {
            previous.setFocused(false);
            previous.onFocusChanged(false);
        }
        focused = widget;
        if (widget != null) {
            widget.setFocused(true);
            widget.onFocusChanged(true);
        }
    }

    private void dispatch(InputEvent event) {
        switch (event) {
            case PointerMoved moved -> pointerMoved(moved.x(), moved.y());
            case PointerEntered entered -> pointerEntered(entered.x(), entered.y());
            case PointerExited exited -> pointerExited(exited.x(), exited.y());
            case PointerPressed pressed -> pointerPressed(pressed);
            case PointerReleased released -> pointerReleased(released);
            case PointerScrolled scrolled -> pointerScrolled(scrolled);
            case KeyPressed pressed -> {
                if (focused != null) {
                    focused.onKeyPressed(pressed.key(), pressed.modifiers(), pressed.repeat());
                }
            }
            case KeyReleased released -> {
                if (focused != null) {
                    focused.onKeyReleased(released.key(), released.modifiers());
                }
            }
            case CharTyped typed -> {
                if (focused != null) {
                    focused.onCharTyped(typed.codepoint());
                }
            }
            case FocusChanged focus -> focusChanged(focus.focused());
        }
    }

    private void pointerEntered(float x, float y) {
        pointerInside = true;
        pointerX = x;
        pointerY = y;
        updateHover();
    }

    private void pointerExited(float x, float y) {
        pointerX = x;
        pointerY = y;
        pointerInside = false;
        updateHover();
    }

    private void pointerMoved(float x, float y) {
        pointerInside = true;
        pointerX = x;
        pointerY = y;
        Widget target = updateHover();
        if (target != null) {
            target.onPointerMove(x, y);
        }
        Set<Widget> delivered = Collections.newSetFromMap(new IdentityHashMap<>());
        if (target != null) {
            delivered.add(target);
        }
        for (Widget widget : captures.values()) {
            if (delivered.add(widget)) {
                widget.onPointerMove(x, y);
            }
        }
    }

    private void pointerPressed(PointerPressed pressed) {
        pointerInside = true;
        pointerX = pressed.x();
        pointerY = pressed.y();
        Widget target = updateHover();
        if (target == null) {
            focus(null);
            return;
        }
        if (target.isFocusable()) {
            focus(target);
        } else {
            focus(null);
        }
        Widget previous = captures.put(pressed.button(), target);
        if (previous != null) {
            clearPressedIfUncaptured(previous);
        }
        target.setPressed(true);
        target.onPointerPress(pointerX, pointerY, pressed.button(), pressed.modifiers());
    }

    private void pointerReleased(PointerReleased released) {
        pointerInside = true;
        pointerX = released.x();
        pointerY = released.y();
        updateHover();
        Widget target = captures.remove(released.button());
        if (target == null) {
            return;
        }
        target.onPointerRelease(pointerX, pointerY, released.button(), released.modifiers());
        clearPressedIfUncaptured(target);
    }

    private void pointerScrolled(PointerScrolled scrolled) {
        pointerInside = true;
        pointerX = scrolled.x();
        pointerY = scrolled.y();
        Widget target = updateHover();
        if (target != null) {
            target.onPointerScroll(pointerX, pointerY, scrolled.deltaX(), scrolled.deltaY());
        }
    }

    private void focusChanged(boolean focusedWindow) {
        if (!focusedWindow) {
            pointerInside = false;
            clearHover();
            clearCaptures();
        }
    }

    private Widget updateHover() {
        Widget hit = pointerInside ? hitTest(pointerX, pointerY) : null;
        if (hit == hovered) {
            return hovered;
        }
        Widget previous = hovered;
        hovered = null;
        if (previous != null) {
            previous.setHovered(false);
            previous.onPointerExit();
        }
        hovered = hit;
        if (hit != null) {
            hit.setHovered(true);
            hit.onPointerEnter();
        }
        return hit;
    }

    private Widget hitTest(float x, float y) {
        for (int index = widgets.size() - 1; index >= 0; index--) {
            Widget hit = widgets.get(index).hitTest(x, y);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void pruneInteraction() {
        if (hovered != null && !isAttached(hovered)) {
            clearHover();
        }
        if (focused != null && !isAttached(focused)) {
            focus(null);
        }
        if (captures.isEmpty()) {
            return;
        }
        Set<Widget> released = Collections.newSetFromMap(new IdentityHashMap<>());
        Iterator<Map.Entry<MouseButton, Widget>> iterator = captures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MouseButton, Widget> entry = iterator.next();
            if (!isAttached(entry.getValue())) {
                released.add(entry.getValue());
                iterator.remove();
            }
        }
        for (Widget widget : released) {
            clearPressedIfUncaptured(widget);
        }
    }

    private void clearHover() {
        if (hovered == null) {
            return;
        }
        Widget previous = hovered;
        hovered = null;
        previous.setHovered(false);
        previous.onPointerExit();
    }

    private void clearCaptures() {
        if (captures.isEmpty()) {
            return;
        }
        Set<Widget> pressedWidgets = Collections.newSetFromMap(new IdentityHashMap<>());
        pressedWidgets.addAll(captures.values());
        captures.clear();
        for (Widget widget : pressedWidgets) {
            widget.setPressed(false);
        }
    }

    private void clearPressedIfUncaptured(Widget widget) {
        if (!captures.containsValue(widget)) {
            widget.setPressed(false);
        }
    }

    private boolean isAttached(Widget widget) {
        Widget top = widget;
        while (top.parent() != null) {
            top = top.parent();
        }
        return containsIdentity(widgets, top);
    }

    private static boolean containsIdentity(List<Widget> list, Widget widget) {
        for (Widget candidate : list) {
            if (candidate == widget) {
                return true;
            }
        }
        return false;
    }
}
