package com.lixui.input;

/**
 * A single input event produced by a windowing adapter or by the host application.
 * Coordinates are in framebuffer pixels and use a top-left origin.
 */
public sealed interface InputEvent permits PointerMoved, PointerEntered, PointerExited,
        PointerPressed, PointerReleased, PointerScrolled, KeyPressed, KeyReleased, CharTyped,
        FocusChanged {
}
