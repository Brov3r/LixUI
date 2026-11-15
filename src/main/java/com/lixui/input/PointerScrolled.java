package com.lixui.input;

/**
 * The mouse wheel or touchpad scrolled.
 *
 * @param x horizontal pointer position in framebuffer pixels
 * @param y vertical pointer position in framebuffer pixels
 * @param deltaX horizontal scroll amount in wheel steps
 * @param deltaY vertical scroll amount in wheel steps
 */
public record PointerScrolled(float x, float y, float deltaX, float deltaY) implements InputEvent {
}
