package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;
import com.lixui.widget.overlay.Popup;
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

class DropdownTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void opensAndSelectsAnOption() {
        WidgetManager manager = manager();
        Dropdown dropdown = dropdown();
        List<SelectOption> changes = new ArrayList<>();
        dropdown.setOnChange(changes::add);
        manager.add(dropdown);
        manager.render();

        click(manager, 60, 20);
        manager.render();

        assertTrue(dropdown.isPopupOpen());
        Popup popup = popup(manager);
        Widget list = popup.content();

        clickOption(manager, list, 1);

        assertFalse(dropdown.isPopupOpen());
        assertEquals(1, dropdown.selectedIndex());
        assertEquals("Beta", dropdown.selectedOption().label());
        assertEquals(2, dropdown.selectedValue());
        assertEquals(List.of("Beta"), changes.stream().map(SelectOption::label).toList());
        assertSame(dropdown, manager.focused());
    }

    @Test
    void ignoresDisabledOptionsAndNonLeftButtons() {
        WidgetManager manager = manager();
        Dropdown dropdown = dropdown();
        dropdown.options().get(1).setDisabled(true);
        manager.add(dropdown);
        manager.render();

        manager.handle(new PointerPressed(60, 20, MouseButton.RIGHT, Modifiers.NONE));
        manager.handle(new PointerReleased(60, 20, MouseButton.RIGHT, Modifiers.NONE));
        assertFalse(dropdown.isPopupOpen());

        click(manager, 60, 20);
        manager.render();
        Popup popup = popup(manager);
        Widget list = popup.content();

        clickOption(manager, list, 1);

        assertTrue(dropdown.isPopupOpen());
        assertEquals(-1, dropdown.selectedIndex());

        manager.handle(new PointerPressed(700, 500, MouseButton.LEFT, Modifiers.NONE));
        assertFalse(dropdown.isPopupOpen());
    }

    @Test
    void exposesSelectionApiAndClampsAfterOptionChanges() {
        Dropdown dropdown = dropdown();
        List<SelectOption> changes = new ArrayList<>();
        dropdown.setOnChange(changes::add);

        dropdown.setSelectedIndex(2);
        assertEquals("Gamma", dropdown.selectedOption().label());
        dropdown.setSelectedIndex(2);
        assertEquals(1, changes.size());

        dropdown.options().get(2).setDisabled(true);
        dropdown.setOptions(dropdown.options());
        assertEquals(-1, dropdown.selectedIndex());
        assertNull(dropdown.selectedValue());

        dropdown.clearSelection();
        assertThrows(IllegalArgumentException.class, () -> dropdown.setSelectedIndex(3));
        assertThrows(IllegalArgumentException.class, () -> dropdown.setSelectedIndex(-2));
        assertThrows(IllegalArgumentException.class, () -> dropdown.setSelectedIndex(2));
    }

    @Test
    void keepsControlStyleAndRespectsWidthLimitsInsideAnInheritedStyle() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Theme theme = manager.context().theme();
        Color red = Color.hex("#FF0000");
        WidgetStyle inherited = new WidgetStyle(red, red, red, red,
                theme.text(), theme.textMuted(), theme.border(), theme.focusRing(), 0, 0, 0);
        Panel root = new Panel();
        root.setStyle(inherited);
        root.setBounds(new Rect(0, 0, 500, 200));
        Dropdown dropdown = dropdown();
        dropdown.setBounds(new Rect(20, 20, 200, 36));
        root.add(dropdown);
        manager.add(root);
        manager.render();

        assertEquals(200, dropdown.measure(LayoutConstraints.loose(500, 100)).width(), 0.001f);

        boolean controlSurface = backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.color().equals(
                        theme.controlStyle().background(WidgetState.NORMAL)));
        assertTrue(controlSurface);
    }

    @Test
    void opensWithKeyboardAndClosesOnEscape() {
        WidgetManager manager = manager();
        Dropdown dropdown = dropdown();
        manager.add(dropdown);
        manager.render();
        manager.focus(dropdown);

        manager.handle(new KeyPressed(Key.DOWN, Modifiers.NONE, false));
        manager.render();
        assertTrue(dropdown.isPopupOpen());

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
        assertFalse(dropdown.isPopupOpen());
        assertSame(dropdown, manager.focused());
    }

    @Test
    void keepsTheSelectedOptionWhenAnEarlierOptionIsRemoved() {
        Dropdown dropdown = dropdown();
        dropdown.setSelectedIndex(1);

        dropdown.removeOption(dropdown.options().get(0));

        assertEquals(0, dropdown.selectedIndex());
        assertEquals("Beta", dropdown.selectedOption().label());
        assertEquals(2, dropdown.selectedValue());

        dropdown.removeOption(dropdown.options().get(0));

        assertEquals(-1, dropdown.selectedIndex());
        assertNull(dropdown.selectedValue());
    }

    @Test
    void scrollsToTheSelectedRowOnTheFirstOpen() {
        WidgetManager manager = manager();
        Dropdown dropdown = new Dropdown();
        dropdown.setBounds(new Rect(10, 10, 200, 36));
        dropdown.setMinimumSize(200, 36);
        dropdown.setMaxPopupHeight(150);
        for (int index = 0; index < 30; index++) {
            dropdown.addOption(new SelectOption("Option " + index, index));
        }
        dropdown.setSelectedIndex(25);
        manager.add(dropdown);
        manager.render();

        click(manager, 60, 20);
        manager.render();

        OptionList list = (OptionList) popup(manager).content();
        assertEquals(25, list.highlighted());
        assertTrue(list.scrollY() > 0);
    }

    private static Dropdown dropdown() {
        Dropdown dropdown = new Dropdown();
        dropdown.setBounds(new Rect(10, 10, 200, 36));
        dropdown.setMinimumSize(200, 36);
        dropdown.addOption(new SelectOption("Alpha", 1));
        dropdown.addOption(new SelectOption("Beta", 2));
        dropdown.addOption(new SelectOption("Gamma", 3));
        return dropdown;
    }

    private static void clickOption(WidgetManager manager, Widget list, int index) {
        Point origin = list.absolutePosition();
        float y = origin.y() + index * 24 + 12;
        click(manager, origin.x() + 20, y);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static Popup popup(WidgetManager manager) {
        return manager.widgets().stream()
                .filter(Popup.class::isInstance)
                .map(Popup.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        return manager;
    }
}
