package com.lixui.widget.control;

import com.lixui.geometry.Size;
import com.lixui.text.Font;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A numeric field with a unit selector that stores the value in base units.
 * Each unit carries a label and a conversion factor, so the displayed amount is
 * {@code value / factor}. Typing converts the amount into the base value, Enter normalizes the
 * amount, and changing the unit keeps the base value and rewrites only the displayed number.
 * A duration preset with millisecond, second, minute and hour units is also provided.
 */
public class UnitInput extends Widget {
    private static final float GAP = 6;
    private static final int MAX_DECIMALS = 9;
    private static final int MAX_INPUT_LENGTH = 32;
    private static final float MIN_UNIT_FIELD_WIDTH = 88;
    private static final float UNIT_TEXT_ALLOWANCE = 60;

    private final HBox content = new HBox();
    private final Input amount = new Input();
    private final Dropdown unitDropdown = new Dropdown();
    private final List<UnitOption> units = new ArrayList<>();
    private int unitIndex;
    private double value;
    private int decimals = 3;
    private boolean syncing;
    private Consumer<Double> onChange;

    /**
     * Creates a unit input with a single fallback unit.
     */
    public UnitInput() {
        content.setGap(GAP);
        content.setCrossAlignment(HBox.CrossAlignment.CENTER);
        amount.setGrow(1);
        amount.setAllowedCharacters(Input.CharacterKind.DIGITS, Input.CharacterKind.SYMBOLS);
        amount.setMaxLength(MAX_INPUT_LENGTH);
        amount.setOnChange(this::parseChange);
        amount.setOnSubmit(this::commitInput);
        unitDropdown.setOnChange(this::selectUnit);
        content.add(amount).add(unitDropdown);
        add(content);
        setUnits(List.of(new UnitOption("unit", 1)));
    }

    /**
     * Returns the value in base units.
     *
     * @return the stored value
     */
    public final double value() {
        return value;
    }

    /**
     * Replaces the value in base units.
     * The displayed amount is recalculated for the selected unit, and the change callback runs when
     * the value actually changes.
     *
     * @param value new value in base units
     * @throws IllegalArgumentException if the value is non-finite
     */
    public final void setValue(double value) {
        requireFinite(value);
        boolean changed = value != this.value;
        this.value = value;
        syncAmount();
        if (changed) {
            notifyChange();
        }
    }

    /**
     * Returns the configured units.
     *
     * @return an immutable snapshot of the units
     */
    public final List<UnitOption> units() {
        return List.copyOf(units);
    }

    /**
     * Replaces the available units.
     * The current unit is kept when it is still present, otherwise the first unit is selected.
     * The base value does not change.
     *
     * @param options non-empty unit list
     * @throws NullPointerException if the list or an option is null
     * @throws IllegalArgumentException if the list is empty
     */
    public final void setUnits(List<UnitOption> options) {
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("At least one unit is required.");
        }
        for (UnitOption option : options) {
            Objects.requireNonNull(option, "unit");
        }
        UnitOption previous = unitIndex < units.size() ? units.get(unitIndex) : null;
        units.clear();
        units.addAll(options);
        unitIndex = previous == null ? 0 : Math.max(0, units.indexOf(previous));
        syncing = true;
        try {
            unitDropdown.clearOptions();
            for (UnitOption unit : units) {
                unitDropdown.addOption(new SelectOption(unit.label(), unit));
            }
            unitDropdown.setSelectedIndex(unitIndex);
        } finally {
            syncing = false;
        }
        updateUnitFieldWidth();
        syncAmount();
    }

    /**
     * Returns the selected unit.
     *
     * @return the current unit
     */
    public final UnitOption unit() {
        return units.get(unitIndex);
    }

    /**
     * Selects a unit from the configured list.
     * The base value does not change; only the displayed amount is recalculated.
     *
     * @param unit unit to select
     * @throws NullPointerException if {@code unit} is null
     * @throws IllegalArgumentException if the unit is not configured
     */
    public final void setUnit(UnitOption unit) {
        int index = units.indexOf(Objects.requireNonNull(unit, "unit"));
        if (index < 0) {
            throw new IllegalArgumentException("Unit is not configured.");
        }
        if (index == unitIndex) {
            return;
        }
        unitIndex = index;
        syncing = true;
        try {
            unitDropdown.setSelectedIndex(index);
        } finally {
            syncing = false;
        }
        syncAmount();
    }

    /**
     * Returns the number of decimal places used for the displayed amount.
     *
     * @return the decimal place count
     */
    public final int decimals() {
        return decimals;
    }

    /**
     * Sets the number of decimal places used for the displayed amount.
     *
     * @param decimals decimal place count from zero up to nine
     * @throws IllegalArgumentException if the count is out of range
     */
    public final void setDecimals(int decimals) {
        if (decimals < 0 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("Decimals must be between zero and nine.");
        }
        this.decimals = decimals;
        syncAmount();
    }

    /**
     * Returns the value change callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<Double> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked with the base value after it changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(Consumer<Double> onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the field and unit selector row.
     *
     * @param constraints available and required dimensions
     * @return the natural input size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return content.measure(constraints);
    }

    /**
     * Stretches the row over the assigned bounds and mirrors the enabled state to its controls.
     */
    @Override
    protected void onLayout() {
        updateUnitFieldWidth();
        content.layout(localBounds());
        amount.setEnabled(isEnabled());
        unitDropdown.setEnabled(isEnabled());
    }

    /**
     * Keeps the empty space around the controls transparent to pointer input.
     *
     * @param x horizontal coordinate relative to the widget's top-left corner
     * @param y vertical coordinate relative to the widget's top-left corner
     * @return the hit child, or null when the point is outside the controls
     */
    @Override
    public Widget hitTest(float x, float y) {
        Widget hit = super.hitTest(x, y);
        return hit == this ? null : hit;
    }

    private void selectUnit(SelectOption option) {
        if (syncing || option == null) {
            return;
        }
        int index = units.indexOf(option.value());
        if (index < 0 || index == unitIndex) {
            return;
        }
        unitIndex = index;
        syncAmount();
    }

    private void parseChange(String text) {
        if (syncing) {
            return;
        }
        Double parsed = parse(text);
        if (parsed == null) {
            return;
        }
        double parsedValue = parsed * unit().factor();
        if (!Double.isFinite(parsedValue)) {
            return;
        }
        if (parsedValue != value) {
            value = parsedValue;
            notifyChange();
        }
    }

    private void commitInput() {
        Double parsed = parse(amount.text());
        if (parsed != null) {
            double parsedValue = parsed * unit().factor();
            if (Double.isFinite(parsedValue) && parsedValue != value) {
                value = parsedValue;
                notifyChange();
            }
        }
        syncAmount();
    }

    private void updateUnitFieldWidth() {
        float textWidth = 0;
        Font font = font();
        if (font != null) {
            for (UnitOption unit : units) {
                textWidth = Math.max(textWidth, font.measure(unit.label()).width());
            }
        }
        unitDropdown.setMinimumSize(Math.max(MIN_UNIT_FIELD_WIDTH,
                textWidth + UNIT_TEXT_ALLOWANCE), 0);
    }

    private void syncAmount() {
        double displayed = value / unit().factor();
        syncing = true;
        try {
            amount.setText(format(displayed));
        } finally {
            syncing = false;
        }
    }

    private String format(double number) {
        if (!Double.isFinite(number)) {
            return "0";
        }
        String text = String.format(Locale.ROOT, "%." + decimals + "f", number);
        if (decimals > 0) {
            int end = text.length();
            while (end > 0 && text.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && text.charAt(end - 1) == '.') {
                end--;
            }
            text = text.substring(0, end);
        }
        return text.equals("-0") ? "0" : text;
    }

    private static Double parse(String text) {
        String trimmed = text.trim().replace(',', '.');
        if (trimmed.isEmpty() || trimmed.equals("-") || trimmed.equals(".")
                || trimmed.equals("-.")) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(trimmed);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(value);
        }
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Unit value must be finite.");
        }
    }

    /**
     * A selectable unit with a display label and a conversion factor.
     * The factor is the number of base units in one displayed unit.
     *
     * @param label display label
     * @param factor positive conversion factor
     */
    public record UnitOption(String label, double factor) {
        /**
         * Creates a unit option.
         *
         * @param label display label
         * @param factor positive conversion factor
         * @throws NullPointerException if {@code label} is null
         * @throws IllegalArgumentException if the label is blank or the factor is not positive or
         *                                  non-finite
         */
        public UnitOption {
            Objects.requireNonNull(label, "label");
            if (label.isBlank()) {
                throw new IllegalArgumentException("Unit label must not be blank.");
            }
            if (!Double.isFinite(factor) || factor <= 0) {
                throw new IllegalArgumentException("Unit factor must be finite and positive.");
            }
        }
    }
}
