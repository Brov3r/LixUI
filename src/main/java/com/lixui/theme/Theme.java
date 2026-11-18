package com.lixui.theme;

import com.lixui.graphics.Color;

import java.util.Objects;

/**
 * Semantic colors used by widgets and custom draw code.
 * A theme is immutable and can be replaced at runtime through the widget manager.
 *
 * @param background application background color
 * @param surface default control and panel surface color
 * @param surfaceHover surface color while hovered
 * @param surfacePressed surface color while pressed
 * @param primary accent color used by interactive controls
 * @param onPrimary content color drawn on top of {@link #primary}
 * @param text primary text color
 * @param textMuted secondary text color
 * @param border border and separator color
 * @param focusRing focus indicator color
 */
public record Theme(Color background, Color surface, Color surfaceHover, Color surfacePressed,
                    Color primary, Color onPrimary, Color text, Color textMuted,
                    Color border, Color focusRing) {
    /**
     * Creates a theme with every color defined.
     *
     * @param background application background color
     * @param surface default control and panel surface color
     * @param surfaceHover surface color while hovered
     * @param surfacePressed surface color while pressed
     * @param primary accent color used by interactive controls
     * @param onPrimary content color drawn on top of {@link #primary}
     * @param text primary text color
     * @param textMuted secondary text color
     * @param border border and separator color
     * @param focusRing focus indicator color
     * @throws NullPointerException if any color is null
     */
    public Theme {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(surfaceHover, "surfaceHover");
        Objects.requireNonNull(surfacePressed, "surfacePressed");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(onPrimary, "onPrimary");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(textMuted, "textMuted");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(focusRing, "focusRing");
    }

    /**
     * Creates the built-in dark theme.
     *
     * @return the default dark theme
     */
    public static Theme dark() {
        return new Theme(
                Color.hex("#1A1A1A"),
                Color.hex("#222222"),
                Color.hex("#2A2A2A"),
                Color.hex("#2E2E2E"),
                Color.hex("#9BD1C1"),
                Color.hex("#1A1A1A"),
                Color.hex("#ECECEC"),
                Color.hex("#B9B9B9"),
                Color.hex("#3A3A3A"),
                Color.hex("#BCE3D5"));
    }

    /**
     * Creates the built-in light theme.
     *
     * @return the default light theme
     */
    public static Theme light() {
        return new Theme(
                Color.hex("#FAF7F0"),
                Color.hex("#FCFCFC"),
                Color.hex("#F5F5F0"),
                Color.hex("#EDEDEB"),
                Color.hex("#9BD1C1"),
                Color.hex("#1A1A1A"),
                Color.hex("#29292B"),
                Color.hex("#5C6673"),
                Color.hex("#DBDBDE"),
                Color.hex("#4FC778"));
    }

    /**
     * Returns the default style for an interactive control.
     *
     * @return a state-aware control style
     */
    public WidgetStyle controlStyle() {
        return new WidgetStyle(surface, surfaceHover, surfacePressed,
                surface.withAlpha(0.6f), text, textMuted.withAlpha(0.6f),
                border, focusRing, 6, 8, 1);
    }

    /**
     * Returns the default style for a primary action.
     *
     * @return a state-aware accent style
     */
    public WidgetStyle primaryStyle() {
        return new WidgetStyle(primary, primary.withAlpha(0.9f), primary.withAlpha(0.8f),
                surface.withAlpha(0.6f), onPrimary, textMuted.withAlpha(0.6f),
                primary, focusRing, 6, 8, 1);
    }

    /**
     * Returns the default style for a panel or card.
     *
     * @return a state-aware panel style
     */
    public WidgetStyle panelStyle() {
        return new WidgetStyle(surface, surface, surface, surface,
                text, textMuted, border, focusRing, 8, 12, 1);
    }
}
