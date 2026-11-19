package com.lixui.text;

/**
 * Horizontal placement of a text block relative to its anchor point.
 * The anchor is the left edge, the horizontal center or the right edge of the text.
 */
public enum HorizontalAlignment {
    /** The anchor point is the left edge of the text. */
    LEFT,
    /** The anchor point is the horizontal center of the text. */
    CENTER,
    /** The anchor point is the right edge of the text. */
    RIGHT,
    /**
     * Lines are stretched to the wrapping width by distributing extra space between words.
     * The last line of each paragraph stays left-aligned, matching word processors. Justification
     * requires a positive wrapping width; without one the text behaves like {@link #LEFT}.
     */
    JUSTIFY
}
