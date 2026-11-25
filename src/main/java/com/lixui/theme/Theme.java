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
     * Creates the Dracula dark palette.
     *
     * @return a theme based on the Dracula colors
     */
    public static Theme dracula() {
        return new Theme(
                Color.hex("#282A36"),
                Color.hex("#343746"),
                Color.hex("#44475A"),
                Color.hex("#21222C"),
                Color.hex("#BD93F9"),
                Color.hex("#282A36"),
                Color.hex("#F8F8F2"),
                Color.hex("#B0B9D6"),
                Color.hex("#44475A"),
                Color.hex("#FF79C6"));
    }

    /**
     * Creates the Catppuccin Latte light palette.
     *
     * @return a theme based on the Catppuccin Latte colors
     */
    public static Theme catppuccinLatte() {
        return new Theme(
                Color.hex("#E6E9EF"),
                Color.hex("#EFF1F5"),
                Color.hex("#E3E6EC"),
                Color.hex("#DCE0E8"),
                Color.hex("#8839EF"),
                Color.hex("#EFF1F5"),
                Color.hex("#4C4F69"),
                Color.hex("#5C5F77"),
                Color.hex("#BCC0CC"),
                Color.hex("#1E66F5"));
    }

    /**
     * Creates the Catppuccin Frappé dark palette.
     *
     * @return a theme based on the Catppuccin Frappé colors
     */
    public static Theme catppuccinFrappe() {
        return new Theme(
                Color.hex("#303446"),
                Color.hex("#414559"),
                Color.hex("#474B60"),
                Color.hex("#292C3C"),
                Color.hex("#BABBF1"),
                Color.hex("#303446"),
                Color.hex("#C6D0F5"),
                Color.hex("#B5BFE2"),
                Color.hex("#51576D"),
                Color.hex("#8CAAEE"));
    }

    /**
     * Creates the Catppuccin Macchiato dark palette.
     *
     * @return a theme based on the Catppuccin Macchiato colors
     */
    public static Theme catppuccinMacchiato() {
        return new Theme(
                Color.hex("#24273A"),
                Color.hex("#363A4F"),
                Color.hex("#41455C"),
                Color.hex("#1E2030"),
                Color.hex("#C6A0F6"),
                Color.hex("#24273A"),
                Color.hex("#CAD3F5"),
                Color.hex("#B8C0E0"),
                Color.hex("#494D64"),
                Color.hex("#8AADF4"));
    }

    /**
     * Creates the Catppuccin Mocha dark palette.
     *
     * @return a theme based on the Catppuccin Mocha colors
     */
    public static Theme catppuccinMocha() {
        return new Theme(
                Color.hex("#1E1E2E"),
                Color.hex("#313244"),
                Color.hex("#3E4053"),
                Color.hex("#181825"),
                Color.hex("#CBA6F7"),
                Color.hex("#1E1E2E"),
                Color.hex("#CDD6F4"),
                Color.hex("#BAC2DE"),
                Color.hex("#45475A"),
                Color.hex("#89B4FA"));
    }

    /**
     * Creates the Nord dark palette.
     *
     * @return a theme based on the Nord colors
     */
    public static Theme nord() {
        return new Theme(
                Color.hex("#2E3440"),
                Color.hex("#3B4252"),
                Color.hex("#434C5E"),
                Color.hex("#2E3440"),
                Color.hex("#88C0D0"),
                Color.hex("#2E3440"),
                Color.hex("#ECEFF4"),
                Color.hex("#B3BCCE"),
                Color.hex("#4C566A"),
                Color.hex("#8FBCBB"));
    }

    /**
     * Creates the Gruvbox Dark palette.
     *
     * @return a theme based on the Gruvbox Dark colors
     */
    public static Theme gruvboxDark() {
        return new Theme(
                Color.hex("#282828"),
                Color.hex("#3C3836"),
                Color.hex("#504945"),
                Color.hex("#1D2021"),
                Color.hex("#FABD2F"),
                Color.hex("#282828"),
                Color.hex("#EBDBB2"),
                Color.hex("#CCBEA4"),
                Color.hex("#665C54"),
                Color.hex("#FE8019"));
    }

    /**
     * Returns a copy of this theme with a different background color.
     *
     * @param background application background color
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code background} is null
     */
    public Theme withBackground(Color background) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different surface color.
     *
     * @param surface default control and panel surface color
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code surface} is null
     */
    public Theme withSurface(Color surface) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different hovered surface color.
     *
     * @param surfaceHover surface color while hovered
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code surfaceHover} is null
     */
    public Theme withSurfaceHover(Color surfaceHover) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different pressed surface color.
     *
     * @param surfacePressed surface color while pressed
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code surfacePressed} is null
     */
    public Theme withSurfacePressed(Color surfacePressed) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different accent color.
     *
     * @param primary accent color used by interactive controls
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code primary} is null
     */
    public Theme withPrimary(Color primary) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different color for content drawn on the accent.
     *
     * @param onPrimary content color drawn on top of {@link #primary}
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code onPrimary} is null
     */
    public Theme withOnPrimary(Color onPrimary) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different primary text color.
     *
     * @param text primary text color
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code text} is null
     */
    public Theme withText(Color text) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different secondary text color.
     *
     * @param textMuted secondary text color
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code textMuted} is null
     */
    public Theme withTextMuted(Color textMuted) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different border color.
     *
     * @param border border and separator color
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code border} is null
     */
    public Theme withBorder(Color border) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
    }

    /**
     * Returns a copy of this theme with a different focus indicator color.
     *
     * @param focusRing focus indicator color
     * @return a new theme with all other roles copied from this theme
     * @throws NullPointerException if {@code focusRing} is null
     */
    public Theme withFocusRing(Color focusRing) {
        return new Theme(background, surface, surfaceHover, surfacePressed,
                primary, onPrimary, text, textMuted, border, focusRing);
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
