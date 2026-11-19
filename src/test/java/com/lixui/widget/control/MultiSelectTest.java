package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiSelectTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void togglesSeveralOptionsWithoutClosingThePopup() {
        WidgetManager manager = manager();
        MultiSelect multi = multiSelect();
        List<String> changes = new ArrayList<>();
        multi.setOnChange((option, checked) ->
                changes.add(option.label() + ":" + checked));
        manager.add(multi);
        manager.render();

        click(manager, 60, 20);
        manager.render();
        Popup popup = popup(manager);
        Widget list = popup.content();

        clickOption(manager, list, 0);
        assertTrue(multi.isPopupOpen());
        clickOption(manager, list, 2);

        assertTrue(multi.isPopupOpen());
        assertEquals(List.of(0, 2), multi.selectedIndices());
        assertEquals(List.of("Alpha", "Gamma"),
                multi.selectedOptions().stream().map(SelectOption::label).toList());
        assertEquals(2, multi.selectedCount());
        assertEquals(List.of("Alpha:true", "Gamma:true"), changes);

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
        assertFalse(multi.isPopupOpen());
    }

    @Test
    void supportsProgrammaticSelectionAndDisabledOptions() {
        MultiSelect multi = multiSelect();
        multi.setSelected(1, true);
        assertTrue(multi.isSelected(1));

        multi.setAll(true);
        assertEquals(List.of(0, 1, 2), multi.selectedIndices());

        multi.setSelected(1, false);
        assertEquals(List.of(0, 2), multi.selectedIndices());

        multi.clearSelection();
        assertTrue(multi.selectedIndices().isEmpty());

        multi.options().get(0).setDisabled(true);
        multi.setOptions(multi.options());
        assertThrows(IllegalArgumentException.class, () -> multi.setSelected(0, true));
        assertThrows(IllegalArgumentException.class, () -> multi.setSelected(3, true));
    }

    @Test
    void ignoresClicksOnDisabledOptions() {
        WidgetManager manager = manager();
        MultiSelect multi = multiSelect();
        multi.options().get(1).setDisabled(true);
        manager.add(multi);
        manager.render();

        click(manager, 60, 20);
        manager.render();
        Widget list = popup(manager).content();

        clickOption(manager, list, 1);

        assertTrue(multi.selectedIndices().isEmpty());
        assertTrue(multi.isPopupOpen());
    }

    private static MultiSelect multiSelect() {
        MultiSelect multi = new MultiSelect();
        multi.setBounds(new Rect(10, 10, 240, 36));
        multi.setMinimumSize(240, 36);
        multi.addOption(new SelectOption("Alpha", 1));
        multi.addOption(new SelectOption("Beta", 2));
        multi.addOption(new SelectOption("Gamma", 3));
        return multi;
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
