package com.lixui.text;

/**
 * A line decoration drawn along a run of text.
 */
public enum TextDecoration {
    /** No decoration. */
    NONE(false, false),
    /** A line below the baseline. */
    UNDERLINE(true, false),
    /** A line through the middle of the text. */
    STRIKETHROUGH(false, true),
    /** Both an underline and a strikethrough. */
    UNDERLINE_AND_STRIKETHROUGH(true, true);

    private final boolean underline;
    private final boolean strikethrough;

    TextDecoration(boolean underline, boolean strikethrough) {
        this.underline = underline;
        this.strikethrough = strikethrough;
    }

    /**
     * Reports whether the decoration draws a line below the baseline.
     *
     * @return true for underlines
     */
    public boolean hasUnderline() {
        return underline;
    }

    /**
     * Reports whether the decoration draws a line through the text.
     *
     * @return true for strikethroughs
     */
    public boolean hasStrikethrough() {
        return strikethrough;
    }
}
