package com.lixui.input;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * A single-threaded queue of input events shared by a windowing adapter and the UI.
 * The UI drains events once per update, so events that arrive between frames are preserved in order.
 */
public final class InputState {
    private final Deque<InputEvent> events = new ArrayDeque<>();

    /**
     * Appends an event to the queue.
     *
     * @param event event to append
     * @throws NullPointerException if {@code event} is null
     */
    public void add(InputEvent event) {
        events.addLast(Objects.requireNonNull(event, "event"));
    }

    /**
     * Removes and returns the oldest queued event.
     *
     * @return the next event, or null when the queue is empty
     */
    public InputEvent poll() {
        return events.pollFirst();
    }

    /**
     * Reports whether the queue contains events.
     *
     * @return true when at least one event is queued
     */
    public boolean hasEvents() {
        return !events.isEmpty();
    }

    /**
     * Removes all queued events.
     */
    public void clear() {
        events.clear();
    }

    /**
     * Queues a pointer move.
     *
     * @param x horizontal position in logical UI units
     * @param y vertical position in logical UI units
     */
    public void pointerMoved(float x, float y) {
        add(new PointerMoved(x, y));
    }

    /**
     * Queues a pointer enter.
     *
     * @param x horizontal position in logical UI units
     * @param y vertical position in logical UI units
     */
    public void pointerEntered(float x, float y) {
        add(new PointerEntered(x, y));
    }

    /**
     * Queues a pointer exit.
     *
     * @param x horizontal position in logical UI units
     * @param y vertical position in logical UI units
     */
    public void pointerExited(float x, float y) {
        add(new PointerExited(x, y));
    }

    /**
     * Queues a pointer press.
     *
     * @param x horizontal position in logical UI units
     * @param y vertical position in logical UI units
     * @param button pressed button
     * @param modifiers active keyboard modifiers
     */
    public void pointerPressed(float x, float y, MouseButton button, Modifiers modifiers) {
        add(new PointerPressed(x, y, button, modifiers));
    }

    /**
     * Queues a pointer release.
     *
     * @param x horizontal position in logical UI units
     * @param y vertical position in logical UI units
     * @param button released button
     * @param modifiers active keyboard modifiers
     */
    public void pointerReleased(float x, float y, MouseButton button, Modifiers modifiers) {
        add(new PointerReleased(x, y, button, modifiers));
    }

    /**
     * Queues a scroll event.
     *
     * @param x horizontal position in logical UI units
     * @param y vertical position in logical UI units
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     */
    public void pointerScrolled(float x, float y, float deltaX, float deltaY) {
        add(new PointerScrolled(x, y, deltaX, deltaY));
    }

    /**
     * Queues a key press.
     *
     * @param key pressed key
     * @param modifiers active keyboard modifiers
     * @param repeat true for a repeated key press
     */
    public void keyPressed(Key key, Modifiers modifiers, boolean repeat) {
        add(new KeyPressed(key, modifiers, repeat));
    }

    /**
     * Queues a key release.
     *
     * @param key released key
     * @param modifiers active keyboard modifiers
     */
    public void keyReleased(Key key, Modifiers modifiers) {
        add(new KeyReleased(key, modifiers));
    }

    /**
     * Queues a typed Unicode code point.
     *
     * @param codepoint typed code point
     */
    public void charTyped(int codepoint) {
        add(new CharTyped(codepoint));
    }

    /**
     * Queues a window focus change.
     *
     * @param focused true when the window gained focus
     */
    public void focusChanged(boolean focused) {
        add(new FocusChanged(focused));
    }
}
