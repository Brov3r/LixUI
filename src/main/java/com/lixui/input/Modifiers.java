package com.lixui.input;

/**
 * Keyboard modifiers that were active when an input event occurred.
 *
 * @param shift left or right Shift
 * @param control left or right Control
 * @param alt left or right Alt
 * @param superKey left or right Super (Windows or Command)
 */
public record Modifiers(boolean shift, boolean control, boolean alt, boolean superKey) {
    /** No modifiers pressed. */
    public static final Modifiers NONE = new Modifiers(false, false, false, false);
}
