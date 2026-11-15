package com.lixui.text;

/**
 * A laid-out line of text relative to the block origin.
 *
 * @param start UTF-16 index of the first visible character in the source text
 * @param end UTF-16 index after the last visible character in the source text
 * @param x horizontal line position relative to the block origin
 * @param y vertical line position relative to the block origin
 * @param width visible line width in logical UI units
 */
public record TextLine(int start, int end, float x, float y, float width) {
    /**
     * Creates a text line.
     *
     * @param start UTF-16 index of the first visible character in the source text
     * @param end UTF-16 index after the last visible character in the source text
     * @param x horizontal line position relative to the block origin
     * @param y vertical line position relative to the block origin
     * @param width visible line width in logical UI units
     * @throws IllegalArgumentException if an index is negative, {@code end} is less than
     *                                  {@code start}, or a coordinate is non-finite
     */
    public TextLine {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Text line indexes are invalid.");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || width < 0) {
            throw new IllegalArgumentException("Text line geometry must be finite.");
        }
    }
}
