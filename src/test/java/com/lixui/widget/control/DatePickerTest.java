package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.render.Viewport;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.command.TextCommand;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.overlay.Popup;
import com.lixui.widget.overlay.PromptDialog;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.tooltip.TextTooltip;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        Button next = (Button) header(popup).get(3);
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
    void changesMonthAndYearFromHeaderArrows() {
        WidgetManager manager = manager();
        DatePicker picker = picker(LocalDate.of(2026, 5, 12), LOCALE);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        Popup popup = popup(manager);
        List<Widget> header = header(popup);
        assertEquals("Previous year", tooltip(header.get(0)));
        assertEquals("Previous month", tooltip(header.get(1)));
        assertEquals("Next month", tooltip(header.get(3)));
        assertEquals("Next year", tooltip(header.get(4)));

        clickWidget(manager, header.get(0));
        manager.render();
        clickDay(manager, grid(popup), 10);

        assertEquals(LocalDate.of(2025, 5, 10), picker.date());

        openPopup(manager, picker);
        clickWidget(manager, header(popup).get(4));
        manager.render();
        clickDay(manager, grid(popup), 15);

        assertEquals(LocalDate.of(2026, 5, 15), picker.date());
    }

    @Test
    void drawsAdjacentMonthDaysInMutedColor() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        manager.setDefaultFont(font(manager));
        DatePicker picker = picker(LocalDate.of(2026, 5, 12), LOCALE);
        manager.add(picker);
        manager.render();
        openPopup(manager, picker);

        DatePicker.CalendarGrid grid = grid(popup(manager));
        assertFalse(grid.dateBounds(LocalDate.of(2026, 4, 30)).isEmpty());
        assertFalse(grid.dateBounds(LocalDate.of(2026, 6, 1)).isEmpty());

        Color muted = manager.context().theme().textMuted().withAlpha(0.65f);
        boolean mutedDrawn = backend.lastDrawList().commands().stream()
                .filter(TextCommand.class::isInstance)
                .map(TextCommand.class::cast)
                .flatMap(command -> command.colors().stream())
                .anyMatch(muted::equals);
        assertTrue(mutedDrawn);
    }

    @Test
    void selectsAdjacentMonthDays() {
        WidgetManager previousManager = manager();
        DatePicker previousPicker = picker(LocalDate.of(2026, 5, 12), LOCALE);
        previousManager.add(previousPicker);
        previousManager.render();
        openPopup(previousManager, previousPicker);

        clickDate(previousManager, grid(popup(previousManager)), LocalDate.of(2026, 4, 30));

        assertEquals(LocalDate.of(2026, 4, 30), previousPicker.date());
        assertFalse(previousPicker.isPopupOpen());

        WidgetManager nextManager = manager();
        DatePicker nextPicker = picker(LocalDate.of(2026, 5, 12), LOCALE);
        nextManager.add(nextPicker);
        nextManager.render();
        openPopup(nextManager, nextPicker);

        clickDate(nextManager, grid(popup(nextManager)), LocalDate.of(2026, 6, 1));

        assertEquals(LocalDate.of(2026, 6, 1), nextPicker.date());
        assertFalse(nextPicker.isPopupOpen());
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

    @Test
    void keepsThePopupSizeFixedAcrossMonthsLocalesAndLongNames() {
        Size expected = null;
        for (Locale locale : List.of(Locale.US, Locale.forLanguageTag("ru-RU"))) {
            for (LocalDate date : List.of(LocalDate.of(2026, 2, 1),
                    LocalDate.of(2026, 5, 12), LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 9, 30))) {
                TestBackend backend = new TestBackend();
                try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
                    manager.resize(VIEWPORT);
                    DatePicker picker = picker(date, locale);
                    manager.add(picker);
                    manager.render();
                    openPopup(manager, picker);

                    Popup popup = popup(manager);
                    Size size = popupSize(popup);
                    if (expected == null) {
                        expected = size;
                    } else {
                        assertEquals(expected.width(), size.width(), 0.001f,
                                "Popup width changed for " + locale + " and " + date);
                        assertEquals(expected.height(), size.height(), 0.001f,
                                "Popup height changed for " + locale + " and " + date);
                    }

                    Widget title = header(popup).get(2);
                    float titleWidth = title.bounds().width();
                    clickWidget(manager, header(popup).get(3));
                    manager.render();

                    assertEquals(expected.width(), popupSize(popup).width(), 0.001f);
                    assertEquals(expected.height(), popupSize(popup).height(), 0.001f);
                    assertEquals(titleWidth, title.bounds().width(), 0.001f);
                    assertTrue(title.bounds().right()
                            <= popup.content().bounds().width() + 0.001f);

                    manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
                }
            }
        }
        assertTrue(expected.width() > 0);
        assertTrue(expected.height() > 0);
    }

    @Test
    void drawsNavigationArrowsAsTransparentHoverTargets() {
        for (Theme theme : List.of(Theme.dark(), Theme.light())) {
            TestBackend backend = new TestBackend();
            try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
                manager.resize(VIEWPORT);
                manager.setTheme(theme);
                DatePicker picker = picker(LocalDate.of(2026, 5, 12), LOCALE);
                manager.add(picker);
                manager.render();
                openPopup(manager, picker);

                List<Widget> header = header(popup(manager));
                for (int index : List.of(0, 1, 3, 4)) {
                    Button button = assertInstanceOf(Button.class, header.get(index));
                    WidgetStyle style = button.surfaceStyle();
                    assertEquals(0, style.background(WidgetState.NORMAL).a(), 0.001f);
                    assertEquals(0, style.background(WidgetState.FOCUSED).a(), 0.001f);
                    assertEquals(0, style.borderWidth(), 0.001f);
                    assertEquals(0, style.border(WidgetState.NORMAL).a(), 0.001f);
                    assertTrue(style.background(WidgetState.HOVERED).a() > 0);
                    assertTrue(style.background(WidgetState.HOVERED).a() < 1);
                    assertEquals(Cursor.POINTER, button.cursor());
                }

                Button nextMonth = (Button) header.get(3);
                Rect buttonBounds = absoluteBounds(nextMonth);
                assertFalse(backend.lastDrawList().commands().stream()
                        .anyMatch(command -> command instanceof RoundedRectCommand rounded
                                && rounded.bounds().equals(buttonBounds)));

                Point center = nextMonth.absolutePosition();
                float x = center.x() + nextMonth.bounds().width() / 2;
                float y = center.y() + nextMonth.bounds().height() / 2;
                manager.handle(new PointerMoved(x, y));
                manager.render();
                assertTrue(hasRoundedColor(backend, buttonBounds,
                        manager.context().theme().surfaceHover().withAlpha(0.5f)));

                manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
                manager.render();
                assertTrue(hasRoundedColor(backend, buttonBounds,
                        manager.context().theme().surfacePressed().withAlpha(0.65f)));

                manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
                assertTrue(picker.isPopupOpen());

                manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));
            }
        }
    }

    private static Size popupSize(Popup popup) {
        Rect bounds = popup.popupBounds();
        return new Size(bounds.width(), bounds.height());
    }

    private static boolean hasRoundedColor(TestBackend backend, Rect bounds, Color color) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.bounds().equals(bounds) && command.color().equals(color));
    }

    private static Rect absoluteBounds(Widget widget) {
        Point origin = widget.absolutePosition();
        return new Rect(origin.x(), origin.y(), widget.bounds().width(),
                widget.bounds().height());
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

    private static List<Widget> header(Popup popup) {
        return popup.content().children().get(0).children();
    }

    private static String tooltip(Widget widget) {
        return ((TextTooltip) widget.tooltip()).text();
    }

    private static void clickDate(WidgetManager manager, DatePicker.CalendarGrid grid,
                                  LocalDate date) {
        Rect bounds = grid.dateBounds(date);
        Point origin = grid.absolutePosition();
        click(manager, origin.x() + bounds.x() + bounds.width() / 2,
                origin.y() + bounds.y() + bounds.height() / 2);
    }

    private static Font font(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        return manager.context().createFont(data.orElseThrow(), 16);
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
