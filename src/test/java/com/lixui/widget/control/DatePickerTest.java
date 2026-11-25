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
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.Popup;
import com.lixui.widget.overlay.PromptDialog;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatePickerTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final Locale LOCALE = Locale.US;

    @Test
    void exposesTheSelectedDateAndValidatesArguments() {
        DatePicker picker = new DatePicker(LocalDate.of(2026, 5, 12), LOCALE);
        List<LocalDate> changes = new ArrayList<>();
        picker.setOnChange(changes::add);

        assertEquals(LocalDate.of(2026, 5, 12), picker.date());
        assertEquals(LOCALE, picker.locale());

        picker.setDate(LocalDate.of(2026, 5, 12));
        assertTrue(changes.isEmpty());

        picker.setDate(LocalDate.of(2026, 6, 1));

        assertEquals(List.of(LocalDate.of(2026, 6, 1)), changes);
        assertEquals(LocalDate.of(2026, 6, 1), picker.date());

        picker.setLocale(Locale.GERMANY);
        assertEquals(Locale.GERMANY, picker.locale());

        assertThrows(NullPointerException.class, () -> picker.setDate(null));
        assertThrows(NullPointerException.class, () -> picker.setLocale(null));
        assertThrows(NullPointerException.class, () -> new DatePicker(null));
        assertThrows(NullPointerException.class,
                () -> new DatePicker(LocalDate.of(2026, 5, 12), null));
    }

    @Test
    void selectsADayAndKeepsFocusInACallbackDialog() {
        WidgetManager manager = manager();
        DatePicker picker = picker(LocalDate.of(2026, 5, 12), LOCALE);
        PromptDialog prompt = new PromptDialog("Follow-up", "The picker already closed.", "value");
        picker.setOnChange(value -> {
            if (value.equals(LocalDate.of(2026, 5, 20))) {
                prompt.show(manager.context());
            }
        });
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        DatePicker.CalendarGrid grid = grid(popup(manager));
        clickDay(manager, grid, 20);

        assertEquals(LocalDate.of(2026, 5, 20), picker.date());
        assertFalse(picker.isPopupOpen());
        assertTrue(prompt.isOpen());
        assertTrue(isWithin(manager.focused(), prompt));

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(prompt.isOpen());
        assertTrue(picker.isFocused());
    }

    @Test
    void navigatesMonthsAndEscapeKeepsTheSelectedDate() {
        WidgetManager manager = manager();
        DatePicker picker = picker(LocalDate.of(2026, 5, 12), LOCALE);
        List<LocalDate> changes = new ArrayList<>();
        picker.setOnChange(changes::add);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        Popup popup = popup(manager);
        Button next = (Button) popup.content().children().get(0).children().get(2);
        clickWidget(manager, next);
        manager.render();

        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(picker.isPopupOpen());
        assertEquals(LocalDate.of(2026, 5, 12), picker.date());
        assertTrue(changes.isEmpty());
        assertTrue(picker.isFocused());

        openPopup(manager, picker);
        clickWidget(manager, next);
        manager.render();

        DatePicker.CalendarGrid grid = grid(popup(manager));
        clickDay(manager, grid, 15);

        assertFalse(picker.isPopupOpen());
        assertEquals(LocalDate.of(2026, 6, 15), picker.date());
        assertEquals(List.of(LocalDate.of(2026, 6, 15)), changes);
        assertTrue(picker.isFocused());
    }

    @Test
    void startsWeeksAccordingToLocale() {
        WidgetManager usManager = manager();
        DatePicker.CalendarGrid usGrid = gridFor(usManager, LocalDate.of(2026, 8, 1), LOCALE);
        assertEquals(6, columnOf(usGrid, 1));
        assertEquals(0, columnOf(usGrid, 2));

        WidgetManager ruManager = manager();
        Locale ru = Locale.forLanguageTag("ru-RU");
        DatePicker.CalendarGrid ruGrid = gridFor(ruManager, LocalDate.of(2026, 8, 1), ru);
        assertEquals(5, columnOf(ruGrid, 1));
        assertEquals(6, columnOf(ruGrid, 2));
    }

    @Test
    void arrangesAMonthThatNeedsSixRows() {
        WidgetManager manager = manager();
        LocalDate first = LocalDate.of(2026, 8, 1);
        DatePicker picker = picker(first, LOCALE);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);
        DatePicker.CalendarGrid grid = grid(popup(manager));

        int leading = Math.floorMod(first.getDayOfWeek().getValue()
                - WeekFields.of(LOCALE).getFirstDayOfWeek().getValue(), 7);
        assertEquals(6, leading);
        assertTrue(leading + first.lengthOfMonth() > 35);

        Rect last = grid.dayBounds(31);
        assertFalse(last.isEmpty());
        assertEquals(grid.bounds().height(), last.bottom(), 0.001f);

        clickDay(manager, grid, 31);

        assertEquals(LocalDate.of(2026, 8, 31), picker.date());
        assertFalse(picker.isPopupOpen());
    }

    @Test
    void handlesLeapFebruary() {
        WidgetManager leapManager = manager();
        DatePicker leapPicker = picker(LocalDate.of(2024, 2, 1), LOCALE);
        leapManager.add(leapPicker);
        leapManager.render();
        openPopup(leapManager, leapPicker);
        DatePicker.CalendarGrid leapGrid = grid(popup(leapManager));
        assertFalse(leapGrid.dayBounds(29).isEmpty());

        clickDay(leapManager, leapGrid, 29);

        assertEquals(LocalDate.of(2024, 2, 29), leapPicker.date());
        assertFalse(leapPicker.isPopupOpen());

        WidgetManager commonManager = manager();
        DatePicker commonPicker = picker(LocalDate.of(2026, 2, 1), LOCALE);
        commonManager.add(commonPicker);
        commonManager.render();
        openPopup(commonManager, commonPicker);
        DatePicker.CalendarGrid commonGrid = grid(popup(commonManager));

        assertTrue(commonGrid.dayBounds(29).isEmpty());
        assertFalse(commonGrid.dayBounds(28).isEmpty());

        clickDay(commonManager, commonGrid, 28);

        assertEquals(LocalDate.of(2026, 2, 28), commonPicker.date());
    }

    private static DatePicker picker(LocalDate date, Locale locale) {
        DatePicker picker = new DatePicker(date, locale);
        picker.setBounds(new Rect(10, 10, 200, 36));
        picker.setMinimumSize(140, 36);
        return picker;
    }

    private static void openPopup(WidgetManager manager, DatePicker picker) {
        Point origin = picker.absolutePosition();
        click(manager, origin.x() + picker.bounds().width() / 2,
                origin.y() + picker.bounds().height() / 2);
        manager.render();
        assertTrue(picker.isPopupOpen());
    }

    private static DatePicker.CalendarGrid grid(Popup popup) {
        for (Widget child : popup.content().children()) {
            if (child instanceof DatePicker.CalendarGrid grid) {
                return grid;
            }
        }
        throw new AssertionError("Calendar grid not found.");
    }

    private static DatePicker.CalendarGrid gridFor(WidgetManager manager, LocalDate date,
                                                    Locale locale) {
        DatePicker picker = picker(date, locale);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);
        return grid(popup(manager));
    }

    private static int columnOf(DatePicker.CalendarGrid grid, int day) {
        float cellWidth = grid.bounds().width() / 7f;
        return Math.round(grid.dayBounds(day).x() / cellWidth);
    }

    private static void clickDay(WidgetManager manager, DatePicker.CalendarGrid grid, int day) {
        Rect bounds = grid.dayBounds(day);
        Point origin = grid.absolutePosition();
        click(manager, origin.x() + bounds.x() + bounds.width() / 2,
                origin.y() + bounds.y() + bounds.height() / 2);
    }

    private static void clickWidget(WidgetManager manager, Widget widget) {
        Point origin = widget.absolutePosition();
        click(manager, origin.x() + widget.bounds().width() / 2,
                origin.y() + widget.bounds().height() / 2);
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

    private static boolean isWithin(Widget widget, Widget ancestor) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        return manager;
    }
}
