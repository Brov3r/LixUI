package com.lixui.input;

import java.util.Objects;

/**
 * A mouse button was released.
 *
 * @param x horizontal position in framebuffer pixels
 * @param y vertical position in framebuffer pixels
 * @param button released button
 * @param modifiers active keyboard modifiers
 */
public record PointerReleased(float x, float y, MouseButton button, Modifiers modifiers) implements InputEvent {
    /**
     * Creates a pointer release event.
     *
     * @param x horizontal position in framebuffer pixels
     * @param y vertical position in framebuffer pixels
     * @param button released button
     * @param modifiers active keyboard modifiers
     * @throws NullPointerException if {@code button} or {@code modifiers} is null
     */
    public PointerReleased {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(modifiers, "modifiers");
    }
}
