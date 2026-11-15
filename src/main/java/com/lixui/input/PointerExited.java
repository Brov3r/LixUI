package com.lixui.input;

/**
 * The pointer left the window.
 *
 * @param x horizontal position in framebuffer pixels
 * @param y vertical position in framebuffer pixels
 */
public record PointerExited(float x, float y) implements InputEvent {
}
