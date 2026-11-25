package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeybindTest {
    private static final Rect BOUNDS = new Rect(10, 10, 220, 36);

    @Test
    void capturesAKeyWithModifiersAndFormatsIt() {
        WidgetManager manager = manager();
        Keybind field = field();
        List<Keybind.Binding> changes = new ArrayList<>();
        field.setOnChange(changes::add);
        manager.add(field);

        click(manager, 20, 20);
        assertTrue(field.isCapturing());

        manager.handle(new KeyPressed(Key.K,
                new Modifiers(true, true, false, false), false));

        assertFalse(field.isCapturing());
        assertEquals(Key.K, field.binding().key());
        assertTrue(field.binding().modifiers().control());
        assertTrue(field.binding().modifiers().shift());
        assertEquals("Ctrl+Shift+K", field.text());
        assertEquals(List.of(field.binding()), changes);
        assertSame(field, manager.focused());
    }

    @Test
    void escapeCancelsCaptureAndKeepsThePreviousBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        Keybind.Binding original = new Keybind.Binding(Key.K,
                new Modifiers(false, true, false, false));
        field.setBinding(original);
        List<Keybind.Binding> changes = new ArrayList<>();
        field.setOnChange(changes::add);
        manager.add(field);

        click(manager, 20, 20);
        assertTrue(field.isCapturing());
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(field.isCapturing());
        assertEquals(original, field.binding());
        assertEquals("Ctrl+K", field.text());
        assertTrue(changes.isEmpty());
    }

    @Test
    void modifierKeysAloneDoNotCompleteTheBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        manager.add(field);

        click(manager, 20, 20);
        manager.handle(new KeyPressed(Key.CONTROL_LEFT,
                new Modifiers(false, true, false, false), false));

        assertTrue(field.isCapturing());
        assertNull(field.binding());

        manager.handle(new KeyPressed(Key.K,
                new Modifiers(false, true, false, false), false));

        assertFalse(field.isCapturing());
        assertEquals("Ctrl+K", field.text());
    }

    @Test
    void ignoresAutoRepeatUntilAFreshKeyPress() {
        WidgetManager manager = manager();
        Keybind field = field();
        manager.add(field);
        manager.handle(new KeyPressed(Key.K, Modifiers.NONE, false));

        click(manager, 20, 20);
        assertTrue(field.isCapturing());

        manager.handle(new KeyPressed(Key.K, Modifiers.NONE, true));
        manager.handle(new KeyPressed(Key.K, Modifiers.NONE, true));

        assertTrue(field.isCapturing());
        assertNull(field.binding());

        manager.handle(new KeyPressed(Key.K, Modifiers.NONE, false));

        assertFalse(field.isCapturing());
        assertEquals(Key.K, field.binding().key());
    }

    @Test
    void tabEnterAndSpaceAreCapturedInsteadOfActivatingNavigation() {
        WidgetManager manager = manager();
        Keybind field = field();
        manager.add(field);

        for (Key key : List.of(Key.TAB, Key.ENTER, Key.SPACE)) {
            click(manager, 20, 20);
            manager.handle(new KeyPressed(key, Modifiers.NONE, false));

            assertFalse(field.isCapturing());
            assertEquals(key, field.binding().key());
            assertSame(field, manager.focused());
            field.clearBinding();
        }
    }

    @Test
    void focusLossCancelsCaptureWithoutChangingTheBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        Keybind.Binding original = new Keybind.Binding(Key.E, Modifiers.NONE);
        field.setBinding(original);
        List<Keybind.Binding> changes = new ArrayList<>();
        field.setOnChange(changes::add);
        FocusProbe other = new FocusProbe();
        other.setBounds(new Rect(300, 10, 120, 36));
        manager.add(field).add(other);

        click(manager, 20, 20);
        assertTrue(field.isCapturing());
        manager.focus(other);

        assertFalse(field.isCapturing());
        assertEquals(original, field.binding());
        assertSame(other, manager.focused());
        assertTrue(changes.isEmpty());
    }

    @Test
    void clearCrossRemovesTheBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        field.setBinding(new Keybind.Binding(Key.F5, Modifiers.NONE));
        List<Keybind.Binding> changes = new ArrayList<>();
        field.setOnChange(changes::add);
        manager.add(field);

        click(manager, BOUNDS.x() + BOUNDS.width() - 16, BOUNDS.y() + BOUNDS.height() / 2);

        assertNull(field.binding());
        assertFalse(field.isCapturing());
        assertEquals("", field.text());
        assertEquals(1, changes.size());
        assertNull(changes.get(0));
    }

    @Test
    void formatsKeyNamesAndRejectsModifierBindings() {
        assertEquals("Ctrl+Shift+K", Keybind.format(Key.K,
                new Modifiers(true, true, false, false)));
        assertEquals("Page Up", Keybind.keyName(Key.PAGE_UP));
        assertEquals("Num Enter", Keybind.keyName(Key.KEYPAD_ENTER));
        assertEquals("1", Keybind.keyName(Key.DIGIT_1));
        assertEquals("F12", Keybind.keyName(Key.F12));
        assertEquals("Esc", Keybind.keyName(Key.ESCAPE));
        assertThrows(IllegalArgumentException.class,
                () -> new Keybind.Binding(Key.CONTROL_LEFT, Modifiers.NONE));
    }

    private static Keybind field() {
        Keybind field = new Keybind();
        field.setBounds(BOUNDS);
        return field;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }

    private static final class FocusProbe extends Widget {
        private FocusProbe() {
            setFocusable(true);
        }
    }
}
