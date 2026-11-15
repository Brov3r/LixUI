package com.lixui.graphics;

import java.util.HexFormat;
import java.util.Objects;

/**
 * An immutable sRGB color with straight (non-premultiplied) alpha.
 * Components are finite values in {@code [0, 1]}; packed RGBA uses {@code 0xRRGGBBAA}.
 *
 * @param r red component
 * @param g green component
 * @param b blue component
 * @param a opacity, where zero is transparent and one is opaque
 */
public record Color(float r, float g, float b, float a) {
    /** Fully transparent black. */
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    /** Opaque black. */
    public static final Color BLACK = new Color(0, 0, 0, 1);

    /** Opaque white. */
    public static final Color WHITE = new Color(1, 1, 1, 1);

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    /**
     * Creates a color from normalized components, treating negative zero as zero.
     *
     * @param r red component in {@code [0, 1]}
     * @param g green component in {@code [0, 1]}
     * @param b blue component in {@code [0, 1]}
     * @param a opacity in {@code [0, 1]}
     * @throws IllegalArgumentException if a component is non-finite or outside {@code [0, 1]}
     */
    public Color {
        r = validateComponent(r);
        g = validateComponent(g);
        b = validateComponent(b);
        a = validateComponent(a);
    }

    /**
     * Creates an opaque color from 8-bit RGB components.
     *
     * @param r red component in {@code [0, 255]}
     * @param g green component in {@code [0, 255]}
     * @param b blue component in {@code [0, 255]}
     * @return the opaque color
     * @throws IllegalArgumentException if a component is outside {@code [0, 255]}
     */
    public static Color rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    /**
     * Creates a color from 8-bit RGBA components.
     *
     * @param r red component in {@code [0, 255]}
     * @param g green component in {@code [0, 255]}
     * @param b blue component in {@code [0, 255]}
     * @param a opacity in {@code [0, 255]}
     * @return the color with the specified opacity
     * @throws IllegalArgumentException if a component is outside {@code [0, 255]}
     */
    public static Color rgba(int r, int g, int b, int a) {
        return new Color(normalizeByte(r), normalizeByte(g), normalizeByte(b), normalizeByte(a));
    }

    /**
     * Creates an opaque color from normalized RGB components.
     *
     * @param r red component in {@code [0, 1]}
     * @param g green component in {@code [0, 1]}
     * @param b blue component in {@code [0, 1]}
     * @return the opaque color
     * @throws IllegalArgumentException if a component is non-finite or outside {@code [0, 1]}
     */
    public static Color rgb(float r, float g, float b) {
        return new Color(r, g, b, 1);
    }

    /**
     * Creates a color from normalized RGBA components.
     *
     * @param r red component in {@code [0, 1]}
     * @param g green component in {@code [0, 1]}
     * @param b blue component in {@code [0, 1]}
     * @param a opacity in {@code [0, 1]}
     * @return the color with the specified opacity
     * @throws IllegalArgumentException if a component is non-finite or outside {@code [0, 1]}
     */
    public static Color rgba(float r, float g, float b, float a) {
        return new Color(r, g, b, a);
    }

    /**
     * Creates an opaque color from packed {@code 0xRRGGBB}.
     *
     * @param rgb packed RGB with the most significant byte set to zero
     * @return the opaque color
     * @throws IllegalArgumentException if bits outside the lower 24 bits are set
     */
    public static Color fromRgb(int rgb) {
        if ((rgb & 0xFF000000) != 0) {
            throw new IllegalArgumentException("Packed RGB must use only the lower 24 bits.");
        }
        return fromRgba((rgb << 8) | 0xFF);
    }

    /**
     * Creates a color from packed {@code 0xRRGGBBAA}.
     *
     * @param rgba packed RGBA, interpreted as an unsigned bit pattern
     * @return the unpacked color
     */
    public static Color fromRgba(int rgba) {
        return rgba(rgba >>> 24, (rgba >>> 16) & 0xFF, (rgba >>> 8) & 0xFF, rgba & 0xFF);
    }

    /**
     * Parses RGB, RGBA, RRGGBB or RRGGBBAA hexadecimal notation with an optional leading {@code #}.
     * Digits are case-insensitive ASCII; whitespace and {@code 0x} prefixes are not accepted.
     * Short-form digits are expanded by repetition, and omitted alpha is opaque.
     *
     * @param hex the hexadecimal color
     * @return the parsed color
     * @throws NullPointerException if {@code hex} is null
     * @throws IllegalArgumentException if the length or any digit is invalid
     */
    public static Color hex(String hex) {
        Objects.requireNonNull(hex, "hex");
        int start = hex.startsWith("#") ? 1 : 0;
        int length = hex.length() - start;
        if (length != 3 && length != 4 && length != 6 && length != 8) {
            throw new IllegalArgumentException("HEX color must contain 3, 4, 6 or 8 digits.");
        }

        boolean shorthand = length <= 4;
        int packed = 0;
        for (int i = start; i < hex.length(); i++) {
            int digit = HexFormat.fromHexDigit(hex.charAt(i));
            packed = shorthand ? (packed << 8) | (digit * 17) : (packed << 4) | digit;
        }
        if (length == 3 || length == 6) {
            packed = (packed << 8) | 0xFF;
        }
        return fromRgba(packed);
    }

    /**
     * Returns a color with unchanged RGB components and a new opacity.
     *
     * @param alpha opacity in {@code [0, 1]}
     * @return the color with the requested opacity
     * @throws IllegalArgumentException if alpha is non-finite or outside {@code [0, 1]}
     */
    public Color withAlpha(float alpha) {
        return new Color(r, g, b, alpha);
    }

    /**
     * Packs RGB into {@code 0xRRGGBB}, rounding to the nearest 8-bit values and discarding alpha.
     *
     * @return packed RGB with the most significant byte set to zero
     */
    public int toRgb() {
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /**
     * Packs RGBA into {@code 0xRRGGBBAA}, rounding to the nearest 8-bit values.
     *
     * @return packed RGBA, which may be negative as a signed Java integer
     */
    public int toRgba() {
        return (toRgb() << 8) | Math.round(a * 255);
    }

    /**
     * Formats the color as uppercase {@code #RRGGBBAA}, rounding components to 8-bit values.
     *
     * @return the hexadecimal color, including alpha and leading zeroes
     */
    public String toHex() {
        return "#" + HEX.toHexDigits(toRgba());
    }

    private static float validateComponent(float value) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException("Color components must be finite and between 0 and 1.");
        }
        return value == 0 ? 0 : value;
    }

    private static float normalizeByte(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Color components must be between 0 and 255.");
        }
        return value / 255.0f;
    }
}
