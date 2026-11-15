package com.lixui.input;

/**
 * The pointer entered the window.
 *
 * @param x horizontal position in logical UI units
 * @param y vertical position in logical UI units
 */
public record PointerEntered(float x, float y) implements InputEvent {
}
