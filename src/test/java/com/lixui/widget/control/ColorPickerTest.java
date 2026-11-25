package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
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
import com.lixui.widget.overlay.Popup;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorPickerTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final float TOLERANCE = 0.01f;

    @Test
    void exposesAndNormalizesTheSelectedColor() {
        Color initial = Color.hex("#FF000080");
        ColorPicker picker = new ColorPicker(initial);
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);

        assertEquals(initial.toRgba(), picker.color().toRgba());

        picker.setColor(initial);
        assertTrue(changes.isEmpty());

        Color green = Color.hex("#00FF00");
        picker.setColor(green);

        assertEquals(green.toRgba(), picker.color().toRgba());
        assertEquals(1, changes.size());

        assertThrows(NullPointerException.class, () -> picker.setColor(null));
        assertThrows(NullPointerException.class, () -> new ColorPicker(null));
    }

    @Test
    void updatesTheColorFromTheSquareAndStrips() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(Color.hex("#FF0000"));
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Widget> content = popup(manager).content().children();
        Widget square = content.get(0);
        Widget hueStrip = content.get(1);
        Widget alphaStrip = content.get(2);

        clickAt(manager, square, 0.5f, 0.5f);
        assertColor(picker.color(), 0.5f, 0.25f, 0.25f, 1);

        clickAt(manager, hueStrip, 0.5f, 0.5f);
        assertColor(picker.color(), 0.25f, 0.5f, 0.5f, 1);

        clickAt(manager, alphaStrip, 0.5f, 0.5f);
        assertColor(picker.color(), 0.25f, 0.5f, 0.5f, 0.5f);

        assertEquals(3, changes.size());
        assertTrue(picker.isPopupOpen());

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(picker.isPopupOpen());
        assertTrue(picker.isFocused());
    }

    @Test
    void appliesHexAndRgbFieldChanges() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(Color.hex("#FF0000"));
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Input> inputs = inputs(popup(manager).content());
        assertEquals(4, inputs.size());
        Input hex = inputs.get(0);
        Input red = inputs.get(1);

        hex.setText("#0000FF");
        manager.focus(hex);
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertColor(picker.color(), 0, 0, 1, 1);
        assertEquals("#0000FF", hex.text());
        assertEquals(1, changes.size());

        red.setText("128");

        assertColor(picker.color(), 128 / 255f, 0, 1, 1);
        assertEquals(2, changes.size());
    }

    @Test
    void preservesHueWhileSaturationOrBrightnessIsZero() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(Color.WHITE);
        List<Color> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Widget> content = popup(manager).content().children();
        Widget square = content.get(0);
        Widget hueStrip = content.get(1);

        clickAt(manager, hueStrip, 0.5f, 0.5f);
        assertColor(picker.color(), 1, 1, 1, 1);
        assertTrue(changes.isEmpty());

        clickAt(manager, square, 0.5f, 0.5f);
        assertColor(picker.color(), 0.25f, 0.5f, 0.5f, 1);
        assertEquals(1, changes.size());

        picker.setColor(Color.BLACK);
        changes.clear();

        clickAt(manager, hueStrip, 0.25f, 0.5f);
        assertColor(picker.color(), 0, 0, 0, 1);
        assertTrue(changes.isEmpty());

        clickAt(manager, square, 0.5f, 0.5f);
        assertColor(picker.color(), 0.375f, 0.5f, 0.25f, 1);
        assertEquals(1, changes.size());
    }

    @Test
    void keepsAlphaWhenEditingRgbOrSixDigitHex() {
        WidgetManager manager = manager();
        ColorPicker picker = picker(new Color(1, 0, 0, 0.5f));
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        List<Input> inputs = inputs(popup(manager).content());
        Input hex = inputs.get(0);
        Input red = inputs.get(1);

        red.setText("128");
        assertColor(picker.color(), 128 / 255f, 0, 0, 0.5f);

        hex.setText("#00FF00");
        manager.focus(hex);
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertColor(picker.color(), 0, 1, 0, 0.5f);
        assertEquals("#00FF0080", hex.text());

        hex.setText("#0000FF40");
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertColor(picker.color(), 0, 0, 1, 64 / 255f);
    }

    private static ColorPicker picker(Color color) {
        ColorPicker picker = new ColorPicker(color);
        picker.setBounds(new Rect(10, 10, 220, 36));
        picker.setMinimumSize(160, 36);
        return picker;
    }

    private static void openPopup(WidgetManager manager, ColorPicker picker) {
        Point origin = picker.absolutePosition();
        click(manager, origin.x() + picker.bounds().width() / 2,
                origin.y() + picker.bounds().height() / 2);
        manager.render();
        assertTrue(picker.isPopupOpen());
    }

    private static void clickAt(WidgetManager manager, Widget widget, float fractionX,
                                float fractionY) {
        Point origin = widget.absolutePosition();
        click(manager, origin.x() + widget.bounds().width() * fractionX,
                origin.y() + widget.bounds().height() * fractionY);
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static List<Input> inputs(Widget root) {
        List<Input> inputs = new ArrayList<>();
        collectInputs(root, inputs);
        return inputs;
    }

    private static void collectInputs(Widget root, List<Input> inputs) {
        for (Widget child : root.children()) {
            if (child instanceof Input input) {
                inputs.add(input);
            }
            collectInputs(child, inputs);
        }
    }

    private static void assertColor(Color color, float red, float green, float blue, float alpha) {
        assertEquals(red, color.r(), TOLERANCE);
        assertEquals(green, color.g(), TOLERANCE);
        assertEquals(blue, color.b(), TOLERANCE);
        assertEquals(alpha, color.a(), TOLERANCE);
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
