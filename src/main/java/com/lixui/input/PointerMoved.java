package com.lixui.input;

/**
 * The pointer moved inside the window.
 *
 * @param x horizontal position in logical UI units
 * @param y vertical position in logical UI units
 */
public record PointerMoved(float x, float y) implements InputEvent {
}
