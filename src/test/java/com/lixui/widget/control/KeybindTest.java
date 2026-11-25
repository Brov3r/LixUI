package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.container.ScrollPanel;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void leftClickStartsCaptureWithoutChangingTheBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        Keybind.Binding original = new Keybind.Binding(Key.K, Modifiers.NONE);
        field.setBinding(original);
        List<Keybind.Binding> changes = new ArrayList<>();
        field.setOnChange(changes::add);
        manager.add(field);

        click(manager, 20, 20);

        assertTrue(field.isCapturing());
        assertEquals(original, field.binding());
        assertTrue(changes.isEmpty());
    }

    @Test
    void nextMousePressAssignsTheButtonInsteadOfClickingAgain() {
        WidgetManager manager = manager();
        Keybind field = field();
        List<Keybind.Binding> changes = new ArrayList<>();
        field.setOnChange(changes::add);
        manager.add(field);

        click(manager, 20, 20);
        assertTrue(field.isCapturing());
        assertNull(field.binding());

        press(manager, 20, 20, MouseButton.RIGHT, Modifiers.NONE);

        assertFalse(field.isCapturing());
        assertTrue(field.binding().isMouse());
        assertEquals(MouseButton.RIGHT, field.binding().mouseButton());
        assertNull(field.binding().key());
        assertEquals("Mouse Right", field.text());
        assertEquals(List.of(field.binding()), changes);

        release(manager, 20, 20, MouseButton.RIGHT, Modifiers.NONE);

        assertFalse(field.isCapturing());
        assertEquals("Mouse Right", field.text());
        assertEquals(List.of(field.binding()), changes);
    }

    @Test
    void mouseBindingKeepsTheModifiersPressedWithIt() {
        WidgetManager manager = manager();
        Keybind field = field();
        manager.add(field);
        Modifiers control = new Modifiers(false, true, false, false);

        click(manager, 20, 20);
        press(manager, 20, 20, MouseButton.LEFT, control);

        assertFalse(field.isCapturing());
        assertEquals(MouseButton.LEFT, field.binding().mouseButton());
        assertEquals(control, field.binding().modifiers());
        assertEquals("Ctrl+Mouse Left", field.text());

        release(manager, 20, 20, MouseButton.LEFT, control);

        assertFalse(field.isCapturing());
        assertEquals("Ctrl+Mouse Left", field.text());
    }

    @Test
    void escapeCancelsMouseCaptureAndKeepsThePreviousBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        Keybind.Binding original = new Keybind.Binding(MouseButton.RIGHT, Modifiers.NONE);
        field.setBinding(original);
        List<Keybind.Binding> changes = new ArrayList<>();
        field.setOnChange(changes::add);
        manager.add(field);

        click(manager, 20, 20);
        assertTrue(field.isCapturing());
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(field.isCapturing());
        assertEquals(original, field.binding());
        assertEquals("Mouse Right", field.text());
        assertTrue(changes.isEmpty());
    }

    @Test
    void focusLossCancelsMouseCaptureWithoutChangingTheBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        Keybind.Binding original = new Keybind.Binding(MouseButton.MIDDLE, Modifiers.NONE);
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
    void clearCrossRemovesAMouseBinding() {
        WidgetManager manager = manager();
        Keybind field = field();
        field.setBinding(new Keybind.Binding(MouseButton.MIDDLE, Modifiers.NONE));
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
    void formatsMouseButtonsWithModifiers() {
        assertEquals("Mouse Left", Keybind.format(MouseButton.LEFT, Modifiers.NONE));
        assertEquals("Mouse Right", new Keybind.Binding(MouseButton.RIGHT,
                Modifiers.NONE).text());
        assertEquals("Ctrl+Mouse Left", new Keybind.Binding(MouseButton.LEFT,
                new Modifiers(false, true, false, false)).text());
        assertEquals("Shift+Alt+Mouse 4", Keybind.format(MouseButton.BUTTON_4,
                new Modifiers(true, false, true, false)));
        assertEquals("Mouse 8", Keybind.mouseButtonName(MouseButton.BUTTON_8));
    }

    @Test
    void startsCaptureInsideAContainerAndUsesControlStyle() {
        WidgetManager manager = manager();
        Keybind field = new Keybind();
        HBox row = new HBox();
        row.setGap(12);
        row.add(field);
        VBox page = new VBox();
        page.add(row);
        ScrollPanel scroll = new ScrollPanel();
        scroll.setBounds(new Rect(0, 0, 400, 300));
        scroll.setContent(page);
        manager.add(scroll);
        Size natural = page.measure(LayoutConstraints.loose(400, Float.MAX_VALUE));
        page.setSize(Math.max(400, natural.width()), natural.height());
        manager.layout();

        WidgetStyle control = manager.context().theme().controlStyle();
        assertEquals(control, field.surfaceStyle());
        assertNotEquals(manager.context().theme().panelStyle(), field.surfaceStyle());

        float x = field.absolutePosition().x() + 10;
        float y = field.absolutePosition().y() + 10;
        click(manager, x, y);

        assertTrue(field.isCapturing());
        assertNull(field.binding());
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

    private static void press(WidgetManager manager, float x, float y, MouseButton button,
                              Modifiers modifiers) {
        manager.handle(new PointerPressed(x, y, button, modifiers));
    }

    private static void release(WidgetManager manager, float x, float y, MouseButton button,
                                Modifiers modifiers) {
        manager.handle(new PointerReleased(x, y, button, modifiers));
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
