package com.lixui.theme;

import com.lixui.graphics.Color;

import java.util.Objects;

/**
 * A state-aware visual style for a widget.
 * Background, text and border colors are resolved by the most specific interaction state.
 *
 * @param background idle background color
 * @param hoverBackground hovered background color
 * @param pressedBackground pressed background color
 * @param disabledBackground disabled background color
 * @param text idle text color
 * @param disabledText disabled text color
 * @param border idle border color
 * @param focusBorder focused border color
 * @param cornerRadius corner radius in logical UI units
 * @param padding inner padding in logical UI units
 * @param borderWidth border width in logical UI units
 */
public record WidgetStyle(Color background, Color hoverBackground, Color pressedBackground,
                          Color disabledBackground, Color text, Color disabledText,
                          Color border, Color focusBorder, float cornerRadius, float padding,
                          float borderWidth) {
    /**
     * Creates a widget style.
     *
     * @param background idle background color
     * @param hoverBackground hovered background color
     * @param pressedBackground pressed background color
     * @param disabledBackground disabled background color
     * @param text idle text color
     * @param disabledText disabled text color
     * @param border idle border color
     * @param focusBorder focused border color
     * @param cornerRadius corner radius in logical UI units
     * @param padding inner padding in logical UI units
     * @param borderWidth border width in logical UI units
     * @throws NullPointerException if a color is null
     * @throws IllegalArgumentException if a geometric value is negative or non-finite
     */
    public WidgetStyle {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(hoverBackground, "hoverBackground");
        Objects.requireNonNull(pressedBackground, "pressedBackground");
        Objects.requireNonNull(disabledBackground, "disabledBackground");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(disabledText, "disabledText");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(focusBorder, "focusBorder");
        if (!Float.isFinite(cornerRadius) || cornerRadius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");
        }
        if (!Float.isFinite(padding) || padding < 0) {
            throw new IllegalArgumentException("Padding must be finite and non-negative.");
        }
        if (!Float.isFinite(borderWidth) || borderWidth < 0) {
            throw new IllegalArgumentException("Border width must be finite and non-negative.");
        }
    }

    /**
     * Resolves the background color for a state.
     *
     * @param state interaction state
     * @return the state background color
     * @throws NullPointerException if {@code state} is null
     */
    public Color background(WidgetState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case NORMAL, FOCUSED -> background;
            case HOVERED -> hoverBackground;
            case PRESSED -> pressedBackground;
            case DISABLED -> disabledBackground;
        };
    }

    /**
     * Resolves the text color for a state.
     *
     * @param state interaction state
     * @return the state text color
     * @throws NullPointerException if {@code state} is null
     */
    public Color text(WidgetState state) {
        return Objects.requireNonNull(state, "state") == WidgetState.DISABLED ? disabledText : text;
    }

    /**
     * Resolves the border color for a state.
     *
     * @param state interaction state
     * @return the state border color
     * @throws NullPointerException if {@code state} is null
     */
    public Color border(WidgetState state) {
        return Objects.requireNonNull(state, "state") == WidgetState.FOCUSED ? focusBorder : border;
    }
}
