package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepperTest {
    @Test
    void resolvesEveryStepState() {
        Stepper stepper = stepper(5);
        stepper.setCurrentIndex(2);
        stepper.steps().get(1).setError(true);
        stepper.steps().get(4).setDisabled(true);

        assertEquals(Stepper.StepState.COMPLETED, stepper.state(0));
        assertEquals(Stepper.StepState.ERROR, stepper.state(1));
        assertEquals(Stepper.StepState.CURRENT, stepper.state(2));
        assertEquals(Stepper.StepState.FUTURE, stepper.state(3));
        assertEquals(Stepper.StepState.DISABLED, stepper.state(4));

        stepper.setEnabled(false);
        assertEquals(Stepper.StepState.DISABLED, stepper.state(2));
    }

    @Test
    void animatesConnectorFillBetweenSteps() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Stepper stepper = stepper(4);
            stepper.setAnimationDuration(0.3f);
            stepper.setBounds(new Rect(0, 0, 400, 80));
            manager.add(stepper);
            manager.render();
            assertEquals(0, stepper.displayedPosition(), 0.001f);

            stepper.setCurrentIndex(3);
            assertEquals(3, stepper.currentIndex());
            manager.update(0.05f);
            float partial = stepper.displayedPosition();
            assertTrue(partial > 0 && partial < 3);

            manager.update(1);
            assertEquals(3, stepper.displayedPosition(), 0.001f);
        }
    }

    @Test
    void clickSelectsOnlyEnabledStepsWhenClickable() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            Stepper stepper = stepper(3);
            stepper.setBounds(new Rect(0, 0, 300, 80));
            List<Integer> changes = new ArrayList<>();
            stepper.setOnChange(changes::add);
            manager.add(stepper);
            manager.render();

            click(manager, 250, 40);
            assertEquals(0, stepper.currentIndex());
            assertTrue(changes.isEmpty());

            manager.update(0.5f);
            stepper.setClickable(true);
            click(manager, 250, 40);
            assertEquals(2, stepper.currentIndex());
            assertEquals(List.of(2), changes);

            stepper.steps().get(1).setDisabled(true);
            click(manager, 150, 40);
            assertEquals(2, stepper.currentIndex());
            assertEquals(List.of(2), changes);

            stepper.setEnabled(false);
            click(manager, 50, 40);
            assertEquals(2, stepper.currentIndex());
        }
    }

    @Test
    void measuresTitlesWithFontMetrics() {
        FontData data = fontData();
        Font font = Font.load(data, 16);
        Stepper stepper = new Stepper();
        stepper.addStep("Alpha");
        stepper.addStep("A longer step");
        stepper.setStepSize(32);
        stepper.setConnectorLength(48);
        stepper.setTitleGap(8);
        stepper.setPadding(2);
        stepper.setFont(font);

        float width = Math.max(font.measure("Alpha").width(),
                font.measure("A longer step").width());
        float columnWidth = Math.max(32 + 48, width + 6);
        Size measured = stepper.measure(LayoutConstraints.loose(10000, 10000));
        assertEquals(columnWidth * 2 + 4, measured.width(), 0.01f);
        assertEquals(4 + 32 + 8 + font.lineHeight(), measured.height(), 0.01f);

        stepper.clearFont();
        assertEquals(4 + 32, stepper.measure(LayoutConstraints.loose(10000, 10000)).height(),
                0.01f);
    }

    @Test
    void drawsAllStatesAndConnectors() {
        FontData data = fontData();
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(manager.context().createFont(data, 16));
            Stepper stepper = stepper(5);
            stepper.setBounds(new Rect(0, 0, 500, 90));
            stepper.setCurrentIndex(2);
            stepper.steps().get(1).setError(true);
            stepper.steps().get(4).setDisabled(true);
            manager.add(stepper);
            manager.render();

            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(MeshCommand.class::isInstance));
            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(ImageCommand.class::isInstance));
            assertTrue(backend.lastDrawList().commands().stream()
                    .anyMatch(TextCommand.class::isInstance));
        }
    }

    @Test
    void removesAndClearsStepsSafely() {
        Stepper stepper = stepper(3);
        Stepper.Step last = stepper.steps().getLast();
        stepper.setCurrentIndex(2);
        assertTrue(stepper.removeStep(last));
        assertEquals(1, stepper.currentIndex());
        assertEquals(2, stepper.steps().size());
        assertFalse(stepper.removeStep(last));
        stepper.clearSteps();
        assertTrue(stepper.steps().isEmpty());
        assertEquals(0, stepper.currentIndex());
        assertEquals(0, stepper.displayedPosition(), 0.001f);
    }

    @Test
    void removingAnEarlierStepKeepsTheCurrentStepObject() {
        Stepper stepper = new Stepper();
        Stepper.Step first = stepper.addStep("A");
        Stepper.Step second = stepper.addStep("B");
        Stepper.Step third = stepper.addStep("C");
        stepper.setCurrentIndex(1);
        List<Integer> indexes = new ArrayList<>();
        stepper.setOnChange(indexes::add);

        assertTrue(stepper.removeStep(first));
        assertEquals(0, stepper.currentIndex());
        assertSame(second, stepper.steps().get(stepper.currentIndex()));
        assertEquals(List.of(0), indexes);
        assertEquals(0, stepper.displayedPosition(), 0.001f);

        assertTrue(stepper.removeStep(third));
        assertSame(second, stepper.steps().get(stepper.currentIndex()));
        assertEquals(List.of(0), indexes);
    }

    @Test
    void removingTheCurrentStepSelectsTheNextOrPreviousStep() {
        Stepper stepper = new Stepper();
        Stepper.Step first = stepper.addStep("A");
        Stepper.Step second = stepper.addStep("B");
        Stepper.Step third = stepper.addStep("C");
        Stepper.Step fourth = stepper.addStep("D");
        stepper.setCurrentIndex(1);
        List<Integer> indexes = new ArrayList<>();
        stepper.setOnChange(indexes::add);

        assertTrue(stepper.removeStep(second));
        assertEquals(1, stepper.currentIndex());
        assertSame(third, stepper.steps().get(stepper.currentIndex()));
        assertEquals(List.of(1), indexes);

        assertTrue(stepper.removeStep(third));
        assertEquals(1, stepper.currentIndex());
        assertSame(fourth, stepper.steps().get(stepper.currentIndex()));
        assertEquals(List.of(1, 1), indexes);

        assertTrue(stepper.removeStep(fourth));
        assertEquals(0, stepper.currentIndex());
        assertSame(first, stepper.steps().get(stepper.currentIndex()));
        assertEquals(List.of(1, 1, 0), indexes);

        assertTrue(stepper.removeStep(first));
        assertTrue(stepper.steps().isEmpty());
        assertEquals(0, stepper.currentIndex());
        assertEquals(List.of(1, 1, 0), indexes);
    }

    @Test
    void rejectsInvalidConfiguration() {
        Stepper stepper = stepper(1);
        assertThrows(IndexOutOfBoundsException.class, () -> stepper.setCurrentIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> stepper.setCurrentIndex(1));
        assertThrows(IndexOutOfBoundsException.class, () -> stepper.state(1));
        assertThrows(NullPointerException.class, () -> stepper.addStep(null));
        assertThrows(NullPointerException.class, () -> stepper.removeStep(null));
        assertThrows(IllegalArgumentException.class, () -> stepper.setStepSize(0));
        assertThrows(IllegalArgumentException.class, () -> stepper.setConnectorLength(-1));
        assertThrows(IllegalArgumentException.class, () -> stepper.setConnectorThickness(0));
        assertThrows(IllegalArgumentException.class, () -> stepper.setTitleGap(-1));
        assertThrows(IllegalArgumentException.class, () -> stepper.setPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> stepper.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> stepper.setAnimationDuration(-1));
        assertThrows(NullPointerException.class, () -> stepper.setAnimationEasing(null));
        assertThrows(NullPointerException.class, () -> stepper.setErrorColor(null));

        Stepper empty = new Stepper();
        assertThrows(IndexOutOfBoundsException.class, () -> empty.setCurrentIndex(0));
    }

    private static Stepper stepper(int count) {
        Stepper stepper = new Stepper();
        for (int index = 1; index <= count; index++) {
            stepper.addStep("Step " + index);
        }
        return stepper;
    }

    private static FontData fontData() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return data.get();
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.LOGICAL);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }
}
