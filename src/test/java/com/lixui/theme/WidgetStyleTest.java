package com.lixui.theme;

import com.lixui.graphics.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WidgetStyleTest {
    private static final Color BACKGROUND = Color.rgb(10, 20, 30);
    private static final Color HOVER = Color.rgb(20, 30, 40);
    private static final Color PRESSED = Color.rgb(30, 40, 50);
    private static final Color DISABLED = Color.rgb(40, 50, 60);
    private static final Color TEXT = Color.rgb(200, 210, 220);
    private static final Color DISABLED_TEXT = Color.rgb(100, 110, 120);
    private static final Color BORDER = Color.rgb(60, 70, 80);
    private static final Color FOCUS = Color.rgb(70, 80, 90);

    @Test
    void resolvesColorsByState() {
        WidgetStyle style = style();
        Theme theme = Theme.dark();

        assertEquals(theme.surface(), theme.controlStyle().background(WidgetState.NORMAL));
        assertEquals(style.background(), style.background(WidgetState.FOCUSED));
        assertEquals(style.hoverBackground(), style.background(WidgetState.HOVERED));
        assertEquals(style.pressedBackground(), style.background(WidgetState.PRESSED));
        assertEquals(style.disabledBackground(), style.background(WidgetState.DISABLED));
        assertEquals(style.text(), style.text(WidgetState.NORMAL));
        assertEquals(style.disabledText(), style.text(WidgetState.DISABLED));
        assertEquals(style.border(), style.border(WidgetState.NORMAL));
        assertEquals(style.focusBorder(), style.border(WidgetState.FOCUSED));
    }

    @Test
    void themeProvidesControlPrimaryAndPanelStyles() {
        Theme theme = Theme.dark();

        assertEquals(theme.surface(), theme.controlStyle().background());
        assertEquals(theme.primary(), theme.primaryStyle().background());
        assertEquals(theme.surface(), theme.panelStyle().background());
        assertEquals(Theme.dark().controlStyle(), Theme.dark().controlStyle());
    }

    @Test
    void rejectsInvalidGeometryAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> new WidgetStyle(
                BACKGROUND, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT, BORDER, FOCUS,
                -1, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> new WidgetStyle(
                BACKGROUND, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT, BORDER, FOCUS,
                4, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new WidgetStyle(
                BACKGROUND, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT, BORDER, FOCUS,
                4, 4, -1));
        assertThrows(NullPointerException.class, () -> new WidgetStyle(
                null, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT, BORDER, FOCUS,
                4, 4, 1));
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY})
    void rejectsNonFiniteGeometry(float value) {
        assertThrows(IllegalArgumentException.class, () -> new WidgetStyle(
                BACKGROUND, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT, BORDER, FOCUS,
                value, 4, 1));
        assertThrows(IllegalArgumentException.class, () -> new WidgetStyle(
                BACKGROUND, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT, BORDER, FOCUS,
                4, value, 1));
        assertThrows(IllegalArgumentException.class, () -> new WidgetStyle(
                BACKGROUND, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT, BORDER, FOCUS,
                4, 4, value));
    }

    private static WidgetStyle style() {
        return new WidgetStyle(BACKGROUND, HOVER, PRESSED, DISABLED, TEXT, DISABLED_TEXT,
                BORDER, FOCUS, 4, 8, 1);
    }
}
