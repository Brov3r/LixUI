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
import com.lixui.render.command.TextCommand;
import com.lixui.text.FontData;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.Popup;
import com.lixui.widget.overlay.PromptDialog;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimePickerTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final Modifiers CONTROL = new Modifiers(false, true, false, false);

    @Test
    void exposesAndNormalizesTheSelectedTime() {
        TimePicker picker = new TimePicker(LocalTime.of(9, 5));
        List<LocalTime> changes = new ArrayList<>();
        picker.setOnChange(changes::add);

        assertEquals(LocalTime.of(9, 5), picker.time());
        assertEquals(9, picker.hour());
        assertEquals(5, picker.minute());
        assertEquals(5, picker.minuteStep());

        picker.setTime(LocalTime.of(23, 59, 30));
        assertEquals(LocalTime.of(23, 59), picker.time());
        assertEquals(List.of(LocalTime.of(23, 59)), changes);

        picker.setTime(23, 59);
        picker.setHour(7);
        picker.setMinute(0);

        assertEquals(List.of(LocalTime.of(23, 59), LocalTime.of(7, 59),
                LocalTime.of(7, 0)), changes);

        assertThrows(IllegalArgumentException.class, () -> picker.setHour(24));
        assertThrows(IllegalArgumentException.class, () -> picker.setHour(-1));
        assertThrows(IllegalArgumentException.class, () -> picker.setMinute(60));
        assertThrows(IllegalArgumentException.class, () -> picker.setMinute(-1));
        assertThrows(IllegalArgumentException.class, () -> picker.setMinuteStep(0));
        assertThrows(IllegalArgumentException.class, () -> picker.setMinuteStep(61));
        assertThrows(IllegalArgumentException.class, () -> picker.setIconSize(0));
        assertThrows(NullPointerException.class, () -> picker.setTime(null));
        assertThrows(NullPointerException.class, () -> new TimePicker(null));
    }

    @Test
    void selectsHourAndMinuteFromThePopupColumns() {
        WidgetManager manager = manager();
        TimePicker picker = picker(LocalTime.of(9, 30), 15);
        List<LocalTime> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();

        clickField(manager, picker);
        manager.render();

        assertTrue(picker.isPopupOpen());
        Popup popup = popup(manager);
        List<OptionList> lists = optionLists(popup.content());
        assertEquals(2, lists.size());
        OptionList hourList = lists.get(0);
        OptionList minuteList = lists.get(1);
        assertEquals(24, optionCount(hourList));
        assertEquals(4, optionCount(minuteList));

        clickOption(manager, hourList, 8);

        assertTrue(picker.isPopupOpen());
        assertEquals(LocalTime.of(8, 30), picker.time());

        clickOption(manager, minuteList, 3);

        assertFalse(picker.isPopupOpen());
        assertEquals(LocalTime.of(8, 45), picker.time());
        assertEquals(List.of(LocalTime.of(8, 30), LocalTime.of(8, 45)), changes);
        assertTrue(picker.isFocused());
    }

    @Test
    void opensFromTheKeyboardAndAcceptsManualOrCancelledEntry() {
        WidgetManager manager = manager();
        TimePicker picker = picker(LocalTime.of(9, 30), 15);
        List<LocalTime> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        manager.focus(picker);

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.render();

        assertTrue(picker.isPopupOpen());
        Input manual = findInput(popup(manager).content());
        assertSame(manual, manager.focused());

        manager.handle(new KeyPressed(Key.A, CONTROL, false));
        for (char character : "07:05".toCharArray()) {
            manager.handle(new CharTyped(character));
        }
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertEquals(LocalTime.of(7, 5), picker.time());
        assertEquals(List.of(LocalTime.of(7, 5)), changes);
        assertFalse(picker.isPopupOpen());
        assertTrue(picker.isFocused());

        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.render();

        assertTrue(picker.isPopupOpen());

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(picker.isPopupOpen());
        assertEquals(LocalTime.of(7, 5), picker.time());
        assertTrue(picker.isFocused());
    }

    @Test
    void centersTheFieldTextAndManualInputInsideTheIconSlot() {
        WidgetManager manager = manager();
        TimePicker picker = picker(LocalTime.of(23, 59), 15);
        manager.add(picker);
        manager.render();

        WidgetStyle style = manager.context().theme().controlStyle();
        assertEquals(HorizontalAlignment.CENTER, picker.fieldTextAlignment());

        Rect textBounds = picker.fieldTextBounds(style);
        assertTrue(textBounds.width() > 0);
        assertEquals(style.padding(), textBounds.x(), 0.001f);
        assertTrue(textBounds.right() <= picker.bounds().width() - picker.iconSize());
        assertTrue(textBounds.right() < picker.bounds().width() - style.padding()
                - picker.iconSize());

        clickField(manager, picker);
        manager.render();

        Input manual = findInput(popup(manager).content());
        assertEquals(HorizontalAlignment.CENTER, manual.textAlignment());
    }

    @Test
    void centersTheRenderedValueInTheFieldAndTheManualField() {
        FontData data = SystemFonts.findDefault().orElse(null);
        Assumptions.assumeTrue(data != null, "No readable system font is installed.");
        for (Viewport viewport : List.of(TestViewports.LOGICAL, TestViewports.HIDPI)) {
            for (Theme theme : List.of(Theme.dark(), Theme.light())) {
                TestBackend backend = new TestBackend();
                try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
                    manager.resize(viewport);
                    manager.setTheme(theme);
                    manager.setDefaultFont(manager.context().createFont(data, 16));
                    TimePicker picker = picker(LocalTime.of(9, 30), 15);
                    manager.add(picker);
                    manager.render();

                    WidgetStyle style = manager.context().theme().controlStyle();
                    assertFieldTextCentered(backend, picker, style);

                    picker.setTime(23, 59);
                    manager.render();
                    assertFieldTextCentered(backend, picker, style);

                    clickField(manager, picker);
                    manager.render();
                    assertManualTextCentered(backend,
                            findInput(popup(manager).content()), style);

                    manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
                }
            }
        }
    }

    @Test
    void escapeKeepsAnAppliedHourSelection() {
        WidgetManager manager = manager();
        TimePicker picker = picker(LocalTime.of(9, 30), 15);
        List<LocalTime> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        clickField(manager, picker);
        manager.render();

        OptionList hourList = optionLists(popup(manager).content()).get(0);
        clickOption(manager, hourList, 8);

        assertTrue(picker.isPopupOpen());
        assertEquals(LocalTime.of(8, 30), picker.time());
        assertEquals(List.of(LocalTime.of(8, 30)), changes);

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(picker.isPopupOpen());
        assertEquals(LocalTime.of(8, 30), picker.time());
        assertEquals(List.of(LocalTime.of(8, 30)), changes);
        assertTrue(picker.isFocused());
    }

    @Test
    void changeCallbackCanOpenAnotherDialogWithoutLosingFocus() {
        WidgetManager manager = manager();
        TimePicker picker = picker(LocalTime.of(9, 30), 15);
        PromptDialog prompt = new PromptDialog("Follow-up", "The picker already closed.", "value");
        picker.setOnChange(value -> {
            if (value.equals(LocalTime.of(8, 45))) {
                prompt.show(manager.context());
            }
        });
        manager.add(picker);
        manager.render();
        clickField(manager, picker);
        manager.render();

        List<OptionList> lists = optionLists(popup(manager).content());
        clickOption(manager, lists.get(0), 8);
        clickOption(manager, lists.get(1), 3);

        assertFalse(picker.isPopupOpen());
        assertTrue(prompt.isOpen());
        assertTrue(isWithin(manager.focused(), prompt));

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(prompt.isOpen());
        assertTrue(picker.isFocused());

        picker.setOnChange(value -> {
            if (value.equals(LocalTime.of(7, 5))) {
                prompt.show(manager.context());
            }
        });
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));
        manager.render();
        manager.handle(new KeyPressed(Key.A, CONTROL, false));
        for (char character : "07:05".toCharArray()) {
            manager.handle(new CharTyped(character));
        }
        manager.handle(new KeyPressed(Key.ENTER, Modifiers.NONE, false));

        assertFalse(picker.isPopupOpen());
        assertTrue(prompt.isOpen());
        assertTrue(isWithin(manager.focused(), prompt));
    }

    private static boolean isWithin(Widget widget, Widget ancestor) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static TimePicker picker(LocalTime time, int minuteStep) {
        TimePicker picker = new TimePicker(time);
        picker.setMinuteStep(minuteStep);
        picker.setBounds(new Rect(10, 10, 180, 36));
        picker.setMinimumSize(120, 36);
        return picker;
    }

    private static void clickField(WidgetManager manager, TimePicker picker) {
        Point origin = picker.absolutePosition();
        click(manager, origin.x() + picker.bounds().width() / 2,
                origin.y() + picker.bounds().height() / 2);
    }

    private static void clickOption(WidgetManager manager, OptionList list, int index) {
        Point origin = list.absolutePosition();
        float top = index * list.itemHeight() - list.scrollY();
        click(manager, origin.x() + list.bounds().width() / 2,
                origin.y() + top + list.itemHeight() / 2);
    }

    private static int optionCount(OptionList list) {
        return Math.round(list.preferredHeight() / list.itemHeight());
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

    private static List<OptionList> optionLists(Widget root) {
        List<OptionList> lists = new ArrayList<>();
        collectOptionLists(root, lists);
        return lists;
    }

    private static void collectOptionLists(Widget root, List<OptionList> lists) {
        for (Widget child : root.children()) {
            if (child instanceof OptionList list) {
                lists.add(list);
            }
            collectOptionLists(child, lists);
        }
    }

    private static Input findInput(Widget root) {
        for (Widget child : root.children()) {
            if (child instanceof Input input) {
                return input;
            }
            Input nested = findInputOrNull(child);
            if (nested != null) {
                return nested;
            }
        }
        throw new AssertionError("Input not found.");
    }

    private static Input findInputOrNull(Widget root) {
        for (Widget child : root.children()) {
            if (child instanceof Input input) {
                return input;
            }
            Input nested = findInputOrNull(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static void assertFieldTextCentered(TestBackend backend, TimePicker picker,
                                                WidgetStyle style) {
        TextCommand text = textCommandIn(backend, localBounds(picker));
        Rect area = picker.fieldTextBounds(style);
        float center = area.x() + area.width() / 2;
        assertEquals(center, text.bounds().center().x(), 1.5f, "field text center");
        assertTrue(text.bounds().x() >= area.x() - 0.5f, "field text starts inside its area");
        float iconLeft = picker.bounds().width() - style.padding() - picker.iconSize();
        assertTrue(text.bounds().right() <= iconLeft, "field text must not reach the icon");
    }

    private static void assertManualTextCentered(TestBackend backend, Input manual,
                                                 WidgetStyle style) {
        Rect content = manual.contentRect(style);
        Point origin = manual.absolutePosition();
        Rect clip = new Rect(origin.x() + content.x(), origin.y() + content.y(),
                content.width(), content.height());
        TextCommand text = textCommandWithClip(backend, clip);
        float center = content.x() + content.width() / 2;
        assertEquals(center, text.bounds().center().x(), 1.5f, "manual text center");
        assertTrue(text.bounds().x() >= content.x() - 0.5f,
                "manual text starts inside its area");
    }

    private static TextCommand textCommandIn(TestBackend backend, Rect bounds) {
        return backend.lastDrawList().commands().stream()
                .filter(TextCommand.class::isInstance)
                .map(TextCommand.class::cast)
                .filter(command -> bounds.contains(command.bounds().center().x(),
                        command.bounds().center().y()))
                .findFirst()
                .orElseThrow();
    }

    private static TextCommand textCommandWithClip(TestBackend backend, Rect clip) {
        return backend.lastDrawList().commands().stream()
                .filter(TextCommand.class::isInstance)
                .map(TextCommand.class::cast)
                .filter(command -> command.clip().equals(clip))
                .findFirst()
                .orElseThrow();
    }

    private static Rect localBounds(Widget widget) {
        return new Rect(0, 0, widget.bounds().width(), widget.bounds().height());
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        return manager;
    }
}
