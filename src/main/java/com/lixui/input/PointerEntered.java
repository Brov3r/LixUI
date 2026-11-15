package com.lixui.input;

/**
 * The pointer entered the window.
 *
 * @param x horizontal position in framebuffer pixels
 * @param y vertical position in framebuffer pixels
 */
public record PointerEntered(float x, float y) implements InputEvent {
}
