package com.lixui.widget;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.CharTyped;
import com.lixui.input.FocusChanged;
import com.lixui.input.InputEvent;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.KeyPressed;
import com.lixui.input.KeyReleased;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.PointerEntered;
import com.lixui.input.PointerExited;
import com.lixui.input.PointerMoved;
import com.lixui.input.PointerPressed;
import com.lixui.input.PointerReleased;
import com.lixui.input.PointerScrolled;
import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import com.lixui.render.Antialiasing;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.theme.Theme;

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
 * Registers an input queue and a render backend, owns top-level widgets, and drives their logic,
 * layout and drawing.
 * Widgets added to this manager have no parent and are positioned relative to the viewport. Add a
 * widget to another widget to build a hierarchy with parent-relative coordinates.
 * Top-level widgets are drawn in insertion order and hit-tested in reverse order, so the last
 * widget is visually on top. Added widgets receive a shared {@link UiContext} and are detached
 * when removed or when the manager is closed. Instances are single-threaded and are intended to be
 * updated once per frame.
 * Window focus loss preserves widget focus but suppresses keyboard and text events until focus returns.
 */
public final class WidgetManager implements AutoCloseable {
    private static final float DRAG_THRESHOLD = 4;
    private static final float CLICK_SLOP = 4;
    private static final double DOUBLE_CLICK_SECONDS = 0.4;
    private static final double LONG_PRESS_SECONDS = 0.5;

    private final InputState input;
    private final RenderManager renderer;
    private final UiContext context;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<Widget> widgetView = Collections.unmodifiableList(widgets);
    private final EnumMap<MouseButton, Widget> captures = new EnumMap<>(MouseButton.class);
    private final EnumMap<MouseButton, PressState> presses = new EnumMap<>(MouseButton.class);
    private Widget hovered;
    private Widget focused;
    private Widget lastClickTarget;
    private MouseButton lastClickButton;
    private FrameInfo frame = FrameInfo.ZERO;
    private float pointerX;
    private float pointerY;
    private float lastClickX;
    private float lastClickY;
    private double eventTime;
    private double lastClickTime = Double.NEGATIVE_INFINITY;
    private boolean pointerInside;
    private boolean windowFocused = true;
    private long focusRevision;
    private long hoverRevision;
    private boolean closed;

    /**
     * Creates a manager for the supplied input queue and backend without clipboard or cursor
     * integration.
     *
     * @param input queue drained by {@link #update(float)}
     * @param backend backend that receives completed draw lists
     * @throws NullPointerException if {@code input} or {@code backend} is null
     */
    public WidgetManager(InputState input, RenderBackend backend) {
        this(input, backend, UiPlatform.NONE);
    }

    /**
     * Creates a manager for the supplied input queue, backend and platform adapter.
     *
     * @param input queue drained by {@link #update(float)}
     * @param backend backend that receives completed draw lists
     * @param platform host adapter used for clipboard access and cursor changes
     * @throws NullPointerException if any argument is null
     */
    public WidgetManager(InputState input, RenderBackend backend, UiPlatform platform) {
        this.input = Objects.requireNonNull(input, "input");
        renderer = new RenderManager(Objects.requireNonNull(backend, "backend"));
        context = new UiContext(renderer, Objects.requireNonNull(platform, "platform"), this);
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
     * Returns the context shared by all attached widgets.
     *
     * @return the manager context
     */
    public UiContext context() {
        return context;
    }

    /**
     * Replaces the active theme and notifies every attached widget.
     * Nothing happens when the theme is equal to the current one.
     *
     * @param theme new theme
     * @throws NullPointerException if {@code theme} is null
     * @throws IllegalStateException if the manager is closed
     */
    public void setTheme(Theme theme) {
        Objects.requireNonNull(theme, "theme");
        ensureOpen();
        if (context.theme().equals(theme)) {
            return;
        }
        context.setTheme(theme);
        notifyContextChanged();
    }

    /**
     * Replaces the font used by widgets when no explicit font is supplied and notifies every
     * attached widget.
     *
     * @param font default font
     * @throws NullPointerException if {@code font} is null
     * @throws IllegalStateException if the manager is closed
     */
    public void setDefaultFont(Font font) {
        Objects.requireNonNull(font, "font");
        ensureOpen();
        if (context.defaultFont() == font) {
            return;
        }
        context.setDefaultFont(font);
        notifyContextChanged();
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
        return widgetView;
    }

    /**
     * Adds a top-level widget.
     * The widget must not already have a parent or belong to a manager.
     *
     * @param widget widget to add
     * @return this manager
     * @throws NullPointerException if {@code widget} is null
     * @throws IllegalArgumentException if the widget has a parent or is already attached to a manager
     * @throws IllegalStateException if the manager is closed
     */
    public WidgetManager add(Widget widget) {
        Objects.requireNonNull(widget, "widget");
        ensureOpen();
        if (widget.parent() != null) {
            throw new IllegalArgumentException("Only widgets without a parent can be added.");
        }
        if (widget.context() != null || containsIdentity(widgets, widget)) {
            throw new IllegalArgumentException("Widget is already registered.");
        }
        widgets.add(widget);
        try {
            widget.attach(context);
        } catch (RuntimeException | Error exception) {
            widgets.removeIf(candidate -> candidate == widget);
            pruneInteraction();
            throw exception;
        }
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
                try {
                    widget.detach();
                } finally {
                    pruneInteraction();
                }
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
     * @throws IllegalStateException if the manager is closed
     */
    public void resize(Viewport viewport) {
        Objects.requireNonNull(viewport, "viewport");
        ensureOpen();
        renderer.resize(viewport);
        context.setViewport(viewport);
        notifyContextChanged();
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
     * Runs a layout pass over every top-level widget and its subtree.
     * Each top-level widget keeps its current position and size, while containers rearrange their
     * children through {@link Widget#layout(Rect)}.
     * {@link #render()} calls this method automatically before drawing.
     *
     * @throws IllegalStateException if the manager is closed
     */
    public void layout() {
        ensureOpen();
        for (Widget widget : widgets) {
            widget.layout(widget.bounds());
        }
        updateHover();
        refreshCursor();
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
     * @throws IllegalStateException if the manager is closed
     */
    public void update(float deltaSeconds) {
        ensureOpen();
        frame = new FrameInfo(deltaSeconds, frame.elapsedSeconds() + deltaSeconds, frame.frameIndex() + 1);
        eventTime = frame.elapsedSeconds();
        pruneInteraction();
        InputEvent event;
        while ((event = input.poll()) != null) {
            dispatch(event);
        }
        updatePresses();
        for (Widget widget : widgets) {
            widget.update(frame);
        }
        updateHover();
        refreshCursor();
    }

    /**
     * Dispatches a single input event without draining the registered queue.
     *
     * @param event event to dispatch
     * @throws NullPointerException if {@code event} is null
     * @throws IllegalStateException if the manager is closed
     */
    public void handle(InputEvent event) {
        Objects.requireNonNull(event, "event");
        ensureOpen();
        pruneInteraction();
        dispatch(event);
    }

    /**
     * Lays out, draws every top-level widget and submits the completed list to the backend.
     * Widgets receive the current {@link #frame()} in their draw callbacks.
     *
     * @throws IllegalStateException if the viewport has not been set with
     *                               {@link #resize(Viewport)} or the manager is closed
     */
    public void render() {
        ensureOpen();
        pruneInteraction();
        layout();
        renderer.begin();
        for (Widget widget : widgets) {
            widget.draw(renderer, frame);
        }
        renderer.end();
    }

    /**
     * Detaches every widget and releases resources created through the manager context.
     * Hover, focus and pointer captures are cleared. The render backend and platform adapter are
     * owned by the host and are not closed by this method.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        clearCaptures();
        clearHover();
        focus(null);
        resetClickSequence();
        pointerInside = false;
        for (Widget widget : List.copyOf(widgets)) {
            widget.detach();
        }
        widgets.clear();
        context.close();
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
            if (!isAttached(widget) || !isInteractive(widget)) {
                throw new IllegalArgumentException("Widget is not an interactive registered widget.");
            }
        }
        Widget previous = focused;
        long revision = ++focusRevision;
        focused = null;
        if (previous != null) {
            previous.setFocused(false);
            previous.onFocusChanged(false);
        }
        if (revision != focusRevision) {
            return;
        }
        if (widget != null && (!isAttached(widget) || !isInteractive(widget) || !widget.isFocusable())) {
            return;
        }
        focused = widget;
        if (widget != null) {
            widget.setFocused(true);
            widget.onFocusChanged(true);
        }
    }

    /**
     * Moves focus to the next or previous focusable widget in depth-first tree order.
     *
     * @param forward true to move forward, false to move backward
     * @throws IllegalStateException if the manager is closed
     */
    public void focusNext(boolean forward) {
        ensureOpen();
        List<Widget> order = new ArrayList<>();
        Widget scope = focusScope(focused);
        if (scope != null) {
            collectFocusable(scope, order);
        } else {
            for (Widget widget : widgets) {
                collectFocusable(widget, order);
            }
        }
        if (order.isEmpty()) {
            focus(null);
            return;
        }
        int current = indexOfIdentity(order, focused);
        int next;
        if (current < 0) {
            next = forward ? 0 : order.size() - 1;
        } else {
            int step = forward ? 1 : -1;
            next = Math.floorMod(current + step, order.size());
        }
        focus(order.get(next));
    }

    /**
     * Captures pointer button events for a widget until the button is released or the capture is
     * released explicitly.
     * Recapturing the same button for the same widget preserves its active gesture.
     *
     * @param widget widget that receives captured pointer events
     * @param button captured button
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the widget is not an interactive registered widget
     * @throws IllegalStateException if the manager is closed
     */
    public void capturePointer(Widget widget, MouseButton button) {
        Objects.requireNonNull(widget, "widget");
        Objects.requireNonNull(button, "button");
        ensureOpen();
        if (!isAttached(widget) || !isInteractive(widget)) {
            throw new IllegalArgumentException("Widget is not an interactive registered widget.");
        }
        Widget previous = captures.put(button, widget);
        if (previous != widget) {
            presses.remove(button);
        }
        if (previous != null && previous != widget) {
            clearPressedIfUncaptured(previous);
        }
        widget.setPressed(true);
    }

    /**
     * Releases a pointer capture and clears the pressed state when no other button is captured.
     *
     * @param button captured button
     * @throws NullPointerException if {@code button} is null
     * @throws IllegalStateException if the manager is closed
     */
    public void releasePointerCapture(MouseButton button) {
        Objects.requireNonNull(button, "button");
        ensureOpen();
        Widget target = captures.remove(button);
        presses.remove(button);
        resetClickSequence();
        if (target != null) {
            clearPressedIfUncaptured(target);
        }
    }

    void releasePointerCapture(Widget owner, MouseButton button) {
        Objects.requireNonNull(button, "button");
        ensureOpen();
        if (captures.get(button) == owner) {
            releasePointerCapture(button);
        }
    }

    private void notifyContextChanged() {
        for (Widget widget : widgets) {
            widget.contextChanged();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Widget manager is closed.");
        }
    }

    private void dispatch(InputEvent event) {
        if (!windowFocused && (event instanceof KeyPressed || event instanceof KeyReleased
                || event instanceof CharTyped)) {
            return;
        }
        switch (event) {
            case PointerMoved moved -> pointerMoved(moved.x(), moved.y());
            case PointerEntered entered -> pointerEntered(entered.x(), entered.y());
            case PointerExited exited -> pointerExited(exited.x(), exited.y());
            case PointerPressed pressed -> pointerPressed(pressed);
            case PointerReleased released -> pointerReleased(released);
            case PointerScrolled scrolled -> pointerScrolled(scrolled);
            case KeyPressed pressed -> {
                boolean consumed = focused != null && bubbleKeyPressed(pressed);
                if (!consumed && pressed.key() == Key.TAB) {
                    focusNext(!pressed.modifiers().shift());
                }
            }
            case KeyReleased released -> {
                if (focused != null) {
                    bubbleKeyReleased(released);
                }
            }
            case CharTyped typed -> {
                if (focused != null) {
                    bubbleCharTyped(typed.codepoint());
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
        Set<Widget> delivered = Collections.newSetFromMap(new IdentityHashMap<>());
        if (target != null) {
            bubblePointerMove(target, x, y, delivered);
        }
        for (Widget widget : List.copyOf(captures.values())) {
            if (!delivered.contains(widget) && isAttached(widget) && isInteractive(widget)) {
                bubblePointerMove(widget, x, y, delivered);
            }
        }
        updateDrag(x, y);
    }

    private void pointerPressed(PointerPressed pressed) {
        pointerInside = true;
        pointerX = pressed.x();
        pointerY = pressed.y();
        Widget target = updateHover();
        if (target == null) {
            resetClickSequence();
            focus(null);
            return;
        }
        focus(focusTarget(target));
        if (!isAttached(target) || !isInteractive(target)) {
            return;
        }
        Widget previous = captures.put(pressed.button(), target);
        if (previous != null && previous != target) {
            presses.remove(pressed.button());
            clearPressedIfUncaptured(previous);
        }
        presses.put(pressed.button(), new PressState(target, pointerX, pointerY,
                eventTime, pressed.modifiers()));
        target.setPressed(true);
        bubblePointerPress(target, pressed);
    }

    private void pointerReleased(PointerReleased released) {
        pointerInside = true;
        pointerX = released.x();
        pointerY = released.y();
        updateHover();
        Widget target = captures.remove(released.button());
        PressState state = presses.remove(released.button());
        if (target == null) {
            resetClickSequence();
            return;
        }
        boolean clicked = false;
        try {
            bubblePointerRelease(target, released);
            if (state != null && isAttached(target) && isInteractive(target)) {
                if (state.dragging) {
                    bubbleDragEnd(target, pointerX, pointerY, released.button(), state.modifiers);
                } else if (!state.longPressed && hovered == target
                        && distance(state.x, state.y, pointerX, pointerY) <= CLICK_SLOP
                        && eventTime - state.time < LONG_PRESS_SECONDS) {
                    completeClick(target, released.button(), released.modifiers());
                    clicked = true;
                }
            }
        } finally {
            clearPressedIfUncaptured(target);
            if (!clicked) {
                resetClickSequence();
            }
        }
    }

    private void pointerScrolled(PointerScrolled scrolled) {
        pointerInside = true;
        pointerX = scrolled.x();
        pointerY = scrolled.y();
        Widget target = updateHover();
        if (target != null) {
            bubblePointerScroll(target, scrolled);
        }
    }

    private void updateDrag(float x, float y) {
        for (MouseButton button : List.copyOf(presses.keySet())) {
            PressState state = presses.get(button);
            if (state == null || captures.get(button) != state.target) {
                continue;
            }
            if (!state.dragging && distance(state.x, state.y, x, y) >= DRAG_THRESHOLD) {
                state.dragging = true;
                resetClickSequence();
                bubbleDragStart(state.target, x, y, button, state.modifiers);
            }
            if (state.dragging && presses.get(button) == state && captures.get(button) == state.target) {
                bubbleDrag(state.target, x, y, button, state.modifiers);
            }
        }
    }

    private void updatePresses() {
        for (MouseButton button : List.copyOf(presses.keySet())) {
            PressState state = presses.get(button);
            if (state == null || captures.get(button) != state.target
                    || state.dragging || state.longPressed) {
                continue;
            }
            if (eventTime - state.time >= LONG_PRESS_SECONDS) {
                state.longPressed = true;
                resetClickSequence();
                bubbleLongPress(state.target, state.x, state.y, button, state.modifiers);
            }
        }
    }

    private void completeClick(Widget target, MouseButton button, Modifiers modifiers) {
        boolean doubleClick = target == lastClickTarget
                && button == lastClickButton
                && eventTime - lastClickTime <= DOUBLE_CLICK_SECONDS
                && distance(lastClickX, lastClickY, pointerX, pointerY) <= CLICK_SLOP;
        if (doubleClick) {
            bubbleDoubleClick(target, pointerX, pointerY, button, modifiers);
            lastClickTarget = null;
            lastClickButton = null;
            lastClickTime = Double.NEGATIVE_INFINITY;
            return;
        }
        lastClickTarget = target;
        lastClickButton = button;
        lastClickTime = eventTime;
        lastClickX = pointerX;
        lastClickY = pointerY;
        bubbleClick(target, pointerX, pointerY, button, modifiers);
    }

    private void focusChanged(boolean focusedWindow) {
        windowFocused = focusedWindow;
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
        long revision = ++hoverRevision;
        hovered = null;
        if (previous != null) {
            previous.setHovered(false);
            previous.onPointerExit();
        }
        if (revision != hoverRevision) {
            return hovered;
        }
        hit = pointerInside ? hitTest(pointerX, pointerY) : null;
        hovered = hit;
        if (hit != null) {
            hit.setHovered(true);
            hit.onPointerEnter();
        }
        refreshCursor();
        return hovered;
    }

    private static Point localPoint(Widget widget, float x, float y) {
        Point origin = widget.absolutePosition();
        return new Point(x - origin.x(), y - origin.y());
    }

    private static Widget focusScope(Widget widget) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (current.isFocusScope()) {
                return current;
            }
        }
        return null;
    }

    private static void collectFocusable(Widget widget, List<Widget> order) {
        if (!isInteractive(widget)) {
            return;
        }
        if (widget.isFocusable()) {
            order.add(widget);
        }
        for (Widget child : widget.children()) {
            collectFocusable(child, order);
        }
    }

    private static Widget focusTarget(Widget target) {
        for (Widget widget = target; widget != null; widget = widget.parent()) {
            if (widget.isFocusable() && isInteractive(widget)) {
                return widget;
            }
        }
        return null;
    }

    private static boolean isInteractive(Widget widget) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (!current.isVisible() || !current.isEnabled()) {
                return false;
            }
        }
        return true;
    }

    private static float distance(float firstX, float firstY, float secondX, float secondY) {
        return (float) Math.hypot(secondX - firstX, secondY - firstY);
    }

    private boolean bubblePointerMove(Widget target, float x, float y,
                                             Set<Widget> delivered) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            if (!delivered.add(widget)) {
                continue;
            }
            Point local = localPoint(widget, x, y);
            if (widget.onPointerMove(local.x(), local.y())) {
                return true;
            }
        }
        return false;
    }

    private boolean bubblePointerPress(Widget target, PointerPressed pressed) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, pressed.x(), pressed.y());
            if (widget.onPointerPress(local.x(), local.y(), pressed.button(), pressed.modifiers())) {
                return true;
            }
        }
        return false;
    }

    private boolean bubblePointerRelease(Widget target, PointerReleased released) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, released.x(), released.y());
            if (widget.onPointerRelease(local.x(), local.y(), released.button(),
                    released.modifiers())) {
                return true;
            }
        }
        return false;
    }

    private boolean bubblePointerScroll(Widget target, PointerScrolled scrolled) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, scrolled.x(), scrolled.y());
            if (widget.onPointerScroll(local.x(), local.y(), scrolled.deltaX(), scrolled.deltaY())) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleClick(Widget target, float x, float y, MouseButton button,
                                       Modifiers modifiers) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, x, y);
            if (widget.onClick(local.x(), local.y(), button, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleDoubleClick(Widget target, float x, float y, MouseButton button,
                                             Modifiers modifiers) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, x, y);
            if (widget.onDoubleClick(local.x(), local.y(), button, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleDragStart(Widget target, float x, float y, MouseButton button,
                                           Modifiers modifiers) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, x, y);
            if (widget.onDragStart(local.x(), local.y(), button, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleDrag(Widget target, float x, float y, MouseButton button,
                                      Modifiers modifiers) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, x, y);
            if (widget.onDrag(local.x(), local.y(), button, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleDragEnd(Widget target, float x, float y, MouseButton button,
                                         Modifiers modifiers) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, x, y);
            if (widget.onDragEnd(local.x(), local.y(), button, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleLongPress(Widget target, float x, float y, MouseButton button,
                                           Modifiers modifiers) {
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            Point local = localPoint(widget, x, y);
            if (widget.onLongPress(local.x(), local.y(), button, modifiers)) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleKeyPressed(KeyPressed pressed) {
        Widget target = focused;
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            if (widget.onKeyPressed(pressed.key(), pressed.modifiers(), pressed.repeat())) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleKeyReleased(KeyReleased released) {
        Widget target = focused;
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            if (widget.onKeyReleased(released.key(), released.modifiers())) {
                return true;
            }
        }
        return false;
    }

    private boolean bubbleCharTyped(int codepoint) {
        Widget target = focused;
        for (Widget widget = target; widget != null && isAttached(target) && isInteractive(target);
                widget = widget.parent()) {
            if (widget.onCharTyped(codepoint)) {
                return true;
            }
        }
        return false;
    }

    private Widget hitTest(float x, float y) {
        for (int index = widgets.size() - 1; index >= 0; index--) {
            Widget widget = widgets.get(index);
            Widget hit = widget.hitTest(x - widget.bounds().x(), y - widget.bounds().y());
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    void pruneInteraction() {
        if (lastClickTarget != null && (!isAttached(lastClickTarget) || !isInteractive(lastClickTarget))) {
            resetClickSequence();
        }
        if (hovered != null && (!isAttached(hovered) || !isInteractive(hovered))) {
            clearHover();
        }
        if (focused != null && (!isAttached(focused) || !isInteractive(focused)
                || !focused.isFocusable())) {
            focus(null);
        }
        if (captures.isEmpty()) {
            refreshCursor();
            return;
        }
        Set<Widget> released = Collections.newSetFromMap(new IdentityHashMap<>());
        Iterator<Map.Entry<MouseButton, Widget>> iterator = captures.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MouseButton, Widget> entry = iterator.next();
            if (!isAttached(entry.getValue()) || !isInteractive(entry.getValue())) {
                presses.remove(entry.getKey());
                released.add(entry.getValue());
                iterator.remove();
            }
        }
        for (Widget widget : released) {
            clearPressedIfUncaptured(widget);
        }
        refreshCursor();
    }

    private void clearHover() {
        if (hovered == null) {
            return;
        }
        Widget previous = hovered;
        hoverRevision++;
        hovered = null;
        previous.setHovered(false);
        previous.onPointerExit();
        refreshCursor();
    }

    private void clearCaptures() {
        presses.clear();
        resetClickSequence();
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

    private void resetClickSequence() {
        lastClickTarget = null;
        lastClickButton = null;
        lastClickTime = Double.NEGATIVE_INFINITY;
    }

    private void refreshCursor() {
        if (closed) {
            return;
        }
        Cursor cursor = Cursor.DEFAULT;
        for (Widget widget = hovered; widget != null; widget = widget.parent()) {
            Cursor candidate = widget.cursor();
            if (candidate != null && candidate != Cursor.DEFAULT) {
                cursor = candidate;
                break;
            }
        }
        context.setCursor(cursor);
    }

    private void clearPressedIfUncaptured(Widget widget) {
        for (Widget captured : captures.values()) {
            if (captured == widget) {
                return;
            }
        }
        widget.setPressed(false);
    }

    private boolean isAttached(Widget widget) {
        Widget top = widget;
        while (top.parent() != null) {
            top = top.parent();
        }
        return containsIdentity(widgets, top);
    }

    private static boolean containsIdentity(List<Widget> list, Widget widget) {
        return indexOfIdentity(list, widget) >= 0;
    }

    private static int indexOfIdentity(List<Widget> list, Widget widget) {
        for (int index = 0; index < list.size(); index++) {
            if (list.get(index) == widget) {
                return index;
            }
        }
        return -1;
    }

    private static final class PressState {
        private final Widget target;
        private final float x;
        private final float y;
        private final double time;
        private final Modifiers modifiers;
        private boolean dragging;
        private boolean longPressed;

        PressState(Widget target, float x, float y, double time, Modifiers modifiers) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.time = time;
            this.modifiers = modifiers;
        }
    }
}
