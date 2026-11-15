package com.lixui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThemeTest {
    @Test
    void builtInThemesDefineEverySemanticColor() {
        assertComplete(Theme.dark());
        assertComplete(Theme.light());
        assertNotEquals(Theme.dark().background(), Theme.light().background());
    }

    @Test
    void recordsEqualThemesByValue() {
        assertEquals(Theme.dark(), Theme.dark());
        assertEquals(Theme.light(), Theme.light());
    }

    @Test
    void rejectsNullColors() {
        Theme theme = Theme.dark();

        assertThrows(NullPointerException.class, () -> new Theme(
                null, theme.surface(), theme.surfaceHover(), theme.surfacePressed(),
                theme.primary(), theme.onPrimary(), theme.text(), theme.textMuted(),
                theme.border(), theme.focusRing()));
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
}
