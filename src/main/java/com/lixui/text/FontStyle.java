package com.lixui.text;

/**
 * Synthetic style applied while a font is rasterized.
 * TrueType outlines are baked into the atlas, so bold thickens glyph strokes to the right and
 * italic shears glyphs to the right above the baseline. The style does not change advances or
 * metrics, which keeps text measurement stable between styles.
 */
public enum FontStyle {
    /** Upright glyphs with the original stroke weight. */
    REGULAR,
    /** Glyphs with thickened coverage. */
    BOLD,
    /** Glyphs sheared to the right above the baseline. */
    ITALIC,
    /** Glyphs that are both thickened and sheared. */
    BOLD_ITALIC;

    boolean bold() {
        return this == BOLD || this == BOLD_ITALIC;
    }

    boolean italic() {
        return this == ITALIC || this == BOLD_ITALIC;
    }
}
