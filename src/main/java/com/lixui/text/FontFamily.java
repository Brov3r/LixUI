package com.lixui.text;

import java.util.Objects;

/**
 * A set of font faces that share a size and metrics but differ in weight and slant.
 * Missing variants fall back to the closest available face, so a family with only a regular face
 * still resolves every style.
 *
 * @param regular regular face, required
 * @param bold bold face, or null to fall back to the regular face
 * @param italic italic face, or null to fall back to the regular face
 * @param boldItalic bold italic face, or null to fall back to the bold or italic face
 */
public record FontFamily(Font regular, Font bold, Font italic, Font boldItalic) {
    /**
     * Creates a font family.
     *
     * @param regular regular face
     * @param bold bold face, or null when unavailable
     * @param italic italic face, or null when unavailable
     * @param boldItalic bold italic face, or null when unavailable
     * @throws NullPointerException if {@code regular} is null
     */
    public FontFamily {
        Objects.requireNonNull(regular, "regular");
    }

    /**
     * Creates a family with only a regular face.
     *
     * @param regular regular face
     * @return a family that resolves every style to the regular face
     * @throws NullPointerException if {@code regular} is null
     */
    public static FontFamily of(Font regular) {
        return new FontFamily(regular, null, null, null);
    }

    /**
     * Selects the face for a style using the available fallbacks.
     *
     * @param style requested style
     * @return the closest available face, never null
     * @throws NullPointerException if {@code style} is null
     */
    public Font resolve(FontStyle style) {
        return switch (Objects.requireNonNull(style, "style")) {
            case REGULAR -> regular;
            case BOLD -> bold != null ? bold : regular;
            case ITALIC -> italic != null ? italic : regular;
            case BOLD_ITALIC -> {
                if (boldItalic != null) {
                    yield boldItalic;
                }
                if (bold != null) {
                    yield bold;
                }
                yield italic != null ? italic : regular;
            }
        };
    }
}
