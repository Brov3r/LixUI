package com.lixui.widget.control;

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

class RadioGroupTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void clickingSelectsOneOptionAndIgnoresDisabledRows() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadioGroup group = new RadioGroup();
            RadioBox first = group.addOption("First");
            RadioBox second = group.addOption("Second");
            RadioBox third = group.addOption("Third");
            RadioBox disabled = group.addOption("Disabled");
            disabled.setEnabled(false);
            group.setBounds(new Rect(0, 0, 220, 140));
            List<Integer> changes = new ArrayList<>();
            group.setOnChange(changes::add);
            manager.add(group);
            manager.render();

            assertEquals(first.bounds().width(), second.bounds().width(), 0.001f);
            assertEquals(first.bounds().y() + first.bounds().height() + group.gap(),
                    second.bounds().y(), 0.001f);

            click(manager, third);
            assertEquals(2, group.selectedIndex());
            assertEquals(List.of(2), changes);
            assertTrue(third.isChecked());
            assertTrue(third.isFocused());
            assertFalse(first.isChecked());
            assertFalse(second.isChecked());

            click(manager, third);
            assertEquals(2, group.selectedIndex());
            assertEquals(List.of(2), changes);

            click(manager, disabled);
            assertEquals(2, group.selectedIndex());
            assertEquals(List.of(2), changes);

            click(manager, second);
            assertEquals(1, group.selectedIndex());
            assertEquals(List.of(2, 1), changes);
            assertTrue(second.isChecked());
            assertFalse(third.isChecked());
            assertTrue(second.isFocused());
        }
    }

    @Test
    void arrowKeysMoveSelectionAndFocusWhileSkippingHiddenAndDisabledOptions() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadioGroup group = new RadioGroup();
            RadioBox first = group.addOption("First");
            RadioBox hidden = group.addOption("Hidden");
            hidden.setVisible(false);
            RadioBox disabled = group.addOption("Disabled");
            disabled.setEnabled(false);
            RadioBox third = group.addOption("Third");
            group.setBounds(new Rect(0, 0, 220, 120));
            List<Integer> changes = new ArrayList<>();
            group.setOnChange(changes::add);
            manager.add(group);
            manager.render();

            click(manager, first);
            assertEquals(List.of(0), changes);

            manager.handle(new KeyPressed(Key.DOWN, Modifiers.NONE, false));
            assertEquals(3, group.selectedIndex());
            assertEquals(List.of(0, 3), changes);
            assertTrue(third.isChecked());
            assertTrue(third.isFocused());
            assertFalse(hidden.isChecked());
            assertFalse(disabled.isChecked());

            manager.handle(new KeyPressed(Key.DOWN, Modifiers.NONE, false));
            assertEquals(0, group.selectedIndex());
            assertEquals(List.of(0, 3, 0), changes);
            assertTrue(first.isFocused());

            manager.handle(new KeyPressed(Key.UP, Modifiers.NONE, false));
            assertEquals(3, group.selectedIndex());
            assertEquals(List.of(0, 3, 0, 3), changes);
            assertTrue(third.isFocused());

            manager.handle(new KeyPressed(Key.LEFT, Modifiers.NONE, false));
            assertEquals(0, group.selectedIndex());
            assertTrue(first.isFocused());

            manager.handle(new KeyPressed(Key.RIGHT, Modifiers.NONE, false));
            assertEquals(3, group.selectedIndex());
            assertTrue(third.isFocused());
        }
    }

    @Test
    void perOptionCallbackDoesNotReplaceTheGroupSelectionCallback() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadioGroup group = new RadioGroup();
            RadioBox first = group.addOption("First");
            RadioBox second = group.addOption("Second");
            group.setBounds(new Rect(0, 0, 220, 100));
            List<Integer> groupChanges = new ArrayList<>();
            List<Boolean> optionChanges = new ArrayList<>();
            group.setOnChange(groupChanges::add);
            second.setOnChange(optionChanges::add);
            manager.add(group);
            manager.render();

            click(manager, second);
            assertEquals(1, group.selectedIndex());
            assertEquals(List.of(1), groupChanges);
            assertEquals(List.of(true), optionChanges);

            click(manager, first);
            assertEquals(0, group.selectedIndex());
            assertEquals(List.of(1, 0), groupChanges);
            assertEquals(List.of(true, false), optionChanges);
        }
    }

    @Test
    void programmaticSelectionFiresOnceAndRejectsInvalidTargets() {
        RadioGroup group = new RadioGroup();
        group.addOption("First");
        group.addOption("Second");
        RadioBox disabled = group.addOption("Disabled");
        disabled.setEnabled(false);
        List<Integer> changes = new ArrayList<>();
        group.setOnChange(changes::add);

        assertEquals(-1, group.selectedIndex());
        group.setSelectedIndex(1);
        assertEquals(1, group.selectedIndex());
        assertEquals(List.of(1), changes);

        group.setSelectedIndex(1);
        assertEquals(List.of(1), changes);

        group.setSelectedIndex(0);
        assertEquals(0, group.selectedIndex());
        assertEquals(List.of(1, 0), changes);

        assertThrows(IllegalArgumentException.class, () -> group.setSelectedIndex(2));
        assertThrows(IllegalArgumentException.class, () -> group.setSelectedIndex(-1));
        assertThrows(IllegalArgumentException.class, () -> group.setSelectedIndex(3));
        assertThrows(NullPointerException.class, () -> group.addOption(null));

        group.setGap(7);
        assertEquals(7, group.gap(), 0.001f);
        assertThrows(IllegalArgumentException.class, () -> group.setGap(-1));
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }

    private static void click(WidgetManager manager, Widget widget) {
        float x = widget.absolutePosition().x() + widget.bounds().width() / 2;
        float y = widget.absolutePosition().y() + widget.bounds().height() / 2;
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
