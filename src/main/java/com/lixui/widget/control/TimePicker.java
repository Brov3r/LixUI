package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.overlay.Popup;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A time field that opens a popup with selectable hour and minute columns and manual text entry.
 * The field shows the selected time as {@code HH:mm}. The popup offers every hour and the minutes
 * matching {@link #minuteStep()}, while the text field inside the popup accepts any minute and
 * commits on Enter. Hour selections apply and are reported immediately while the popup stays open;
 * minute selections and manual Enter close the popup after applying. Escape dismisses the popup and
 * keeps any hour already applied, matching the immediate update contract. Every accepted change is
 * reported through {@link #setOnChange(Consumer)}.
 */
public class TimePicker extends Panel {
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float ICON_GAP = 6;
    private static final float LIST_WIDTH = 84;
    private static final float LIST_GAP = 6;
    private static final float ITEM_HEIGHT = 24;
    private static final float CHECK_SIZE = 14;
    private static final float MAX_LIST_HEIGHT = 190;
    private static final float CONTENT_GAP = 6;
    private static final int MAX_MANUAL_LENGTH = 8;
    private static final Pattern MANUAL_PATTERN = Pattern.compile("^(\\d{1,2})\\s*[:.]\\s*(\\d{1,2})$");

    private final Popup popup = new Popup();
    private final VBox popupContent = new VBox();
    private final Input manualInput = new Input();
    private final HBox listRow = new HBox();
    private final IconSet clockIcons = new IconSet("clock.svg");
    // OptionList draws its check mark from index 1, so the first slot is unused here.
    private final IconSet listIcons = new IconSet("tick.svg", "tick.svg");
    private final OptionList hours;
    private final OptionList minutes;

    private float iconSize = DEFAULT_ICON_SIZE;
    private int hour;
    private int minute;
    private int minuteStep = 5;
    private Consumer<LocalTime> onChange;

    /**
     * Creates a picker showing midnight with five-minute steps.
     */
    public TimePicker() {
        this(LocalTime.MIDNIGHT);
    }

    /**
     * Creates a picker showing the supplied time with five-minute steps.
     *
     * @param time initial time; seconds and nanoseconds are ignored
     * @throws NullPointerException if {@code time} is null
     */
    public TimePicker(LocalTime time) {
        Objects.requireNonNull(time, "time");
        hour = time.getHour();
        minute = time.getMinute();
        setFocusable(true);
        hours = createOptionList(false);
        minutes = createOptionList(true);
        manualInput.setPlaceholder("HH:mm");
        manualInput.setMaxLength(MAX_MANUAL_LENGTH);
        manualInput.setAllowedCharacters(Input.CharacterKind.DIGITS, Input.CharacterKind.SYMBOLS);
        manualInput.setOnSubmit(this::commitManualInput);
        listRow.setGap(LIST_GAP);
        listRow.add(new ListSlot(hours)).add(new ListSlot(minutes));
        popupContent.setGap(CONTENT_GAP);
        popupContent.add(manualInput).add(listRow);
        popup.setPadding(6);
        popup.setFocusScope(true);
        popup.setContent(popupContent);
        popup.setCloseListener(this::restoreFieldFocus);
        syncManualInput();
    }

    /**
     * Returns the selected time.
     *
     * @return the selected time without seconds
     */
    public final LocalTime time() {
        return LocalTime.of(hour, minute);
    }

    /**
     * Replaces the selected time.
     * Seconds and nanoseconds are ignored, and the change callback runs only when the hour or
     * minute actually changes.
     *
     * @param time new time
     * @throws NullPointerException if {@code time} is null
     */
    public final void setTime(LocalTime time) {
        Objects.requireNonNull(time, "time");
        setTime(time.getHour(), time.getMinute());
    }

    /**
     * Replaces the selected time.
     * The change callback runs only when the hour or minute actually changes.
     *
     * @param hour hour from zero to twenty-three
     * @param minute minute from zero to fifty-nine
     * @throws IllegalArgumentException if a component is out of range
     */
    public final void setTime(int hour, int minute) {
        requireHour(hour);
        requireMinute(minute);
        applyTime(hour, minute);
    }

    /**
     * Returns the selected hour.
     *
     * @return the hour from zero to twenty-three
     */
    public final int hour() {
        return hour;
    }

    /**
     * Replaces the selected hour.
     * The minute is preserved.
     *
     * @param hour hour from zero to twenty-three
     * @throws IllegalArgumentException if the hour is out of range
     */
    public final void setHour(int hour) {
        requireHour(hour);
        applyTime(hour, minute);
    }

    /**
     * Returns the selected minute.
     *
     * @return the minute from zero to fifty-nine
     */
    public final int minute() {
        return minute;
    }

    /**
     * Replaces the selected minute.
     * The hour is preserved.
     *
     * @param minute minute from zero to fifty-nine
     * @throws IllegalArgumentException if the minute is out of range
     */
    public final void setMinute(int minute) {
        requireMinute(minute);
        applyTime(hour, minute);
    }

    /**
     * Returns the step between minute options.
     *
     * @return the minute step
     */
    public final int minuteStep() {
        return minuteStep;
    }

    /**
     * Sets the step between minute options.
     * The current minute is kept even when it is not a multiple of the new step. Changing the step
     * closes an open popup.
     *
     * @param minuteStep step from one to sixty minutes
     * @throws IllegalArgumentException if the step is out of range
     */
    public final void setMinuteStep(int minuteStep) {
        if (minuteStep < 1 || minuteStep > 60) {
            throw new IllegalArgumentException("Minute step must be between one and sixty.");
        }
        if (this.minuteStep == minuteStep) {
            return;
        }
        this.minuteStep = minuteStep;
        closePopup();
    }

    /**
     * Returns the icon edge length used by the field.
     *
     * @return the icon size in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the icon edge length used by the field.
     *
     * @param iconSize positive icon size in logical UI units
     * @throws IllegalArgumentException if the size is non-finite or not positive
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the time change callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<LocalTime> onChange() {
        return onChange;
    }

    /**
     * Assigns the callback invoked when the selected time changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(Consumer<LocalTime> onChange) {
        this.onChange = onChange;
    }

    /**
     * Reports whether the selection popup is open.
     *
     * @return true while the popup is visible
     */
    public final boolean isPopupOpen() {
        return popup.isOpen();
    }

    /**
     * Measures the time text and the trailing clock icon.
     *
     * @param constraints available and required dimensions
     * @return the natural field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        WidgetStyle style = resolvedStyle();
        if (style == null) {
            return super.onMeasure(constraints);
        }
        Font font = font();
        Size textSize = font == null ? new Size(0, 0) : font.measure(formattedTime());
        float contentWidth = textSize.width() + iconSize + ICON_GAP;
        float contentHeight = Math.max(textSize.height(), iconSize);
        float padding = style.padding();
        return new Size(contentWidth + padding * 2, contentHeight + padding * 2);
    }

    /**
     * Paints the field surface, the time text and the clock icon.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        WidgetStyle style = resolvedStyle();
        if (style == null || localBounds().isEmpty()) {
            return;
        }
        WidgetState state = state();
        renderer.roundedRect(localBounds(), style.cornerRadius(), style.background(state));
        drawBorder(renderer, style, state);
        Font font = font();
        float padding = style.padding();
        float iconX = localBounds().width() - padding - iconSize;
        float contentWidth = Math.max(0, iconX - ICON_GAP - padding);
        if (font != null) {
            renderer.text(font, formattedTime(), padding, localBounds().height() / 2,
                    style.text(state), new TextStyle(contentWidth, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
        ensureClockIcon();
        Texture clock = clockIcons.get(0);
        if (clock != null) {
            renderer.icon(clock, new Rect(iconX, (localBounds().height() - iconSize) / 2,
                    iconSize, iconSize), ImageStyle.ICON, style.text(state));
        }
    }

    private void drawBorder(RenderManager renderer, WidgetStyle style, WidgetState state) {
        float width = style.borderWidth();
        if (width <= 0) {
            return;
        }
        float inset = width / 2;
        Rect bounds = localBounds();
        Rect border = new Rect(inset, inset,
                Math.max(0, bounds.width() - width), Math.max(0, bounds.height() - width));
        if (border.isEmpty()) {
            return;
        }
        renderer.strokeRoundedRect(border, Math.max(0, style.cornerRadius() - inset),
                width, Paint.solid(style.border(state)));
    }

    /**
     * Returns the pointer cursor for the field.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Opens the popup on a completed left click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click was consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT || !isEnabled()) {
            return false;
        }
        openPopup();
        return true;
    }

    /**
     * Opens the popup on a double click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button double-clicked button
     * @param modifiers active modifiers
     * @return true when the double click was consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return onClick(x, y, button, modifiers);
    }

    /**
     * Opens the popup with Enter, Space or an arrow key and closes it on Escape.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ESCAPE && popup.isOpen()) {
            closePopup();
            return true;
        }
        if (key == Key.ENTER || key == Key.SPACE || key == Key.UP || key == Key.DOWN) {
            openPopup();
            return true;
        }
        return false;
    }

    /**
     * Closes the popup and releases the field icons when the picker leaves the tree.
     */
    @Override
    protected void onDetached() {
        closePopup();
        super.onDetached();
        clockIcons.release(context());
        listIcons.release(context());
    }

    /**
     * Closes the popup and drops cached icons after a context change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        closePopup();
        clockIcons.release(context());
        listIcons.release(context());
    }

    private OptionList createOptionList(boolean minuteList) {
        return new OptionList(new OptionList.SelectionModel() {
            @Override
            public boolean isSelected(int index) {
                return minuteList ? index * minuteStep == minute : index == hour;
            }

            @Override
            public void activate(int index) {
                if (minuteList) {
                    closePopup();
                    setMinute(index * minuteStep);
                } else {
                    setHour(index);
                }
            }
        }, this::closePopup, listIcons, ITEM_HEIGHT, CHECK_SIZE);
    }

    private void openPopup() {
        if (context() == null || popup.isOpen()) {
            return;
        }
        List<SelectOption> hourOptions = new ArrayList<>(24);
        for (int value = 0; value < 24; value++) {
            hourOptions.add(new SelectOption(twoDigits(value)));
        }
        List<SelectOption> minuteOptions = new ArrayList<>();
        for (int value = 0; value < 60; value += minuteStep) {
            minuteOptions.add(new SelectOption(twoDigits(value)));
        }
        hours.setOptions(hourOptions);
        minutes.setOptions(minuteOptions);
        syncManualInput();
        Rect anchor = new Rect(absolutePosition().x(), absolutePosition().y(),
                bounds().width(), bounds().height());
        popup.open(context(), anchor);
        hours.setHighlighted(hour);
        minutes.setHighlighted(minute / minuteStep);
        context().requestFocus(manualInput);
    }

    private void closePopup() {
        popup.close();
    }

    private void commitManualInput() {
        Matcher matcher = MANUAL_PATTERN.matcher(manualInput.text().trim());
        if (!matcher.matches()) {
            syncManualInput();
            return;
        }
        int parsedHour = Integer.parseInt(matcher.group(1));
        int parsedMinute = Integer.parseInt(matcher.group(2));
        if (parsedHour > 23 || parsedMinute > 59) {
            syncManualInput();
            return;
        }
        closePopup();
        setTime(parsedHour, parsedMinute);
    }

    private void applyTime(int hour, int minute) {
        boolean changed = this.hour != hour || this.minute != minute;
        this.hour = hour;
        this.minute = minute;
        syncManualInput();
        if (changed && onChange != null) {
            onChange.accept(time());
        }
    }

    private void syncManualInput() {
        manualInput.setText(formattedTime());
    }

    private String formattedTime() {
        return twoDigits(hour) + ":" + twoDigits(minute);
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", value);
    }

    private void ensureClockIcon() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(iconSize * context.density()));
        if (!clockIcons.matches(context, size)) {
            clockIcons.reload(context, size);
        }
    }

    private void restoreFieldFocus() {
        if (context() == null || !isEnabled()) {
            return;
        }
        try {
            requestFocus();
        } catch (IllegalArgumentException ignored) {
            // The picker may be detaching while its popup is being closed.
        }
    }

    private WidgetStyle resolvedStyle() {
        if (hasLocalStyle()) {
            return style();
        }
        return context() == null ? null : context().theme().controlStyle();
    }

    /**
     * Constrains an option list to the popup column width and maximum height.
     */
    private static final class ListSlot extends Widget {
        private final OptionList list;

        private ListSlot(OptionList list) {
            this.list = list;
            add(list);
        }

        /**
         * Measures the list inside the fixed column width and height limit.
         *
         * @param constraints available and required dimensions
         * @return the constrained column size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            float width = Math.min(LIST_WIDTH, constraints.maxWidth());
            Size natural = list.measure(LayoutConstraints.loose(width,
                    Math.min(MAX_LIST_HEIGHT, constraints.maxHeight())));
            return new Size(width, Math.min(natural.height(), MAX_LIST_HEIGHT));
        }

        /**
         * Stretches the list over the slot bounds.
         */
        @Override
        protected void onLayout() {
            list.layout(new Rect(0, 0, bounds().width(), bounds().height()));
        }
    }

    private static void requireHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour must be between zero and twenty-three.");
        }
    }

    private static void requireMinute(int minute) {
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Minute must be between zero and fifty-nine.");
        }
    }
}
