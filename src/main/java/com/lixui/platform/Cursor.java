package com.lixui.platform;

/**
 * A platform-neutral pointer cursor shape requested by the UI.
 */
public enum Cursor {
    /** The default arrow cursor. */
    DEFAULT,
    /** A pointing hand cursor used for clickable controls. */
    POINTER,
    /** A text insertion cursor used for editable fields. */
    TEXT,
    /** A crosshair cursor used for precise selection. */
    CROSSHAIR,
    /** A move cursor used for draggable content. */
    MOVE,
    /** A horizontal resize cursor. */
    RESIZE_HORIZONTAL,
    /** A vertical resize cursor. */
    RESIZE_VERTICAL,
    /** A diagonal resize cursor pointing from the top-left to the bottom-right. */
    RESIZE_DIAGONAL,
    /** A diagonal resize cursor pointing from the top-right to the bottom-left. */
    RESIZE_DIAGONAL_INVERSE,
    /** A cursor showing that the current operation is not allowed. */
    NOT_ALLOWED
}
