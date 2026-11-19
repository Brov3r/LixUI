package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.KeyReleased;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.Viewport;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.tooltip.TextTooltip;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToggleControlsTest {
    private static final Viewport VIEWPORT = new Viewport(800, 600, 800, 600);

    @Test
    void togglesWithPointerKeyboardAndProgrammatically() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            CheckBox box = new CheckBox("Sound");
            box.setBounds(new Rect(0, 0, 140, 24));
            List<Boolean> changes = new ArrayList<>();
            box.setOnChange(changes::add);
            manager.add(box);
            manager.render();

            click(manager, 10, 12);
            assertTrue(box.isChecked());
            assertEquals(List.of(true), changes);
            assertTrue(box.isFocused());

            manager.handle(new KeyPressed(Key.SPACE, Modifiers.NONE, false));
            assertTrue(box.isChecked());
            manager.handle(new KeyReleased(Key.SPACE, Modifiers.NONE));
            assertFalse(box.isChecked());
            assertEquals(List.of(true, false), changes);

            box.setChecked(true);
            assertEquals(List.of(true, false, true), changes);
            box.setChecked(true);
            assertEquals(3, changes.size());

            click(manager, 10, 12, MouseButton.RIGHT);
            assertTrue(box.isChecked());
        }
    }

    @Test
    void groupKeepsASingleSelectionAndHonorsTheMinimum() {
        CheckBox first = new CheckBox("First");
        CheckBox second = new CheckBox("Second");
        ToggleGroup group = new ToggleGroup();
        group.setExclusive(true);
        group.add(first);
        group.add(second);

        first.setChecked(true);
        assertTrue(first.isChecked());
        second.setChecked(true);
        assertFalse(first.isChecked());
        assertSame(second, group.selected().getFirst());

        group.setMinimumSelected(1);
        second.setChecked(false);
        assertTrue(second.isChecked());
        assertEquals(1, group.selected().size());

        group.setMinimumSelected(0);
        second.setChecked(false);
        assertTrue(group.selected().isEmpty());
    }

    @Test
    void groupClearsTheOldestSelectionAboveTheMaximum() {
        CheckBox first = new CheckBox("First");
        CheckBox second = new CheckBox("Second");
        CheckBox third = new CheckBox("Third");
        ToggleGroup group = new ToggleGroup();
        group.add(first);
        group.add(second);
        group.add(third);
        group.setMaximumSelected(2);

        first.setChecked(true);
        second.setChecked(true);
        third.setChecked(true);

        assertFalse(first.isChecked());
        assertTrue(second.isChecked());
        assertTrue(third.isChecked());
        assertEquals(List.of(second, third), group.selected());
    }

    @Test
    void radioBoxStaysCheckedAndAnExclusiveGroupSwitchesTheSelection() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            RadioBox first = new RadioBox("First");
            first.setBounds(new Rect(0, 0, 80, 20));
            RadioBox second = new RadioBox("Second");
            second.setBounds(new Rect(0, 30, 80, 20));
            ToggleGroup group = new ToggleGroup();
            group.setExclusive(true);
            group.add(first);
            group.add(second);
            manager.add(first);
            manager.add(second);
            manager.render();

            click(manager, 10, 10);
            assertTrue(first.isChecked());
            click(manager, 10, 10);
            assertTrue(first.isChecked());

            click(manager, 10, 40);
            assertTrue(second.isChecked());
            assertFalse(first.isChecked());
            assertSame(second, group.selected().getFirst());
        }
    }

    @Test
    void drawsCheckMarkSwitchTrackAndRadioDot() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            CheckBox box = new CheckBox();
            box.setBounds(new Rect(0, 0, 20, 20));
            box.setChecked(true);
            manager.add(box);
            manager.update(0.5f);
            manager.render();
            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(MeshCommand.class::isInstance));

            CheckBox switchBox = new CheckBox();
            switchBox.setIndicator(CheckBox.Indicator.SWITCH);
            switchBox.setBounds(new Rect(40, 0, 40, 20));
            switchBox.setChecked(true);
            manager.add(switchBox);
            manager.update(0.5f);
            manager.render();
            assertTrue(hasRoundedWithRadius(backend, 9));
            assertTrue(hasRoundedWithRadius(backend, 7));

            RadioBox radio = new RadioBox();
            radio.setBounds(new Rect(90, 0, 20, 20));
            radio.setChecked(true);
            manager.add(radio);
            manager.update(0.5f);
            manager.render();
            assertTrue(hasRoundedWithRadius(backend, 8));
            assertTrue(hasRoundedWithRadius(backend, 3.6f));
        }
    }

    @Test
    void playsSoundOnlyForUserInitiatedToggles() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            CheckBox box = new CheckBox("Sound");
            box.setBounds(new Rect(0, 0, 120, 24));
            int[] sounds = {0};
            box.setSoundPlayer(() -> sounds[0]++);
            manager.add(box);
            manager.render();

            click(manager, 10, 12);
            assertEquals(1, sounds[0]);

            box.setChecked(false);
            assertEquals(1, sounds[0]);

            RadioBox radio = new RadioBox("Radio");
            radio.setBounds(new Rect(0, 30, 120, 24));
            radio.setSoundPlayer(() -> sounds[0]++);
            manager.add(radio);
            click(manager, 10, 42);
            click(manager, 10, 42);
            assertEquals(2, sounds[0]);
        }
    }

    @Test
    void animatesSelectionProgressAndCanBeInstant() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            CheckBox box = new CheckBox("Animated");
            box.setBounds(new Rect(0, 0, 120, 24));
            manager.add(box);
            box.setChecked(true);
            manager.update(0.02f);
            assertTrue(box.checkedAmount() > 0 && box.checkedAmount() < 1);

            manager.update(1f);
            assertEquals(1f, box.checkedAmount(), 0.001f);

            box.setAnimationRate(0);
            box.setChecked(false);
            assertEquals(0f, box.checkedAmount(), 0.001f);
            assertThrows(IllegalArgumentException.class, () -> box.setAnimationRate(-1));
        }
    }

    @Test
    void exposesFineTuningAndRejectsInvalidValues() {
        CheckBox box = new CheckBox("Option");
        box.setBoxSize(18);
        box.setGap(10);
        box.setBoxRadius(4);
        box.setCheckThickness(2.5f);
        box.setTrackWidth(40);
        box.setTrackHeight(22);
        box.setThumbInset(3);
        assertEquals(18f, box.boxSize(), 0.001f);
        assertEquals(10f, box.gap(), 0.001f);
        assertEquals(4f, box.boxRadius(), 0.001f);
        assertEquals(2.5f, box.checkThickness(), 0.001f);
        assertEquals(40f, box.trackWidth(), 0.001f);
        assertEquals(22f, box.trackHeight(), 0.001f);
        assertEquals(3f, box.thumbInset(), 0.001f);

        assertThrows(IllegalArgumentException.class, () -> box.setBoxSize(0));
        assertThrows(IllegalArgumentException.class, () -> box.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> box.setBoxRadius(-1));
        assertThrows(IllegalArgumentException.class, () -> box.setCheckThickness(0));
        assertThrows(IllegalArgumentException.class, () -> box.setTrackWidth(0));
        assertThrows(IllegalArgumentException.class, () -> box.setTrackHeight(0));
        assertThrows(IllegalArgumentException.class, () -> box.setThumbInset(-1));
        assertThrows(IllegalArgumentException.class, () -> box.setThumbInset(11));
        assertThrows(NullPointerException.class, () -> box.setIndicator(null));
        assertThrows(NullPointerException.class, () -> box.setText(null));

        RadioBox radio = new RadioBox("Radio");
        radio.setRadioSize(20);
        assertEquals(20f, radio.radioSize(), 0.001f);
        assertThrows(IllegalArgumentException.class, () -> radio.setRadioSize(0));

        ToggleGroup group = new ToggleGroup();
        assertThrows(IllegalArgumentException.class, () -> group.setMaximumSelected(0));
        group.setMaximumSelected(2);
        assertThrows(IllegalArgumentException.class, () -> group.setMinimumSelected(3));
    }

    @Test
    void tooltipTextAssignsAndClearsAReadyMadeTooltip() {
        CheckBox box = new CheckBox("Option");
        box.setTooltipText("Enables the option");
        assertTrue(box.tooltip() instanceof TextTooltip);
        box.setTooltipText("");
        assertNull(box.tooltip());
    }

    private static boolean hasRoundedWithRadius(TestBackend backend, float radius) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> Math.abs(command.radius() - radius) < 0.01f);
    }

    private static void clickOn(CheckBox box) {
        // Pointer events need an attached manager, so this helper is only used with standalone boxes.
        box.setChecked(!box.isChecked());
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        click(manager, x, y, MouseButton.LEFT);
    }

    private static void click(WidgetManager manager, float x, float y, MouseButton button) {
        manager.handle(new PointerPressed(x, y, button, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, button, Modifiers.NONE));
    }
}
