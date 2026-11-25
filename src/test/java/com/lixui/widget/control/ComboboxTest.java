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
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.Popup;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    @Test
    void searchClearRestoresRowsAndKeepsSelectionAndFocus() {
        WidgetManager manager = manager();
        useFont(manager);
        Combobox combobox = combobox();
        combobox.setSelectedIndex(0);
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        SearchInput search = search(manager);
        assertSame(search, manager.focused());

        type(manager, "gam");
        manager.render();
        assertEquals(1, visibleRowCount(manager));
        assertTrue(combobox.isPopupOpen());

        clickClear(manager, search);
        manager.render();

        assertEquals(3, visibleRowCount(manager));
        assertTrue(combobox.isPopupOpen());
        assertSame(search, manager.focused());
        assertEquals(0, combobox.selectedIndex());
        assertEquals("Alpha", combobox.text());

        type(manager, "zzz");
        manager.render();
        assertEquals(0, visibleRowCount(manager));

        clickClear(manager, search);
        manager.render();

        assertEquals(3, visibleRowCount(manager));
        assertTrue(combobox.isPopupOpen());
        assertSame(search, manager.focused());
        assertEquals(0, combobox.selectedIndex());
    }

    @Test
    void repeatedLayoutKeepsSearchTextFocusAndPopup() {
        WidgetManager manager = manager();
        useFont(manager);
        Combobox combobox = combobox();
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        SearchInput search = search(manager);
        type(manager, "gam");
        manager.render();
        for (int frame = 0; frame < 3; frame++) {
            manager.render();
        }

        assertEquals("gam", search.text());
        assertSame(search, manager.focused());
        assertTrue(combobox.isPopupOpen());
        assertEquals(1, visibleRowCount(manager));

        clickClear(manager, search);
        for (int frame = 0; frame < 3; frame++) {
            manager.render();
        }

        assertEquals("", search.text());
        assertSame(search, manager.focused());
        assertTrue(combobox.isPopupOpen());
        assertEquals(3, visibleRowCount(manager));
    }

    @Test
    void popupHeightFollowsFilteredRowsAndAvailableSpace() {
        WidgetManager manager = manager();
        useFont(manager);
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
        Popup popup = popup(manager);
        float longHeight = popup.popupBounds().height();
        assertTrue(longHeight <= 150);
        assertEquals(30, visibleRowCount(manager));

        type(manager, "Option 25");
        manager.render();
        float oneRowHeight = popup.popupBounds().height();
        assertEquals(1, visibleRowCount(manager));
        assertTrue(oneRowHeight < longHeight);

        type(manager, "x");
        manager.render();
        assertEquals(0, visibleRowCount(manager));
        assertTrue(popup.popupBounds().height() < oneRowHeight);

        clickClear(manager, search(manager));
        manager.render();
        assertEquals(30, visibleRowCount(manager));
        assertEquals(longHeight, popup.popupBounds().height(), 0.01f);
        assertTrue(combobox.isPopupOpen());
        assertSame(search(manager), manager.focused());
        assertEquals(25, combobox.selectedIndex());
    }

    @Test
    void popupResizesWhenOptionsChangeAndWhenTheViewportShrinks() {
        WidgetManager manager = manager();
        Combobox combobox = emptyCombobox();
        for (int index = 0; index < 6; index++) {
            combobox.addOption("Option " + index);
        }
        manager.add(combobox);
        manager.render();
        open(manager, combobox);
        float initial = popup(manager).popupBounds().height();

        combobox.addOption("Extra");
        manager.render();
        assertTrue(popup(manager).popupBounds().height() > initial);

        combobox.removeOption(combobox.options().get(6));
        manager.render();
        assertEquals(initial, popup(manager).popupBounds().height(), 0.01f);

        Viewport compact = new Viewport(300, 220, 300, 220);
        manager.resize(compact);
        manager.render();

        Popup popup = popup(manager);
        assertTrue(combobox.isPopupOpen());
        assertTrue(popup.popupBounds().width() <= compact.logicalWidth());
        assertTrue(popup.popupBounds().height() <= compact.logicalHeight());
        assertTrue(popup.popupBounds().height() < initial);
        assertSame(search(manager), manager.focused());

        manager.resize(TestViewports.HIDPI);
        manager.render();

        Popup scaled = popup(manager);
        assertTrue(combobox.isPopupOpen());
        assertEquals(6, visibleRowCount(manager));
        assertTrue(scaled.popupBounds().width() <= TestViewports.HIDPI.logicalWidth());
        assertTrue(scaled.popupBounds().height() <= TestViewports.HIDPI.logicalHeight());
        assertSame(search(manager), manager.focused());
    }

    @Test
    void popupFlipsAboveAndFitsTheSpaceWhenOpenedNearTheBottom() {
        WidgetManager manager = manager();
        Combobox combobox = new Combobox();
        combobox.setBounds(new Rect(10, 520, 240, 36));
        combobox.setMinimumSize(240, 36);
        combobox.setMaxPopupHeight(220);
        for (int index = 0; index < 20; index++) {
            combobox.addOption("Option " + index);
        }
        manager.add(combobox);
        manager.render();

        click(manager, 60, 538);
        assertTrue(combobox.isPopupOpen());
        manager.render();
        Rect bounds = popup(manager).popupBounds();
        assertTrue(bounds.bottom() <= 520 - 2);
        assertTrue(bounds.y() >= 4);
        assertTrue(bounds.height() <= 220);
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

    private static SearchInput search(WidgetManager manager) {
        return (SearchInput) popup(manager).content().children().get(0);
    }

    private static int visibleRowCount(WidgetManager manager) {
        OptionList list = (OptionList) popup(manager).content().children().get(1);
        return Math.round(list.preferredHeight() / list.itemHeight());
    }

    private static void clickClear(WidgetManager manager, SearchInput search) {
        manager.update(0.5f);
        Point origin = search.absolutePosition();
        Rect bounds = search.bounds();
        click(manager, origin.x() + bounds.width() - 16, origin.y() + bounds.height() / 2);
    }

    private static void useFont(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        manager.setDefaultFont(manager.context().createFont(data.get(), 16));
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
