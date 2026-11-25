package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

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
public class TimePicker extends PickerField {
    private static final float LIST_WIDTH = 84;
    private static final float LIST_GAP = 6;
    private static final float ITEM_HEIGHT = 24;
    private static final float CHECK_SIZE = 14;
    private static final float MAX_LIST_HEIGHT = 190;
    private static final float CONTENT_GAP = 6;
    private static final int MAX_MANUAL_LENGTH = 8;
    private static final Pattern MANUAL_PATTERN = Pattern.compile("^(\\d{1,2})\\s*[:.]\\s*(\\d{1,2})$");

    private final VBox popupContent = new VBox();
    private final Input manualInput = new Input();
    private final HBox listRow = new HBox();
    // OptionList draws its check mark from index 1, so the first slot is unused here.
    private final IconSet listIcons = new IconSet("tick.svg", "tick.svg");
    private final OptionList hours;
    private final OptionList minutes;

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
        super("clock.svg");
        Objects.requireNonNull(time, "time");
        hour = time.getHour();
        minute = time.getMinute();
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
        popup().setPadding(6);
        popup().setContent(popupContent);
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
     * Returns the field text.
     *
     * @return the formatted time
     */
    @Override
    protected String fieldText() {
        return formattedTime();
    }

    /**
     * Opens the popup and focuses the manual entry field.
     */
    @Override
    protected void openPopup() {
        if (context() == null || popup().isOpen()) {
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
        popup().open(context(), anchor);
        hours.setHighlighted(hour);
        minutes.setHighlighted(minute / minuteStep);
        context().requestFocus(manualInput);
    }

    /**
     * Closes the popup and releases the list icons when the picker leaves the tree.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        listIcons.release(context());
    }

    /**
     * Drops the cached list icons after a context change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
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
}
