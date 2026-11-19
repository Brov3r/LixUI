package com.lixui.widget.control;

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
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.Viewport;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SliderTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);

    @Test
    void clampsAndSnapsTheValue() {
        Slider slider = new Slider();
        slider.setRange(0, 100);
        slider.setStep(10);

        slider.setValue(-5);
        assertEquals(0, slider.value(), 0.001f);
        slider.setValue(104);
        assertEquals(100, slider.value(), 0.001f);
        slider.setValue(23);
        assertEquals(20, slider.value(), 0.001f);
        slider.setValue(27);
        assertEquals(30, slider.value(), 0.001f);

        slider.setRange(0, 25);
        assertEquals(25, slider.value(), 0.001f);
    }

    @Test
    void draggingTheTrackChangesTheValue() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        Slider slider = new Slider();
        slider.setRange(0, 100);
        slider.setBounds(new Rect(0, 0, 200, 20));
        manager.add(slider);
        manager.render();

        manager.handle(new PointerPressed(100, 10, MouseButton.LEFT, Modifiers.NONE));
        assertEquals(50, slider.value(), 0.001f);

        manager.handle(new PointerMoved(192, 10));
        assertEquals(100, slider.value(), 0.001f);

        manager.handle(new PointerMoved(8, 10));
        assertEquals(0, slider.value(), 0.001f);

        manager.handle(new PointerReleased(8, 10, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void verticalSliderMapsTheBottomToTheMinimum() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        Slider slider = new Slider(Slider.Orientation.VERTICAL);
        slider.setRange(0, 100);
        slider.setBounds(new Rect(0, 0, 48, 200));
        manager.add(slider);
        manager.render();

        manager.handle(new PointerPressed(24, 192, MouseButton.LEFT, Modifiers.NONE));
        assertEquals(0, slider.value(), 0.001f);

        manager.handle(new PointerMoved(24, 8));
        assertEquals(100, slider.value(), 0.001f);

        manager.handle(new PointerReleased(24, 8, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void mouseWheelChangesTheValue() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        Slider slider = new Slider();
        slider.setRange(0, 100);
        slider.setStep(5);
        slider.setBounds(new Rect(0, 0, 200, 24));
        manager.add(slider);
        manager.render();

        manager.handle(new PointerScrolled(100, 12, 0, 1));
        assertEquals(5, slider.value(), 0.001f);

        manager.handle(new PointerScrolled(100, 12, 0, -1));
        assertEquals(0, slider.value(), 0.001f);
    }

    @Test
    void formatsCompactAndFullValues() {
        assertEquals("1 000", SliderSupport.formatFull(1_000));
        assertEquals("10 000", SliderSupport.formatFull(10_000));
        assertEquals("1 234.5", SliderSupport.formatFull(1_234.5f));
        assertEquals("1 000", SliderSupport.formatCompact(1_000));
        assertEquals("10k", SliderSupport.formatCompact(10_000));
        assertEquals("1.5m", SliderSupport.formatCompact(1_500_000));
        assertEquals("1b", SliderSupport.formatCompact(1_000_000_000));
    }

    @Test
    void keyboardAdjustsTheFocusedValue() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        Slider slider = new Slider();
        slider.setRange(0, 100);
        slider.setStep(5);
        manager.add(slider);
        manager.focus(slider);

        manager.handle(new KeyPressed(Key.RIGHT, Modifiers.NONE, false));
        assertEquals(5, slider.value(), 0.001f);
        manager.handle(new KeyPressed(Key.END, Modifiers.NONE, false));
        assertEquals(100, slider.value(), 0.001f);
        manager.handle(new KeyPressed(Key.HOME, Modifiers.NONE, false));
        assertEquals(0, slider.value(), 0.001f);
        manager.handle(new KeyPressed(Key.PAGE_UP, Modifiers.NONE, false));
        assertEquals(10, slider.value(), 0.001f);
    }

    @Test
    void drawsTheTrackAndThumb() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Slider slider = new Slider();
        slider.setRange(0, 100);
        slider.setValue(50);
        slider.setBounds(new Rect(0, 0, 200, 20));
        manager.add(slider);

        manager.render();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(3, commands.size());
        RoundedRectCommand thumb = assertInstanceOf(RoundedRectCommand.class, commands.get(2));
        assertEquals(manager.context().theme().text(), thumb.color());
    }

    @Test
    void rejectsInvalidConfiguration() {
        Slider slider = new Slider();
        assertThrows(IllegalArgumentException.class, () -> slider.setRange(1, 1));
        assertThrows(IllegalArgumentException.class, () -> slider.setRange(2, 1));
        assertThrows(IllegalArgumentException.class, () -> slider.setRange(Float.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> slider.setValue(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> slider.setStep(-1));
        assertThrows(IllegalArgumentException.class, () -> slider.setStep(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> slider.setThumbSize(0));
        assertThrows(IllegalArgumentException.class, () -> slider.setTrackThickness(-1));
        assertThrows(IllegalArgumentException.class, () -> slider.setTickCount(-1));
        assertThrows(NullPointerException.class, () -> slider.setOrientation(null));
    }
}
