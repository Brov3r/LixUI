package com.lixui.text;

import java.util.Objects;

/**
 * Layout options for a wrapped or truncated text block.
 * A zero maximum width disables wrapping, a zero line height uses the font line height, and a zero
 * maximum line count keeps every generated line.
 *
 * @param maxWidth maximum line width in logical UI units, or zero for no wrapping
 * @param lineHeight explicit line height in logical UI units, or zero for the font line height
 * @param maxLines maximum number of visible lines, or zero for no limit
 * @param overflow behavior when generated lines exceed {@code maxLines}
 * @param horizontal horizontal alignment of every line
 * @param vertical vertical alignment of the whole block
 */
public record TextStyle(float maxWidth, float lineHeight, int maxLines, TextOverflow overflow,
                        HorizontalAlignment horizontal, VerticalAlignment vertical) {
    /** The default single-line text style aligned to the top-left corner. */
    public static final TextStyle DEFAULT = new TextStyle(
            0, 0, 0, TextOverflow.CLIP, HorizontalAlignment.LEFT, VerticalAlignment.TOP);

    /**
     * Creates text layout options.
     *
     * @param maxWidth maximum line width in logical UI units, or zero for no wrapping
     * @param lineHeight explicit line height in logical UI units, or zero for the font line height
     * @param maxLines maximum number of visible lines, or zero for no limit
     * @param overflow behavior when generated lines exceed {@code maxLines}
     * @param horizontal horizontal alignment of every line
     * @param vertical vertical alignment of the whole block
     * @throws IllegalArgumentException if a size or line count value is negative or non-finite
     * @throws NullPointerException if {@code overflow}, {@code horizontal} or {@code vertical} is null
     */
    public TextStyle {
        if (!Float.isFinite(maxWidth) || maxWidth < 0) {
            throw new IllegalArgumentException("Maximum text width must be finite and non-negative.");
        }
        if (!Float.isFinite(lineHeight) || lineHeight < 0) {
            throw new IllegalArgumentException("Text line height must be finite and non-negative.");
        }
        if (maxLines < 0) {
            throw new IllegalArgumentException("Maximum text line count must be non-negative.");
        }
        Objects.requireNonNull(overflow, "overflow");
        Objects.requireNonNull(horizontal, "horizontal");
        Objects.requireNonNull(vertical, "vertical");
    }

    /**
     * Creates a non-wrapping style with the supplied alignment.
     *
     * @param horizontal horizontal alignment of every line
     * @param vertical vertical alignment of the whole block
     * @return the text style
     * @throws NullPointerException if an alignment is null
     */
    public static TextStyle of(HorizontalAlignment horizontal, VerticalAlignment vertical) {
        return new TextStyle(0, 0, 0, TextOverflow.CLIP, horizontal, vertical);
    }
}
