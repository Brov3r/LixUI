package com.lixui.input;

/**
 * The pointer moved inside the window.
 *
 * @param x horizontal position in framebuffer pixels
 * @param y vertical position in framebuffer pixels
 */
public record PointerMoved(float x, float y) implements InputEvent {
}
