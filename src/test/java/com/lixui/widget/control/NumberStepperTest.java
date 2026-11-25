package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberStepperTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void buttonsAdjustAndClampTheValue() {
        NumberStepper stepper = new NumberStepper();
        stepper.setRange(0, 2);
        stepper.setValue(1);

        increment(stepper).click();
        assertEquals(2, stepper.value(), 0.001);

        increment(stepper).click();
        assertEquals(2, stepper.value(), 0.001);

        decrement(stepper).click();
        decrement(stepper).click();
        decrement(stepper).click();
        assertEquals(0, stepper.value(), 0.001);
    }

    @Test
    void typedValueUpdatesImmediatelyAndEnterNormalizesIt() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            NumberStepper stepper = new NumberStepper();
            stepper.setRange(0, 10);
            stepper.setBounds(new Rect(0, 0, 260, 40));
            manager.add(stepper);
            manager.render();

            stepper.setText("7.6");
            assertEquals(8, stepper.value(), 0.001);

            manager.focus(stepper);
            manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

            assertEquals("8", stepper.text());
            assertEquals(8, stepper.value(), 0.001);
        }
    }

    @Test
    void inputTextIsCenteredAndModifierClicksMultiplyTheStep() {
        NumberStepper stepper = new NumberStepper();
        stepper.setRange(0, 1000);
        stepper.setValue(0);
        assertEquals(HorizontalAlignment.CENTER, stepper.textAlignment());

        increment(stepper).onClick(0, 0, MouseButton.LEFT,
                new Modifiers(true, false, false, false));
        assertEquals(10, stepper.value(), 0.001);

        increment(stepper).onClick(0, 0, MouseButton.LEFT,
                new Modifiers(false, true, false, false));
        assertEquals(110, stepper.value(), 0.001);

        increment(stepper).onClick(0, 0, MouseButton.LEFT,
                new Modifiers(false, false, true, false));
        assertEquals(1000, stepper.value(), 0.001);
    }

    @Test
    void decimalPrecisionRoundsTheValue() {
        NumberStepper stepper = new NumberStepper();
        stepper.setRange(0, 10);
        stepper.setStep(0.25);
        stepper.setDecimals(1);

        stepper.setValue(1.25);

        assertEquals(1.3, stepper.value(), 0.001);
    }

    @Test
    void keepsExternalCallbacksWhileParsingText() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            NumberStepper stepper = new NumberStepper();
            stepper.setRange(0, 10);
            stepper.setBounds(new Rect(0, 0, 260, 40));
            manager.add(stepper);
            manager.render();

            List<String> texts = new ArrayList<>();
            List<String> submissions = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            stepper.setOnChange(texts::add);
            stepper.setOnSubmit(() -> submissions.add("submitted"));
            stepper.setOnValueChange(values::add);

            stepper.setText("7.6");
            assertEquals(8, stepper.value(), 0.001);
            assertEquals(List.of("7.6"), texts);
            assertEquals(List.of(8.0), values);

            manager.focus(stepper);
            manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

            assertEquals(List.of("submitted"), submissions);
            assertEquals("8", stepper.text());
        }
    }

    @Test
    void keepsLargeValuesAtMaximumDecimalPrecision() {
        NumberStepper stepper = new NumberStepper();
        stepper.setRange(0, 1e13);
        stepper.setDecimals(9);
        stepper.setValue(1e12);

        assertEquals(1e12, stepper.value(), 0.0);
        assertEquals("1000000000000.000000000", stepper.text());
    }

    @Test
    void keepsHalfTiesRoundedTowardPositiveInfinity() {
        NumberStepper stepper = new NumberStepper();
        stepper.setRange(-10, 10);

        stepper.setValue(1.5);
        assertEquals(2, stepper.value(), 0.001);

        stepper.setValue(-1.5);
        assertEquals(-1, stepper.value(), 0.001);
    }

    @Test
    void roundsValuesJustBelowHalfDown() {
        NumberStepper stepper = new NumberStepper();
        stepper.setRange(0, 1);
        stepper.setValue(Math.nextDown(0.5));

        assertEquals(0, stepper.value(), 0.0);
    }

    @Test
    void rejectsInvalidConfiguration() {
        NumberStepper stepper = new NumberStepper();
        assertThrows(IllegalArgumentException.class, () -> stepper.setValue(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> stepper.setStep(0));
        assertThrows(IllegalArgumentException.class, () -> stepper.setStep(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> stepper.setRange(5, 1));
        assertThrows(IllegalArgumentException.class, () -> stepper.setDecimals(-1));
        assertThrows(IllegalArgumentException.class, () -> stepper.setDecimals(10));
    }

    private static Button decrement(NumberStepper stepper) {
        return (Button) stepper.children().get(0);
    }

    private static Button increment(NumberStepper stepper) {
        return (Button) stepper.children().get(1);
    }

    private static Font font(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return manager.context().createFont(data.get(), 16);
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
