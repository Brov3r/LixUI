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

class RangeSliderTest {
    private static final Viewport VIEWPORT = TestViewports.HIDPI;

    @Test
    void clampsValuesAndKeepsThemOrdered() {
        RangeSlider slider = new RangeSlider();
        slider.setRange(0, 100);
        slider.setStep(5);
        slider.setValues(30, 70);

        slider.setLowValue(80);
        assertEquals(70, slider.lowValue(), 0.001f);
        assertEquals(70, slider.highValue(), 0.001f);

        slider.setValues(30, 70);
        slider.setHighValue(10);
        assertEquals(30, slider.lowValue(), 0.001f);
        assertEquals(30, slider.highValue(), 0.001f);

        slider.setValues(-10, 110);
        assertEquals(0, slider.lowValue(), 0.001f);
        assertEquals(100, slider.highValue(), 0.001f);
        assertEquals(new RangeSlider.Range(0, 100), slider.range());
    }

    @Test
    void draggingTheThumbsChangesTheSelectedRange() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        RangeSlider slider = new RangeSlider();
        slider.setRange(0, 100);
        slider.setThumbSize(20);
        slider.setValues(25, 75);
        slider.setBounds(new Rect(0, 0, 200, 20));
        manager.add(slider);
        manager.render();

        manager.handle(new PointerPressed(55, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(65, 10));
        assertEquals(30.5556f, slider.lowValue(), 0.01f);
        assertEquals(75, slider.highValue(), 0.001f);
        manager.handle(new PointerReleased(65, 10, MouseButton.LEFT, Modifiers.NONE));

        manager.handle(new PointerPressed(145, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(185, 10));
        assertEquals(97.2222f, slider.highValue(), 0.01f);
        manager.handle(new PointerReleased(185, 10, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void thumbsCannotCross() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        RangeSlider slider = new RangeSlider();
        slider.setRange(0, 100);
        slider.setThumbSize(20);
        slider.setValues(30, 70);
        slider.setBounds(new Rect(0, 0, 200, 20));
        manager.add(slider);
        manager.render();

        manager.handle(new PointerPressed(64, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(190, 10));

        assertEquals(70, slider.lowValue(), 0.001f);
        assertEquals(70, slider.highValue(), 0.001f);
    }

    @Test
    void mouseWheelMovesTheNearestThumb() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        RangeSlider slider = new RangeSlider();
        slider.setRange(0, 100);
        slider.setStep(5);
        slider.setThumbSize(20);
        slider.setValues(20, 80);
        slider.setBounds(new Rect(0, 0, 200, 20));
        manager.add(slider);
        manager.render();

        manager.handle(new PointerScrolled(46, 10, 0, 1));
        assertEquals(25, slider.lowValue(), 0.001f);
        assertEquals(80, slider.highValue(), 0.001f);

        manager.handle(new PointerScrolled(154, 10, 0, -1));
        assertEquals(25, slider.lowValue(), 0.001f);
        assertEquals(75, slider.highValue(), 0.001f);
    }

    @Test
    void overlappingThumbsCanBeDraggedApart() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        RangeSlider slider = new RangeSlider();
        slider.setRange(0, 100);
        slider.setThumbSize(20);
        slider.setValues(0, 0);
        slider.setBounds(new Rect(0, 0, 200, 20));
        manager.add(slider);
        manager.render();

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(100, 10));
        assertEquals(0, slider.lowValue(), 0.001f);
        assertEquals(50, slider.highValue(), 0.001f);
        manager.handle(new PointerReleased(100, 10, MouseButton.LEFT, Modifiers.NONE));

        slider.setValues(100, 100);
        manager.handle(new PointerPressed(190, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(100, 10));
        assertEquals(50, slider.lowValue(), 0.001f);
        assertEquals(100, slider.highValue(), 0.001f);
        manager.handle(new PointerReleased(100, 10, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void keyboardMovesTheLastUsedThumb() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        RangeSlider slider = new RangeSlider();
        slider.setRange(0, 100);
        slider.setStep(10);
        slider.setThumbSize(20);
        slider.setValues(20, 80);
        slider.setBounds(new Rect(0, 0, 200, 20));
        manager.add(slider);
        manager.render();
        manager.focus(slider);

        manager.handle(new KeyPressed(Key.RIGHT, Modifiers.NONE, false));
        assertEquals(30, slider.lowValue(), 0.001f);
        assertEquals(80, slider.highValue(), 0.001f);

        manager.handle(new PointerPressed(154, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(154, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new KeyPressed(Key.LEFT, Modifiers.NONE, false));
        assertEquals(30, slider.lowValue(), 0.001f);
        assertEquals(70, slider.highValue(), 0.001f);
    }

    @Test
    void rejectsInvalidConfiguration() {
        RangeSlider slider = new RangeSlider();
        slider.setRange(0, 100);
        assertThrows(IllegalArgumentException.class, () -> slider.setRange(0, 0));
        assertThrows(IllegalArgumentException.class, () -> slider.setRange(Float.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> slider.setValues(80, 20));
        assertThrows(IllegalArgumentException.class, () -> slider.setValues(Float.NaN, 20));
        assertThrows(IllegalArgumentException.class, () -> slider.setLowValue(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> slider.setHighValue(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> slider.setStep(-1));
        assertThrows(IllegalArgumentException.class, () -> slider.setThumbSize(0));
        assertThrows(IllegalArgumentException.class, () -> slider.setTrackThickness(0));
        assertThrows(IllegalArgumentException.class, () -> slider.setTickCount(-1));
        assertThrows(NullPointerException.class, () -> slider.setOrientation(null));
        assertThrows(IllegalArgumentException.class, () -> new RangeSlider.Range(2, 1));
        assertThrows(IllegalArgumentException.class, () -> new RangeSlider.Range(0, Float.NaN));
    }
}
