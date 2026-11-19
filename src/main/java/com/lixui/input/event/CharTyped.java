package com.lixui.input.event;

/**
 * A Unicode code point was produced by text input.
 *
 * @param codepoint the code point typed by the user
 */
public record CharTyped(int codepoint) implements InputEvent {
}
