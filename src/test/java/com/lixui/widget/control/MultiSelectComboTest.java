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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiSelectComboTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void togglesSeveralRowsWithoutClosingThePopup() {
        WidgetManager manager = manager();
        MultiSelectCombo combobox = combobox();
        List<String> changes = new ArrayList<>();
        combobox.setOnChange((option, checked) ->
                changes.add(option.label() + ":" + checked));
        manager.add(combobox);
        manager.render();

        click(manager, 60, 20);
        manager.render();
        Popup popup = popup(manager);
        assertSame(popup.content().children().get(0), manager.focused());
        Widget list = popup.content().children().get(1);

        clickOption(manager, list, 1);
        assertTrue(combobox.isPopupOpen());
        clickOption(manager, list, 2);

        assertTrue(combobox.isPopupOpen());
        assertEquals(List.of(1, 2), combobox.selectedIndices());
        assertEquals(2, combobox.selectedCount());
        assertEquals("Beta +1", combobox.text());
        assertEquals(List.of("Beta:true", "Gamma:true"), changes);

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
        assertFalse(combobox.isPopupOpen());
        assertSame(combobox, manager.focused());
    }

    @Test
    void filtersAndTogglesTheHighlightedRowWithEnter() {
        WidgetManager manager = manager();
        MultiSelectCombo combobox = combobox();
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "gam");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertTrue(combobox.isPopupOpen());
        assertEquals(List.of(2), combobox.selectedIndices());
        assertEquals("Gamma", combobox.text());

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertTrue(combobox.selectedIndices().isEmpty());
    }

    @Test
    void highlightsTheFirstEnabledMatchAndSkipsDisabledRows() {
        WidgetManager manager = manager();
        MultiSelectCombo combobox = emptyCombobox();
        combobox.addOption("Atlas");
        combobox.options().get(0).setDisabled(true);
        combobox.addOption("Amber");
        combobox.addOption("Beta");
        manager.add(combobox);
        manager.render();

        open(manager, combobox);
        type(manager, "a");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals(List.of(1), combobox.selectedIndices());
        assertEquals("Amber", combobox.text());
    }

    @Test
    void showsASummaryAndKeepsSelectionWhenAnEarlierOptionIsRemoved() {
        MultiSelectCombo combobox = combobox();

        combobox.setAll(true);
        assertEquals("Alpha +2", combobox.text());
        assertEquals(List.of(0, 1, 2), combobox.selectedIndices());

        combobox.removeOption(combobox.options().get(0));
        assertEquals(List.of(0, 1), combobox.selectedIndices());
        assertEquals("Beta +1", combobox.text());

        combobox.clearSelection();
        assertEquals("None selected", combobox.text());

        assertThrows(IllegalArgumentException.class, () -> combobox.setSelected(2, true));
    }

    @Test
    void scrollsToTheSelectedRowOnTheFirstOpen() {
        WidgetManager manager = manager();
        MultiSelectCombo combobox = new MultiSelectCombo();
        combobox.setBounds(new Rect(10, 10, 240, 36));
        combobox.setMinimumSize(240, 36);
        combobox.setMaxPopupHeight(150);
        for (int index = 0; index < 30; index++) {
            combobox.addOption(new SelectOption("Option " + index, index));
        }
        combobox.setSelected(25, true);
        manager.add(combobox);
        manager.render();

        open(manager, combobox);

        OptionList list = (OptionList) popup(manager).content().children().get(1);
        assertEquals(25, list.highlighted());
        assertTrue(list.scrollY() > 0);
    }

    private static MultiSelectCombo combobox() {
        MultiSelectCombo combobox = emptyCombobox();
        combobox.addOption(new SelectOption("Alpha", 1));
        combobox.addOption(new SelectOption("Beta", 2));
        combobox.addOption(new SelectOption("Gamma", 3));
        return combobox;
    }

    private static MultiSelectCombo emptyCombobox() {
        MultiSelectCombo combobox = new MultiSelectCombo();
        combobox.setBounds(new Rect(10, 10, 240, 36));
        combobox.setMinimumSize(240, 36);
        return combobox;
    }

    private static void open(WidgetManager manager, MultiSelectCombo combobox) {
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
