package com.lixui.input;

import java.util.Objects;

/**
 * A mouse button was pressed.
 *
 * @param x horizontal position in framebuffer pixels
 * @param y vertical position in framebuffer pixels
 * @param button pressed button
 * @param modifiers active keyboard modifiers
 */
public record PointerPressed(float x, float y, MouseButton button, Modifiers modifiers) implements InputEvent {
    /**
     * Creates a pointer press event.
     *
     * @param x horizontal position in framebuffer pixels
     * @param y vertical position in framebuffer pixels
     * @param button pressed button
     * @param modifiers active keyboard modifiers
     * @throws NullPointerException if {@code button} or {@code modifiers} is null
     */
    public PointerPressed {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(modifiers, "modifiers");
    }
}
