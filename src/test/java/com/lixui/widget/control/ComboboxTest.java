package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.Popup;
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

class ComboboxTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void opensWithAFocusedFilterAndSelectsAClickedRow() {
        WidgetManager manager = manager();
        Combobox combobox = combobox();
        List<SelectOption> changes = new ArrayList<>();
        combobox.setOnChange(changes::add);
        manager.add(combobox);
        manager.render();

        click(manager, 60, 20);
        assertTrue(combobox.isPopupOpen());
        manager.render();
        Popup popup = popup(manager);
        assertSame(popup.content().children().get(0), manager.focused());

        Widget list = popup.content().children().get(1);
        clickOption(manager, list, 1);

        assertFalse(combobox.isPopupOpen());
        assertEquals(1, combobox.selectedIndex());
        assertEquals("Beta", combobox.text());
        assertEquals(2, combobox.value());
        assertEquals(List.of("Beta"), changes.stream().map(SelectOption::label).toList());
        assertSame(combobox, manager.focused());
    }

    @Test
    void filtersOptionsAndCommitsTheHighlightedRowFromTheFilter() {
        WidgetManager manager = manager();
        Combobox combobox = combobox();
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "gam");

        assertTrue(combobox.isPopupOpen());
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertFalse(combobox.isPopupOpen());
        assertEquals(2, combobox.selectedIndex());
        assertEquals("Gamma", combobox.text());
        assertEquals(3, combobox.value());
        assertSame(combobox, manager.focused());
    }

    @Test
    void movesTheHighlightFromTheFilterAndSkipsDisabledRows() {
        WidgetManager manager = manager();
        Combobox combobox = combobox();
        combobox.options().get(1).setDisabled(true);
        combobox.setOptions(combobox.options());
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "a");
        manager.handle(new KeyPressed(Key.DOWN, Modifiers.NONE, false));
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals(2, combobox.selectedIndex());
        assertEquals("Gamma", combobox.text());
    }

    @Test
    void escapeDismissesThePopupAndRestoresFocusWithoutChangingTheValue() {
        WidgetManager manager = manager();
        Combobox combobox = combobox();
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "gam");
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(combobox.isPopupOpen());
        assertEquals(-1, combobox.selectedIndex());
        assertEquals("", combobox.text());
        assertSame(combobox, manager.focused());

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        assertTrue(combobox.isPopupOpen());
        manager.handle(new KeyPressed(Key.DOWN, Modifiers.NONE, false));
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        assertEquals("Beta", combobox.text());
    }

    @Test
    void enterWithoutAMatchKeepsThePopupOpenUnlessCustomValuesAreAllowed() {
        WidgetManager manager = manager();
        Combobox combobox = combobox();
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "zzz");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertTrue(combobox.isPopupOpen());
        assertEquals(-1, combobox.selectedIndex());

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
        combobox.setAllowCustom(true);
        open(manager, combobox);
        type(manager, "Dragoon");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertFalse(combobox.isPopupOpen());
        assertEquals(-1, combobox.selectedIndex());
        assertNull(combobox.selectedOption());
        assertEquals("Dragoon", combobox.text());
        assertEquals("Dragoon", combobox.value());
        assertSame(combobox, manager.focused());
    }

    @Test
    void scrollsToTheSelectedRowTheFirstTimeThePopupOpens() {
        WidgetManager manager = manager();
        Combobox combobox = new Combobox();
        combobox.setBounds(new Rect(10, 10, 240, 36));
        combobox.setMinimumSize(240, 36);
        combobox.setMaxPopupHeight(150);
        for (int index = 0; index < 30; index++) {
            combobox.addOption(new SelectOption("Option " + index, index));
        }
        combobox.setSelectedIndex(25);
        manager.add(combobox);
        manager.render();

        open(manager, combobox);

        OptionList list = (OptionList) popup(manager).content().children().get(1);
        assertEquals(25, list.highlighted());
        assertTrue(list.scrollY() > 0);
    }

    @Test
    void keepsTheSelectedOptionWhenEarlierOptionsAreRemovedOrReordered() {
        Combobox combobox = combobox();
        combobox.setSelectedIndex(1);

        SelectOption removed = combobox.options().get(0);
        combobox.removeOption(removed);

        assertEquals(0, combobox.selectedIndex());
        assertEquals("Beta", combobox.text());

        SelectOption gamma = combobox.options().get(1);
        SelectOption beta = combobox.options().get(0);
        combobox.setOptions(List.of(gamma, beta));

        assertEquals(1, combobox.selectedIndex());
        assertEquals("Beta", combobox.text());

        combobox.removeOption(beta);

        assertEquals(-1, combobox.selectedIndex());
        assertNull(combobox.value());
        assertEquals("", combobox.text());
    }

    @Test
    void selectsAndClearsProgrammaticValues() {
        Combobox combobox = combobox();
        List<SelectOption> changes = new ArrayList<>();
        combobox.setOnChange(changes::add);

        combobox.setValue(2);
        assertEquals(1, combobox.selectedIndex());

        combobox.setSelectedIndex(-1);
        assertNull(combobox.value());
        assertEquals("", combobox.text());

        combobox.setAllowCustom(true);
        combobox.setValue("Bard");
        assertEquals(-1, combobox.selectedIndex());
        assertEquals("Bard", combobox.value());

        combobox.setAllowCustom(false);
        assertNull(combobox.value());
        assertEquals(-1, combobox.selectedIndex());
        assertEquals(4, changes.size());

        assertThrows(IllegalArgumentException.class, () -> combobox.setValue("Bard"));
        assertThrows(IllegalArgumentException.class, () -> combobox.setSelectedIndex(3));
        combobox.options().get(0).setDisabled(true);
        combobox.setOptions(combobox.options());
        assertThrows(IllegalArgumentException.class, () -> combobox.setSelectedIndex(0));
    }

    @Test
    void highlightsTheFirstEnabledMatch() {
        WidgetManager manager = manager();
        Combobox combobox = emptyCombobox();
        combobox.addOption("Atlas");
        combobox.options().get(0).setDisabled(true);
        combobox.addOption("Amber");
        combobox.addOption("Beta");
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "a");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals(1, combobox.selectedIndex());
        assertEquals("Amber", combobox.text());
    }

    @Test
    void enterDoesNothingWhenEveryMatchIsDisabled() {
        WidgetManager manager = manager();
        Combobox combobox = emptyCombobox();
        combobox.addOption("Atlas");
        combobox.options().get(0).setDisabled(true);
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "tla");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertTrue(combobox.isPopupOpen());
        assertEquals(-1, combobox.selectedIndex());
        assertEquals("", combobox.text());
    }

    @Test
    void selectionCallbackRunsAfterThePopupClosesAndKeepsItsFocus() {
        WidgetManager manager = manager();
        Combobox combobox = combobox();
        Widget dialog = new Widget() { };
        dialog.setFocusable(true);
        dialog.setBounds(new Rect(0, 0, 10, 10));
        manager.add(dialog).add(combobox);
        manager.render();
        List<Boolean> popupStates = new ArrayList<>();
        combobox.setOnChange(option -> {
            popupStates.add(combobox.isPopupOpen());
            manager.focus(dialog);
        });

        open(manager, combobox);
        type(manager, "gam");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals(List.of(false), popupStates);
        assertSame(dialog, manager.focused());
    }

    @Test
    void customCallbackRunsAfterThePopupClosesAndKeepsItsFocus() {
        WidgetManager manager = manager();
        Combobox combobox = combobox();
        combobox.setAllowCustom(true);
        Widget dialog = new Widget() { };
        dialog.setFocusable(true);
        dialog.setBounds(new Rect(0, 0, 10, 10));
        manager.add(dialog).add(combobox);
        manager.render();
        List<Boolean> popupStates = new ArrayList<>();
        combobox.setOnChange(option -> {
            popupStates.add(combobox.isPopupOpen());
            manager.focus(dialog);
        });

        open(manager, combobox);
        type(manager, "Dragoon");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals(List.of(false), popupStates);
        assertEquals("Dragoon", combobox.value());
        assertSame(dialog, manager.focused());
    }

    private static Combobox combobox() {
        Combobox combobox = emptyCombobox();
        combobox.addOption(new SelectOption("Alpha", 1));
        combobox.addOption(new SelectOption("Beta", 2));
        combobox.addOption(new SelectOption("Gamma", 3));
        return combobox;
    }

    private static Combobox emptyCombobox() {
        Combobox combobox = new Combobox();
        combobox.setBounds(new Rect(10, 10, 240, 36));
        combobox.setMinimumSize(240, 36);
        return combobox;
    }

    private static void open(WidgetManager manager, Combobox combobox) {
        click(manager, 60, 20);
        assertTrue(combobox.isPopupOpen());
        manager.render();
    }

    private static void type(WidgetManager manager, String text) {
        for (int index = 0; index < text.length(); index++) {
            manager.handle(new CharTyped(text.charAt(index)));
        }
    }

    private static void clickOption(WidgetManager manager, Widget list, int index) {
        Point origin = list.absolutePosition();
        click(manager, origin.x() + 20, origin.y() + index * 24 + 12);
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
