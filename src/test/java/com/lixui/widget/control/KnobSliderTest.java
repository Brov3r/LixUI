package com.lixui.widget.control;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.WidgetManager;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.input.event.PointerScrolled;
import com.lixui.render.Viewport;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnobSliderTest {
    private static final Viewport VIEWPORT = TestViewports.HIDPI;

    @Test
    void clampsAndSnapsTheValue() {
        KnobSlider knob = new KnobSlider();
        knob.setRange(0, 100);
        knob.setStep(10);
        knob.setValue(-5);
        assertEquals(0, knob.value(), 0.001f);
        knob.setValue(23);
        assertEquals(20, knob.value(), 0.001f);
        knob.setValue(27);
        assertEquals(30, knob.value(), 0.001f);
        knob.setValue(120);
        assertEquals(100, knob.value(), 0.001f);
    }

    @Test
    void verticalDraggingChangesTheValue() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        KnobSlider knob = new KnobSlider();
        knob.setRange(0, 100);
        knob.setKnobSize(100);
        knob.setValue(50);
        knob.setBounds(new Rect(0, 0, 100, 100));
        manager.add(knob);
        manager.render();

        manager.handle(new PointerPressed(50, 50, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(50, 0));
        assertEquals(75, knob.value(), 0.001f);
        manager.handle(new PointerReleased(50, 0, MouseButton.LEFT, Modifiers.NONE));

        manager.handle(new PointerPressed(50, 50, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(50, 100));
        assertEquals(50, knob.value(), 0.001f);
        manager.handle(new PointerReleased(50, 100, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void mouseWheelChangesTheValue() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        KnobSlider knob = new KnobSlider();
        knob.setRange(0, 100);
        knob.setStep(5);
        knob.setBounds(new Rect(0, 0, 100, 100));
        manager.add(knob);
        manager.render();

        manager.handle(new PointerScrolled(50, 50, 0, 1));
        assertEquals(5, knob.value(), 0.001f);

        manager.handle(new PointerScrolled(50, 50, 0, -1));
        assertEquals(0, knob.value(), 0.001f);
    }

    @Test
    void keyboardAdjustsTheFocusedValue() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        KnobSlider knob = new KnobSlider();
        knob.setRange(0, 100);
        knob.setStep(5);
        manager.add(knob);
        manager.focus(knob);

        manager.handle(new KeyPressed(Key.RIGHT, Modifiers.NONE, false));
        assertEquals(5, knob.value(), 0.001f);
        manager.handle(new KeyPressed(Key.END, Modifiers.NONE, false));
        assertEquals(100, knob.value(), 0.001f);
        manager.handle(new KeyPressed(Key.HOME, Modifiers.NONE, false));
        assertEquals(0, knob.value(), 0.001f);
        manager.handle(new KeyPressed(Key.PAGE_UP, Modifiers.NONE, false));
        assertEquals(10, knob.value(), 0.001f);
    }

    @Test
    void rejectsInvalidConfiguration() {
        KnobSlider knob = new KnobSlider();
        assertThrows(IllegalArgumentException.class, () -> knob.setRange(2, 2));
        assertThrows(IllegalArgumentException.class, () -> knob.setValue(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> knob.setStep(-1));
        assertThrows(IllegalArgumentException.class, () -> knob.setStep(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> knob.setKnobSize(0));
        assertThrows(IllegalArgumentException.class, () -> knob.setTrackThickness(0));
    }
}
