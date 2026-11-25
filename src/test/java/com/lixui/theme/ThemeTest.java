package com.lixui.theme;

import com.lixui.graphics.Color;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeTest {
    @Test
    void builtInThemesDefineEverySemanticColor() {
        assertComplete(Theme.dark());
        assertComplete(Theme.light());
        assertComplete(Theme.dracula());
        assertComplete(Theme.catppuccinLatte());
        assertComplete(Theme.catppuccinFrappe());
        assertComplete(Theme.catppuccinMacchiato());
        assertComplete(Theme.catppuccinMocha());
        assertComplete(Theme.nord());
        assertComplete(Theme.gruvboxDark());
        assertNotEquals(Theme.dark().background(), Theme.light().background());
    }

    @Test
    void recordsEqualThemesByValue() {
        assertEquals(Theme.dark(), Theme.dark());
        assertEquals(Theme.light(), Theme.light());
        assertEquals(Theme.dracula(), Theme.dracula());
    }

    @Test
    void rejectsNullColors() {
        Theme theme = Theme.dark();

        assertThrows(NullPointerException.class, () -> new Theme(
                null, theme.surface(), theme.surfaceHover(), theme.surfacePressed(),
                theme.primary(), theme.onPrimary(), theme.text(), theme.textMuted(),
                theme.border(), theme.focusRing()));
    }

    @Test
    void withCopiesUntouchedRoles() {
        Theme base = Theme.dracula();
        Color primary = Color.hex("#FF9E64");

        Theme custom = base.withPrimary(primary);

        assertNotSame(base, custom);
        assertEquals(primary, custom.primary());
        assertEquals(base.background(), custom.background());
        assertEquals(base.surface(), custom.surface());
        assertEquals(base.surfaceHover(), custom.surfaceHover());
        assertEquals(base.surfacePressed(), custom.surfacePressed());
        assertEquals(base.onPrimary(), custom.onPrimary());
        assertEquals(base.text(), custom.text());
        assertEquals(base.textMuted(), custom.textMuted());
        assertEquals(base.border(), custom.border());
        assertEquals(base.focusRing(), custom.focusRing());
        assertEquals(Theme.dracula(), base);
    }

    @Test
    void withMethodsReplaceEveryRole() {
        Color background = Color.hex("#101010");
        Color surface = Color.hex("#202020");
        Color surfaceHover = Color.hex("#303030");
        Color surfacePressed = Color.hex("#404040");
        Color primary = Color.hex("#505050");
        Color onPrimary = Color.hex("#606060");
        Color text = Color.hex("#707070");
        Color textMuted = Color.hex("#808080");
        Color border = Color.hex("#909090");
        Color focusRing = Color.hex("#A0A0A0");

        Theme base = Theme.dark();
        Theme custom = base
                .withBackground(background)
                .withSurface(surface)
                .withSurfaceHover(surfaceHover)
                .withSurfacePressed(surfacePressed)
                .withPrimary(primary)
                .withOnPrimary(onPrimary)
                .withText(text)
                .withTextMuted(textMuted)
                .withBorder(border)
                .withFocusRing(focusRing);

        assertEquals(background, custom.background());
        assertEquals(surface, custom.surface());
        assertEquals(surfaceHover, custom.surfaceHover());
        assertEquals(surfacePressed, custom.surfacePressed());
        assertEquals(primary, custom.primary());
        assertEquals(onPrimary, custom.onPrimary());
        assertEquals(text, custom.text());
        assertEquals(textMuted, custom.textMuted());
        assertEquals(border, custom.border());
        assertEquals(focusRing, custom.focusRing());
        assertEquals(Theme.dark(), base);
    }

    @Test
    void withMethodsRejectNullColors() {
        Theme theme = Theme.dark();

        assertThrows(NullPointerException.class, () -> theme.withBackground(null));
        assertThrows(NullPointerException.class, () -> theme.withSurface(null));
        assertThrows(NullPointerException.class, () -> theme.withSurfaceHover(null));
        assertThrows(NullPointerException.class, () -> theme.withSurfacePressed(null));
        assertThrows(NullPointerException.class, () -> theme.withPrimary(null));
        assertThrows(NullPointerException.class, () -> theme.withOnPrimary(null));
        assertThrows(NullPointerException.class, () -> theme.withText(null));
        assertThrows(NullPointerException.class, () -> theme.withTextMuted(null));
        assertThrows(NullPointerException.class, () -> theme.withBorder(null));
        assertThrows(NullPointerException.class, () -> theme.withFocusRing(null));
    }

    @Test
    void presetsKeepContentReadable() {
        Map<String, Theme> presets = Map.of(
                "Dracula", Theme.dracula(),
                "Catppuccin Latte", Theme.catppuccinLatte(),
                "Catppuccin Frappé", Theme.catppuccinFrappe(),
                "Catppuccin Macchiato", Theme.catppuccinMacchiato(),
                "Catppuccin Mocha", Theme.catppuccinMocha(),
                "Nord", Theme.nord(),
                "Gruvbox Dark", Theme.gruvboxDark());

        presets.forEach((name, theme) -> {
            assertContrast(name, "text on background", theme.text(), theme.background(), 4.5);
            assertContrast(name, "text on surface", theme.text(), theme.surface(), 4.5);
            assertContrast(name, "muted text on surface", theme.textMuted(), theme.surface(), 4.5);
            assertContrast(name, "muted text on hover", theme.textMuted(), theme.surfaceHover(), 4.5);
            assertContrast(name, "muted text on press", theme.textMuted(), theme.surfacePressed(), 4.5);
            assertContrast(name, "accent on surface", theme.primary(), theme.surface(), 4.5);
            assertContrast(name, "content on accent", theme.onPrimary(), theme.primary(), 4.5);
            assertContrast(name, "focus ring on surface", theme.focusRing(), theme.surface(), 3.0);
        });
    }

    @Test
    void presetsDistinguishInteractionStates() {
        Map<String, Theme> presets = Map.of(
                "Dracula", Theme.dracula(),
                "Catppuccin Latte", Theme.catppuccinLatte(),
                "Catppuccin Frappé", Theme.catppuccinFrappe(),
                "Catppuccin Macchiato", Theme.catppuccinMacchiato(),
                "Catppuccin Mocha", Theme.catppuccinMocha(),
                "Nord", Theme.nord(),
                "Gruvbox Dark", Theme.gruvboxDark());

        presets.forEach((name, theme) -> {
            assertNotEquals(theme.surface(), theme.surfaceHover(), name + " hover equals surface");
            assertNotEquals(theme.surface(), theme.surfacePressed(), name + " press equals surface");
            assertNotEquals(theme.surfaceHover(), theme.surfacePressed(),
                    name + " hover equals press");
            assertTrue(contrast(theme.surface(), theme.surfaceHover()) > 1.03,
                    name + " hover is not distinguishable from surface");
            assertTrue(contrast(theme.surface(), theme.surfacePressed()) > 1.1,
                    name + " press is not distinguishable from surface");
            assertTrue(contrast(theme.border(), theme.surface()) > 1.2,
                    name + " border is not distinguishable from surface");
        });
    }

    private static void assertComplete(Theme theme) {
        assertNotNull(theme.background());
        assertNotNull(theme.surface());
        assertNotNull(theme.surfaceHover());
        assertNotNull(theme.surfacePressed());
        assertNotNull(theme.primary());
        assertNotNull(theme.onPrimary());
        assertNotNull(theme.text());
        assertNotNull(theme.textMuted());
        assertNotNull(theme.border());
        assertNotNull(theme.focusRing());
    }

    private static void assertContrast(String theme, String role, Color foreground,
                                       Color background, double minimum) {
        double ratio = contrast(foreground, background);
        assertTrue(ratio >= minimum,
                () -> theme + ": " + role + " contrast " + ratio + " is below " + minimum);
    }

    private static double contrast(Color first, Color second) {
        double lighter = Math.max(luminance(first), luminance(second));
        double darker = Math.min(luminance(first), luminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.r()) + 0.7152 * linear(color.g()) + 0.0722 * linear(color.b());
    }

    private static double linear(float channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
