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
                Color.hex("#12141A"),
                Color.hex("#1D2028"),
                Color.hex("#23262E"),
                Color.hex("#2C303A"),
                Color.hex("#5B8CFF"),
                Color.hex("#FFFFFF"),
                Color.hex("#F2F4F8"),
                Color.hex("#9AA3B2"),
                Color.hex("#343A46"),
                Color.hex("#A66CFF"));
    }

    /**
     * Creates the built-in light theme.
     *
     * @return the default light theme
     */
    public static Theme light() {
        return new Theme(
                Color.hex("#F4F6FA"),
                Color.hex("#FFFFFF"),
                Color.hex("#EEF1F7"),
                Color.hex("#E2E7F0"),
                Color.hex("#3B6EF5"),
                Color.hex("#FFFFFF"),
                Color.hex("#171A21"),
                Color.hex("#5D6675"),
                Color.hex("#D0D6E0"),
                Color.hex("#7B4DFF"));
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
