package com.lixui.input;

import java.util.Objects;

/**
 * A keyboard key was released.
 *
 * @param key released key
 * @param modifiers active keyboard modifiers
 */
public record KeyReleased(Key key, Modifiers modifiers) implements InputEvent {
    /**
     * Creates a key release event.
     *
     * @param key released key
     * @param modifiers active keyboard modifiers
     * @throws NullPointerException if {@code key} or {@code modifiers} is null
     */
    public KeyReleased {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(modifiers, "modifiers");
    }
}
