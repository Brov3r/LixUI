package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.render.Viewport;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycapTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void downStateUsesThePressedSurface() {
        TestBackend backend = new TestBackend();
        WidgetStyle style = new WidgetStyle(
                Color.rgb(10, 20, 30), Color.rgb(40, 50, 60), Color.rgb(70, 80, 90),
                Color.rgb(100, 110, 120), Color.rgb(130, 140, 150), Color.rgb(160, 170, 180),
                Color.rgb(190, 200, 210), Color.rgb(220, 230, 240), 6, 8, 1);
        try (WidgetManager manager = manager(backend)) {
            Keycap keycap = new Keycap("Ctrl");
            keycap.setStyle(style);
            keycap.setBounds(new Rect(10, 20, 80, 40));
            manager.add(keycap);

            keycap.setDown(true);
            manager.render();

            RoundedRectCommand background = assertInstanceOf(RoundedRectCommand.class,
                    backend.lastDrawList().commands().getFirst());
            assertEquals(style.pressedBackground(), background.color());
        }
    }

    @Test
    void clickableKeycapActivatesAndRequestsThePointerCursor() {
        Keycap keycap = new Keycap("K");
        int[] actions = {0};
        keycap.setOnAction(() -> actions[0]++);

        keycap.click();

        assertEquals(1, actions[0]);
        assertTrue(keycap.isFocusable());
        assertEquals(Cursor.POINTER, keycap.cursor());
    }

    @Test
    void decorativeKeycapIgnoresPointerActivationAndFocus() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Keycap keycap = new Keycap("K");
            keycap.setInteractive(false);
            keycap.setBounds(new Rect(10, 20, 80, 40));
            int[] actions = {0};
            keycap.setOnAction(() -> actions[0]++);
            manager.add(keycap);

            manager.handle(new PointerPressed(20, 30, MouseButton.LEFT, Modifiers.NONE));
            manager.handle(new PointerReleased(20, 30, MouseButton.LEFT, Modifiers.NONE));

            assertEquals(0, actions[0]);
            assertFalse(keycap.isFocusable());
            assertFalse(keycap.isFocused());
            assertEquals(Cursor.DEFAULT, keycap.cursor());
        }
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
