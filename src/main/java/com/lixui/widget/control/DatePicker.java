package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.MouseButton;
import com.lixui.input.Modifiers;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A date field that opens a popup calendar.
 * The calendar shows a month title, single month arrows and double year arrows and a six-week grid
 * with localized month and weekday names. Days of the previous and next month stay visible in a
 * muted color and select their own date when clicked. The selected day is highlighted, clicking a
 * day applies it, closes the popup and reports the date through {@link #setOnChange(Consumer)}.
 * Month and year navigation and Escape do not change the selected date.
 */
public class DatePicker extends PickerField {
    private static final float NAV_ICON_SIZE = 16;
    private static final float HEADER_GAP = 6;

    private final Button previousYearButton = new Button();
    private final Button previousMonthButton = new Button();
    private final Button nextMonthButton = new Button();
    private final Button nextYearButton = new Button();
    private final Label titleLabel = new Label();
    private final CalendarGrid grid;
    private final VBox popupContent = new VBox();
    private final IconSet navIcons = new IconSet("arrow-double-left-05.svg",
            "arrow-left-04.svg", "arrow-right-04.svg", "arrow-double-right-05.svg");

    private LocalDate date;
    private Locale locale;
    private DateTimeFormatter dateFormatter;
    private YearMonth displayedMonth;
    private Consumer<LocalDate> onChange;

    /**
     * Creates a picker showing today with the default locale.
     */
    public DatePicker() {
        this(LocalDate.now());
    }

    /**
     * Creates a picker showing the supplied date with the default locale.
     *
     * @param date selected date
     * @throws NullPointerException if {@code date} is null
     */
    public DatePicker(LocalDate date) {
        this(date, Locale.getDefault(Locale.Category.FORMAT));
    }

    /**
     * Creates a picker showing the supplied date and locale.
     *
     * @param date selected date
     * @param locale locale used for month and weekday names and the field text
     * @throws NullPointerException if an argument is null
     */
    public DatePicker(LocalDate date, Locale locale) {
        super("calendar.svg");
        this.date = Objects.requireNonNull(date, "date");
        this.locale = Objects.requireNonNull(locale, "locale");
        dateFormatter = createDateFormatter(locale);
        displayedMonth = YearMonth.from(date);
        grid = new CalendarGrid(this::activateDate);
        configureNavButton(previousYearButton, "Previous year", () -> shiftYear(-1));
        configureNavButton(previousMonthButton, "Previous month", () -> shiftMonth(-1));
        configureNavButton(nextMonthButton, "Next month", () -> shiftMonth(1));
        configureNavButton(nextYearButton, "Next year", () -> shiftYear(1));
        titleLabel.setAutoSize(false);
        titleLabel.setWrap(false);
        titleLabel.setAlignment(HorizontalAlignment.CENTER);
        titleLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        titleLabel.setGrow(1);
        HBox header = new HBox();
        header.setGap(HEADER_GAP);
        header.setCrossAlignment(HBox.CrossAlignment.CENTER);
        header.add(previousYearButton).add(previousMonthButton).add(titleLabel)
                .add(nextMonthButton).add(nextYearButton);
        popupContent.setGap(HEADER_GAP);
        popupContent.add(header).add(grid);
        popup().setPadding(8);
        popup().setContent(popupContent);
        updateHeader();
    }

    private static void configureNavButton(Button button, String tooltip, Runnable action) {
        button.setIconSize(NAV_ICON_SIZE);
        button.setTooltipText(tooltip);
        button.setOnAction(action);
    }

    /**
     * Returns the selected date.
     *
     * @return the selected date
     */
    public final LocalDate date() {
        return date;
    }

    /**
     * Replaces the selected date.
     * The change callback runs only when the date actually changes. An open popup follows the new
     * month.
     *
     * @param date new date
     * @throws NullPointerException if {@code date} is null
     */
    public final void setDate(LocalDate date) {
        LocalDate next = Objects.requireNonNull(date, "date");
        if (this.date.equals(next)) {
            return;
        }
        this.date = next;
        if (popup().isOpen()) {
            displayedMonth = YearMonth.from(next);
            grid.setState(displayedMonth, next, locale);
            updateHeader();
        }
        if (onChange != null) {
            onChange.accept(next);
        }
    }

    /**
     * Returns the locale used for month and weekday names and the field text.
     *
     * @return the calendar locale
     */
    public final Locale locale() {
        return locale;
    }

    /**
     * Replaces the locale used for month and weekday names and the field text.
     *
     * @param locale new locale
     * @throws NullPointerException if {@code locale} is null
     */
    public final void setLocale(Locale locale) {
        Locale next = Objects.requireNonNull(locale, "locale");
        if (this.locale.equals(next)) {
            return;
        }
        this.locale = next;
        dateFormatter = createDateFormatter(next);
        if (popup().isOpen()) {
            grid.setState(displayedMonth, date, next);
            updateHeader();
        }
    }

    /**
     * Returns the date change callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<LocalDate> onChange() {
        return onChange;
    }

    /**
     * Assigns the callback invoked when the selected date changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(Consumer<LocalDate> onChange) {
        this.onChange = onChange;
    }

    /**
     * Returns the localized field text.
     *
     * @return the formatted selected date
     */
    @Override
    protected String fieldText() {
        return date.format(dateFormatter);
    }

    /**
     * Opens the popup on the month of the selected date.
     */
    @Override
    protected void openPopup() {
        if (context() == null || popup().isOpen()) {
            return;
        }
        displayedMonth = YearMonth.from(date);
        grid.setState(displayedMonth, date, locale);
        updateHeader();
        Rect anchor = new Rect(absolutePosition().x(), absolutePosition().y(),
                bounds().width(), bounds().height());
        popup().open(context(), anchor);
    }

    /**
     * Loads the navigation icons after the picker attaches.
     */
    @Override
    protected void onAttached() {
        super.onAttached();
        applyNavIcons();
    }

    /**
     * Refreshes the navigation icons after a context change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        applyNavIcons();
    }

    /**
     * Releases the navigation icons when the picker leaves the tree.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        navIcons.release(context());
    }

    private void shiftMonth(int delta) {
        if (!popup().isOpen()) {
            return;
        }
        displayedMonth = displayedMonth.plusMonths(delta);
        grid.setState(displayedMonth, date, locale);
        updateHeader();
    }

    private void shiftYear(int delta) {
        if (!popup().isOpen()) {
            return;
        }
        displayedMonth = displayedMonth.plusYears(delta);
        grid.setState(displayedMonth, date, locale);
        updateHeader();
    }

    private void activateDate(LocalDate value) {
        displayedMonth = YearMonth.from(value);
        closePopup();
        setDate(value);
    }

    private void updateHeader() {
        titleLabel.setText(displayedMonth.format(
                DateTimeFormatter.ofPattern("LLLL yyyy").withLocale(locale)));
    }

    private void applyNavIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(NAV_ICON_SIZE * context.density()));
        if (!navIcons.matches(context, size)) {
            navIcons.reload(context, size);
        }
        previousYearButton.setIcon(navIcons.get(0));
        previousMonthButton.setIcon(navIcons.get(1));
        nextMonthButton.setIcon(navIcons.get(2));
        nextYearButton.setIcon(navIcons.get(3));
    }

    private static DateTimeFormatter createDateFormatter(Locale locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
    }

    /**
     * A six-week calendar grid with localized weekday labels and day hit testing.
     */
    static final class CalendarGrid extends Widget {
        private static final int COLUMNS = 7;
        private static final int ROWS = 6;
        private static final float CELL_WIDTH = 32;
        private static final float WEEKDAY_HEIGHT = 22;
        private static final float CELL_HEIGHT = 32;
        private static final float DAY_INSET = 2;
        private static final float DAY_RADIUS = 6;
        private static final LocalDate REFERENCE_MONDAY = LocalDate.of(2024, 1, 1);

        private final Consumer<LocalDate> activator;
        private YearMonth month = YearMonth.now();
        private LocalDate selected = LocalDate.now();
        private Locale locale = Locale.getDefault();
        private DateTimeFormatter weekdayFormatter = DateTimeFormatter.ofPattern("ccc");
        private DayOfWeek firstDayOfWeek = DayOfWeek.MONDAY;
        private LocalDate firstVisible = LocalDate.now();
        private int dayCount;
        private LocalDate hovered;

        private CalendarGrid(Consumer<LocalDate> activator) {
            this.activator = activator;
            updateMonth();
        }

        private void setState(YearMonth month, LocalDate selected, Locale locale) {
            this.month = Objects.requireNonNull(month, "month");
            this.selected = Objects.requireNonNull(selected, "selected");
            this.locale = Objects.requireNonNull(locale, "locale");
            weekdayFormatter = DateTimeFormatter.ofPattern("ccc").withLocale(locale);
            updateMonth();
            hovered = null;
        }

        private void updateMonth() {
            firstDayOfWeek = WeekFields.of(locale).getFirstDayOfWeek();
            int leadingBlanks = Math.floorMod(
                    month.atDay(1).getDayOfWeek().getValue() - firstDayOfWeek.getValue(), 7);
            dayCount = month.lengthOfMonth();
            firstVisible = month.atDay(1).minusDays(leadingBlanks);
        }

        /**
         * Measures the fixed six-week grid.
         *
         * @param constraints available and required dimensions
         * @return the natural grid size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(Math.min(COLUMNS * CELL_WIDTH, constraints.maxWidth()),
                    Math.min(WEEKDAY_HEIGHT + ROWS * CELL_HEIGHT, constraints.maxHeight()));
        }

        /**
         * Paints the weekday labels and the visible days of the month.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Font font = font();
            if (font == null || localBounds().isEmpty()) {
                return;
            }
            Theme theme = context().theme();
            float cellWidth = localBounds().width() / COLUMNS;
            float cellHeight = (localBounds().height() - WEEKDAY_HEIGHT) / ROWS;
            if (cellWidth <= 0 || cellHeight <= 0) {
                return;
            }
            TextStyle centered = TextStyle.of(HorizontalAlignment.CENTER,
                    VerticalAlignment.CENTER);
            for (int column = 0; column < COLUMNS; column++) {
                DayOfWeek day = firstDayOfWeek.plus(column);
                LocalDate weekday = REFERENCE_MONDAY.plusDays(day.getValue() - 1L);
                renderer.text(font, weekday.format(weekdayFormatter),
                        column * cellWidth + cellWidth / 2, WEEKDAY_HEIGHT / 2,
                        theme.textMuted(), centered);
            }
            LocalDate today = LocalDate.now();
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    LocalDate value = firstVisible.plusDays(row * COLUMNS + column);
                    Rect cell = cellRect(row, column, cellWidth, cellHeight);
                    if (value.equals(selected)) {
                        renderer.roundedRect(cell, DAY_RADIUS, theme.primary());
                    } else if (value.equals(hovered)) {
                        renderer.roundedRect(cell, DAY_RADIUS, theme.surfaceHover());
                    } else if (value.equals(today)) {
                        renderer.strokeRoundedRect(cell, DAY_RADIUS, 1,
                                Paint.solid(theme.primary().withAlpha(0.55f)));
                    }
                    boolean currentMonth = value.getYear() == month.getYear()
                            && value.getMonth() == month.getMonth();
                    Color textColor = value.equals(selected) ? theme.onPrimary()
                            : currentMonth ? theme.text() : theme.textMuted().withAlpha(0.65f);
                    renderer.text(font, Integer.toString(value.getDayOfMonth()),
                            cell.x() + cell.width() / 2, cell.y() + cell.height() / 2,
                            textColor, centered);
                }
            }
        }

        /**
         * Tracks the hovered day.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @return false so pointer movement can bubble
         */
        @Override
        protected boolean onPointerMove(float x, float y) {
            hovered = dateAt(x, y);
            return false;
        }

        /**
         * Clears the hovered day when the pointer leaves.
         */
        @Override
        protected void onPointerExit() {
            hovered = null;
        }

        /**
         * Returns a pointer cursor over selectable days.
         *
         * @return the pointer cursor over a day, otherwise the default cursor
         */
        @Override
        public Cursor cursor() {
            return hovered != null ? Cursor.POINTER : Cursor.DEFAULT;
        }

        /**
         * Activates the clicked day, including days of the adjacent months.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the click selected a day
         */
        @Override
        protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            LocalDate value = dateAt(x, y);
            if (value == null) {
                return false;
            }
            activator.accept(value);
            return true;
        }

        private Rect cellRect(int row, int column, float cellWidth, float cellHeight) {
            return new Rect(column * cellWidth + DAY_INSET,
                    WEEKDAY_HEIGHT + row * cellHeight + DAY_INSET,
                    Math.max(0, cellWidth - DAY_INSET * 2),
                    Math.max(0, cellHeight - DAY_INSET * 2));
        }

        Rect dayBounds(int day) {
            return day >= 1 && day <= dayCount ? dateBounds(month.atDay(day)) : Rect.EMPTY;
        }

        Rect dateBounds(LocalDate value) {
            long index = firstVisible.until(value, ChronoUnit.DAYS);
            if (index < 0 || index >= (long) ROWS * COLUMNS) {
                return Rect.EMPTY;
            }
            int row = (int) index / COLUMNS;
            int column = (int) index % COLUMNS;
            float cellWidth = localBounds().width() / COLUMNS;
            float cellHeight = (localBounds().height() - WEEKDAY_HEIGHT) / ROWS;
            return new Rect(column * cellWidth, WEEKDAY_HEIGHT + row * cellHeight,
                    cellWidth, cellHeight);
        }

        private LocalDate dateAt(float x, float y) {
            float cellWidth = localBounds().width() / COLUMNS;
            float cellHeight = (localBounds().height() - WEEKDAY_HEIGHT) / ROWS;
            if (cellWidth <= 0 || cellHeight <= 0 || x < 0 || y < WEEKDAY_HEIGHT) {
                return null;
            }
            int column = (int) (x / cellWidth);
            int row = (int) ((y - WEEKDAY_HEIGHT) / cellHeight);
            if (column < 0 || column >= COLUMNS || row < 0 || row >= ROWS) {
                return null;
            }
            return firstVisible.plusDays(row * COLUMNS + column);
        }
    }
}
