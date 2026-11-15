package com.lixui.input;

/**
 * The window gained or lost input focus.
 *
 * @param focused true when the window is focused
 */
public record FocusChanged(boolean focused) implements InputEvent {
}
