package com.lixui.input;

/**
 * The pointer left the window.
 *
 * @param x horizontal position in logical UI units
 * @param y vertical position in logical UI units
 */
public record PointerExited(float x, float y) implements InputEvent {
}
