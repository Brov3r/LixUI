package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.render.Viewport;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopupTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void opensBelowTheAnchorAndClosesOnOutsidePress() {
        WidgetManager manager = manager();
        Popup popup = new Popup();
        Widget content = new Widget() { };
        popup.setContent(content);

        popup.open(manager.context(), new Rect(100, 100, 120, 30), 160, 80);
        manager.layout();

        assertTrue(popup.isOpen());
        assertEquals(new Rect(100, 132, 168, 88), popup.popupBounds());
        assertEquals(new Rect(104, 136, 160, 80), content.bounds());

        manager.handle(new PointerPressed(20, 20, MouseButton.LEFT, Modifiers.NONE));

        assertFalse(popup.isOpen());
        assertFalse(manager.widgets().contains(popup));
    }

    @Test
    void flipsAboveWhenThereIsNoRoomBelow() {
        WidgetManager manager = manager();
        Popup popup = new Popup();
        popup.open(manager.context(), new Rect(100, 540, 120, 30), 160, 80);
        manager.layout();

        assertEquals(new Rect(100, 450, 168, 88), popup.popupBounds());
    }

    @Test
    void closesOnEscapeAndRejectsInvalidArguments() {
        WidgetManager manager = manager();
        Popup popup = new Popup();
        popup.open(manager.context(), new Rect(100, 100, 120, 30), 160, 80);
        manager.layout();

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(popup.isOpen());
        assertThrows(NullPointerException.class,
                () -> popup.open(null, Rect.EMPTY, 10, 10));
        assertThrows(IllegalArgumentException.class,
                () -> popup.open(manager.context(), Rect.EMPTY, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> popup.setPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> popup.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> popup.setViewportMargin(-1));
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        Widget root = new Widget() { };
        root.setBounds(new Rect(0, 0, VIEWPORT.logicalWidth(), VIEWPORT.logicalHeight()));
        manager.add(root);
        return manager;
    }
}
