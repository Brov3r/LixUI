package com.lixui.text;

/**
 * How text that exceeds the available lines or width is handled.
 */
public enum TextOverflow {
    /** Draw only the text that fits and omit the remainder. */
    CLIP,
    /** Replace the end of the last visible line with an ellipsis. */
    ELLIPSIS
}
