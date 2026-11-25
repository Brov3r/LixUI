package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.text.FontStyle;
import com.lixui.text.TextOverflow;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.container.ScrollPanel;
import com.lixui.widget.layout.Grid;
import com.lixui.widget.layout.GridTrack;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A scrollable grid of typed property editors grouped into sections.
 * Every row consists of a label and an editor chosen by the property type: a check box for
 * booleans, a text field for strings, a numeric stepper for numbers and a dropdown for choices.
 * A search field hides rows that do not match the label or key, and a reset button restores every
 * property to its default value.
 */
public class PropertyGrid extends Widget {
    private static final float HEADER_GAP = 8;
    private static final float ROW_GAP = 6;
    private static final float LABEL_GAP = 10;
    private static final float LABEL_COLUMN_WIDTH = 120;
    private static final float SECTION_GAP = 10;
    private static final float CONTENT_PADDING = 4;

    private final VBox root = new VBox();
    private final HBox header = new HBox();
    private final SearchInput search = new SearchInput();
    private final Button resetButton = new Button("Reset");
    private final FittingScrollPanel scroll = new FittingScrollPanel();
    private final VBox content = new VBox();
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private final List<RowEntry> rows = new ArrayList<>();
    private final List<Property<?>> properties = new ArrayList<>();
    private Consumer<Property<?>> onValueChange;
    private String query = "";
    private boolean silent;

    /**
     * Creates an empty property grid.
     */
    public PropertyGrid() {
        header.setGap(HEADER_GAP);
        header.setCrossAlignment(HBox.CrossAlignment.CENTER);
        search.setGrow(1);
        search.setPlaceholder("Search properties...");
        search.setOnSearch(this::applyFilter);
        resetButton.setOnAction(this::reset);
        header.add(search).add(resetButton);

        content.setGap(ROW_GAP);
        content.setPadding(CONTENT_PADDING);
        scroll.setContent(content);
        scroll.setGrow(1);

        root.setGap(SECTION_GAP);
        root.add(header).add(scroll);
        add(root);
    }

    /**
     * Adds a boolean row backed by a check box.
     *
     * @param key unique property key
     * @param label row label
     * @param section section title
     * @param initial initial and default value
     * @return the created property
     */
    public final BooleanProperty addBoolean(String key, String label, String section,
                                            boolean initial) {
        return register(new BooleanProperty(this, requireKey(key), label, section, initial));
    }

    /**
     * Adds a text row backed by a single-line input.
     *
     * @param key unique property key
     * @param label row label
     * @param section section title
     * @param initial initial and default value
     * @return the created property
     */
    public final TextProperty addText(String key, String label, String section, String initial) {
        return register(new TextProperty(this, requireKey(key), label, section,
                Objects.requireNonNull(initial, "initial")));
    }

    /**
     * Adds a number row with the default range {@code 0..100}, unit steps and no decimals.
     *
     * @param key unique property key
     * @param label row label
     * @param section section title
     * @param initial initial and default value
     * @return the created property
     */
    public final NumberProperty addNumber(String key, String label, String section,
                                          double initial) {
        return addNumber(key, label, section, initial, 0, 100, 1, 0);
    }

    /**
     * Adds a number row backed by a numeric stepper.
     *
     * @param key unique property key
     * @param label row label
     * @param section section title
     * @param initial initial and default value
     * @param minimum inclusive lower bound
     * @param maximum inclusive upper bound
     * @param step positive increment
     * @param decimals decimal places from zero to nine
     * @return the created property
     */
    public final NumberProperty addNumber(String key, String label, String section, double initial,
                                          double minimum, double maximum, double step,
                                          int decimals) {
        NumberProperty property = new NumberProperty(this, requireKey(key), label, section,
                initial, minimum, maximum, step, decimals);
        return register(property);
    }

    /**
     * Adds a choice row backed by a dropdown.
     *
     * @param key unique property key
     * @param label row label
     * @param section section title
     * @param options option labels
     * @param selectedIndex initially selected index, or -1 for no selection
     * @return the created property
     * @throws NullPointerException if an option is null
     * @throws IllegalArgumentException if no option is given or the index is out of range
     */
    public final SelectProperty addSelect(String key, String label, String section,
                                          List<String> options, int selectedIndex) {
        return register(new SelectProperty(this, requireKey(key), label, section, options,
                selectedIndex));
    }

    /**
     * Returns the properties in registration order.
     *
     * @return an immutable snapshot of the properties
     */
    public final List<Property<?>> properties() {
        return List.copyOf(properties);
    }

    /**
     * Returns the current row filter.
     *
     * @return the filter text, or an empty string when every row is shown
     */
    public final String query() {
        return query;
    }

    /**
     * Replaces the row filter and hides rows whose label and key do not contain it.
     * Matching ignores case. The filter is applied immediately, while typing in the grid is
     * debounced.
     *
     * @param query filter text
     * @throws NullPointerException if {@code query} is null
     */
    public final void setQuery(String query) {
        applyFilter(Objects.requireNonNull(query, "query"));
        search.setText(query);
    }

    /**
     * Restores every property to its default value.
     * Each property that actually changes is reported once through the value callback.
     */
    public final void reset() {
        List<Property<?>> changed = new ArrayList<>();
        runSilently(() -> {
            for (Property<?> property : properties) {
                if (property.resetValue()) {
                    changed.add(property);
                }
            }
        });
        for (Property<?> property : changed) {
            notifyChange(property);
        }
    }

    /**
     * Returns the callback invoked after a property value changes.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<Property<?>> onValueChange() {
        return onValueChange;
    }

    /**
     * Assigns a callback invoked with the changed property.
     *
     * @param onValueChange callback, or null to clear it
     */
    public final void setOnValueChange(Consumer<Property<?>> onValueChange) {
        this.onValueChange = onValueChange;
    }

    /**
     * Measures the header and the scrollable property rows.
     *
     * @param constraints available and required dimensions
     * @return the natural grid size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return root.measure(constraints);
    }

    /**
     * Places the header above the scrollable property rows.
     */
    @Override
    protected void onLayout() {
        root.layout(localBounds());
    }

    private <T extends Property<?>> T register(T property) {
        if (rows.stream().anyMatch(row -> row.property.key().equals(property.key()))) {
            throw new IllegalArgumentException("Property key is already registered: "
                    + property.key());
        }
        Section section = sections.computeIfAbsent(property.section(), this::createSection);
        Widget editor = property.createEditor();
        property.bindEditor(editor);
        editor.setGrow(1);
        Label label = new Label(property.label());
        label.setWrap(false);
        label.setOverflow(TextOverflow.ELLIPSIS);
        section.body.add(label).add(editor);
        RowEntry entry = new RowEntry(property, label, section);
        rows.add(entry);
        properties.add(property);
        applyRowVisibility(entry);
        updateSectionVisibility(section);
        return property;
    }

    private Section createSection(String title) {
        Label header = new Label(title);
        header.setFontStyle(FontStyle.BOLD);
        Grid body = new Grid();
        body.setColumns(GridTrack.fixed(LABEL_COLUMN_WIDTH), GridTrack.proportional(1));
        body.setGap(LABEL_GAP);
        content.add(header).add(body);
        return new Section(header, body);
    }

    private void applyFilter(String next) {
        if (query.equals(next)) {
            return;
        }
        query = next;
        String needle = next.trim().toLowerCase(Locale.ROOT);
        for (RowEntry row : rows) {
            setRowVisible(row, needle.isEmpty()
                    || row.property.label().toLowerCase(Locale.ROOT).contains(needle)
                    || row.property.key().toLowerCase(Locale.ROOT).contains(needle));
        }
        for (Section section : sections.values()) {
            updateSectionVisibility(section);
        }
    }

    private void updateSectionVisibility(Section section) {
        boolean visible = rows.stream().anyMatch(row -> row.section == section
                && row.label.isVisible());
        section.header.setVisible(visible);
        section.body.setVisible(visible);
    }

    private void applyRowVisibility(RowEntry entry) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        setRowVisible(entry, needle.isEmpty()
                || entry.property.label().toLowerCase(Locale.ROOT).contains(needle)
                || entry.property.key().toLowerCase(Locale.ROOT).contains(needle));
    }

    private void setRowVisible(RowEntry entry, boolean visible) {
        entry.label.setVisible(visible);
        entry.property.editor().setVisible(visible);
    }

    private void notifyChange(Property<?> property) {
        if (!silent && onValueChange != null) {
            onValueChange.accept(property);
        }
    }

    private void runSilently(Runnable action) {
        boolean previous = silent;
        silent = true;
        try {
            action.run();
        } finally {
            silent = previous;
        }
    }

    private boolean isSilent() {
        return silent;
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Property key must not be blank.");
        }
        return key;
    }

    private record Section(Label header, Grid body) {
    }

    private record RowEntry(Property<?> property, Label label, Section section) {
    }

    /**
     * Scrolls a content widget that always fills the viewport width and keeps its natural height.
     */
    private static final class FittingScrollPanel extends ScrollPanel {
        /**
         * Sizes the content to the viewport width and its measured height before scrolling.
         */
        @Override
        protected void onLayout() {
            Widget content = content();
            if (content != null) {
                float width = Math.max(0, bounds().width() - contentInsetLeft()
                        - contentInsetRight());
                Size size = content.measure(LayoutConstraints.loose(Math.max(1, width),
                        Float.MAX_VALUE));
                content.layout(new Rect(0, 0, Math.max(width, size.width()), size.height()));
            }
            super.onLayout();
        }
    }

    /**
     * A property shown in a grid row.
     * Subclasses store a typed value and create the matching editor.
     *
     * @param <T> value type exposed by the property
     */
    public abstract static class Property<T> {
        private final PropertyGrid grid;
        private final String key;
        private final String label;
        private final String section;
        private T defaultValue;
        private Widget editor;

        private Property(PropertyGrid grid, String key, String label, String section,
                         T defaultValue) {
            this.grid = grid;
            this.key = key;
            this.label = Objects.requireNonNull(label, "label");
            this.section = requireSection(section);
            this.defaultValue = defaultValue;
        }

        /**
         * Returns the property key.
         *
         * @return the unique key
         */
        public final String key() {
            return key;
        }

        /**
         * Returns the row label.
         *
         * @return the label text
         */
        public final String label() {
            return label;
        }

        /**
         * Returns the section title.
         *
         * @return the section title
         */
        public final String section() {
            return section;
        }

        /**
         * Returns the current value.
         *
         * @return the typed value
         */
        public abstract T value();

        /**
         * Returns the default value restored by {@link PropertyGrid#reset()}.
         *
         * @return the initial value
         */
        public final T defaultValue() {
            return defaultValue;
        }

        /**
         * Returns the editor widget shown in the row.
         *
         * @return the editor, or null before the grid registers the property
         */
        public final Widget editor() {
            return editor;
        }

        /**
         * Attaches the editor widget created for this property.
         *
         * @param editor editor shown in the grid row
         */
        protected final void bindEditor(Widget editor) {
            this.editor = editor;
        }

        /**
         * Stores the default value restored by {@link PropertyGrid#reset()}.
         * Number editors use it to keep the default consistent with their normalized value.
         *
         * @param defaultValue normalized default value
         */
        protected final void setDefaultValue(T defaultValue) {
            this.defaultValue = defaultValue;
        }

        /**
         * Creates the editor for this property type.
         *
         * @return a new editor widget
         */
        protected abstract Widget createEditor();

        /**
         * Applies the stored value to the editor without notifying listeners.
         */
        protected abstract void applyValueToEditor();

        /**
         * Restores the default value and updates the editor.
         *
         * @return true when the value changed
         */
        protected abstract boolean resetValue();

        /**
         * Runs an editor update without reporting the resulting editor callbacks.
         *
         * @param action update to run
         */
        protected final void runSilently(Runnable action) {
            grid.runSilently(action);
        }

        /**
         * Reports whether editor callbacks are currently suppressed.
         *
         * @return true while a silent update is in progress
         */
        protected final boolean isSilent() {
            return grid.isSilent();
        }

        /**
         * Reports the property as changed through the grid callback.
         */
        protected final void notifyChanged() {
            grid.notifyChange(this);
        }
    }

    /**
     * A boolean property backed by a check box.
     */
    public static final class BooleanProperty extends Property<Boolean> {
        private boolean value;

        private BooleanProperty(PropertyGrid grid, String key, String label, String section,
                                boolean initial) {
            super(grid, key, label, section, initial);
            value = initial;
        }

        /**
         * Returns whether the option is checked.
         *
         * @return the current value
         */
        @Override
        public Boolean value() {
            return value;
        }

        /**
         * Reports whether the property is checked.
         *
         * @return the current value
         */
        public boolean isChecked() {
            return value;
        }

        /**
         * Replaces the value and updates the check box.
         *
         * @param checked new value
         */
        public void setChecked(boolean checked) {
            if (value == checked) {
                return;
            }
            value = checked;
            runSilently(this::applyValueToEditor);
            notifyChanged();
        }

        /**
         * Creates the check box editor.
         *
         * @return a configured check box
         */
        @Override
        protected Widget createEditor() {
            CheckBox checkBox = new CheckBox();
            checkBox.setChecked(value);
            checkBox.setOnChange(checked -> {
                if (isSilent()) {
                    return;
                }
                value = checked;
                notifyChanged();
            });
            return checkBox;
        }

        /**
         * Applies the stored value to the check box.
         */
        @Override
        protected void applyValueToEditor() {
            if (editor() != null) {
                ((CheckBox) editor()).setChecked(value);
            }
        }

        /**
         * Restores the default value.
         *
         * @return true when the value changed
         */
        @Override
        protected boolean resetValue() {
            if (value == defaultValue()) {
                return false;
            }
            value = defaultValue();
            applyValueToEditor();
            return true;
        }
    }

    /**
     * A text property backed by a single-line input.
     */
    public static final class TextProperty extends Property<String> {
        private String value;

        private TextProperty(PropertyGrid grid, String key, String label, String section,
                             String initial) {
            super(grid, key, label, section, initial);
            value = initial;
        }

        /**
         * Returns the current text.
         *
         * @return the stored text
         */
        @Override
        public String value() {
            return value;
        }

        /**
         * Replaces the text and updates the input.
         *
         * @param text new text
         * @throws NullPointerException if {@code text} is null
         */
        public void setText(String text) {
            Objects.requireNonNull(text, "text");
            if (value.equals(text)) {
                return;
            }
            value = text;
            runSilently(this::applyValueToEditor);
            notifyChanged();
        }

        /**
         * Creates the text editor.
         *
         * @return a configured input
         */
        @Override
        protected Widget createEditor() {
            Input input = new Input(value);
            input.setOnChange(text -> {
                if (isSilent()) {
                    return;
                }
                value = text;
                notifyChanged();
            });
            return input;
        }

        /**
         * Applies the stored text to the input.
         */
        @Override
        protected void applyValueToEditor() {
            if (editor() != null) {
                ((Input) editor()).setText(value);
            }
        }

        /**
         * Restores the default text.
         *
         * @return true when the value changed
         */
        @Override
        protected boolean resetValue() {
            if (value.equals(defaultValue())) {
                return false;
            }
            value = defaultValue();
            applyValueToEditor();
            return true;
        }
    }

    /**
     * A number property backed by a numeric stepper.
     */
    public static final class NumberProperty extends Property<Double> {
        private double value;
        private double minimum;
        private double maximum;
        private double step;
        private int decimals;

        private NumberProperty(PropertyGrid grid, String key, String label, String section,
                               double initial, double minimum, double maximum, double step,
                               int decimals) {
            super(grid, key, label, section,
                    validatedInitial(initial, minimum, maximum, step, decimals));
            this.minimum = minimum;
            this.maximum = maximum;
            this.step = step;
            this.decimals = decimals;
            this.value = defaultValue();
        }

        private static double validatedInitial(double initial, double minimum, double maximum,
                                               double step, int decimals) {
            if (!Double.isFinite(initial) || !Double.isFinite(minimum)
                    || !Double.isFinite(maximum) || !Double.isFinite(step)) {
                throw new IllegalArgumentException("Number property values must be finite.");
            }
            if (minimum > maximum) {
                throw new IllegalArgumentException("Minimum must not exceed the maximum.");
            }
            if (step <= 0) {
                throw new IllegalArgumentException("Step must be positive.");
            }
            if (decimals < 0 || decimals > 9) {
                throw new IllegalArgumentException("Decimals must be between zero and nine.");
            }
            return initial;
        }

        /**
         * Returns the current number.
         *
         * @return the stored value
         */
        @Override
        public Double value() {
            return value;
        }

        /**
         * Replaces the number and updates the stepper.
         *
         * @param number new value
         * @throws IllegalArgumentException if the value is non-finite
         */
        public void setValue(double number) {
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("Number property value must be finite.");
            }
            if (editor() == null) {
                value = number;
                return;
            }
            NumberStepper stepper = (NumberStepper) editor();
            double previous = value;
            runSilently(() -> stepper.setValue(number));
            value = stepper.value();
            if (Double.compare(previous, value) != 0) {
                notifyChanged();
            }
        }

        /**
         * Returns the inclusive lower bound.
         *
         * @return the minimum
         */
        public double minimum() {
            return minimum;
        }

        /**
         * Returns the inclusive upper bound.
         *
         * @return the maximum
         */
        public double maximum() {
            return maximum;
        }

        /**
         * Returns the increment used by the stepper.
         *
         * @return the positive step
         */
        public double step() {
            return step;
        }

        /**
         * Returns the decimal place count.
         *
         * @return the decimal count
         */
        public int decimals() {
            return decimals;
        }

        /**
         * Replaces the range and clamps the current value into it.
         *
         * @param minimum inclusive lower bound
         * @param maximum inclusive upper bound
         * @throws IllegalArgumentException if a bound is non-finite or the minimum exceeds it
         */
        public void setRange(double minimum, double maximum) {
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
                throw new IllegalArgumentException("Number property range is invalid.");
            }
            this.minimum = minimum;
            this.maximum = maximum;
            double previous = value;
            if (editor() != null) {
                NumberStepper stepper = (NumberStepper) editor();
                runSilently(() -> stepper.setRange(minimum, maximum));
                value = stepper.value();
            }
            if (Double.compare(previous, value) != 0) {
                notifyChanged();
            }
        }

        /**
         * Creates the numeric stepper editor.
         *
         * @return a configured stepper
         */
        @Override
        protected Widget createEditor() {
            NumberStepper stepper = new NumberStepper();
            stepper.setRange(minimum, maximum);
            stepper.setStep(step);
            stepper.setDecimals(decimals);
            stepper.setValue(value);
            value = stepper.value();
            setDefaultValue(value);
            stepper.setOnValueChange(number -> {
                if (isSilent()) {
                    return;
                }
                value = number;
                notifyChanged();
            });
            return stepper;
        }

        /**
         * Applies the stored value to the stepper.
         */
        @Override
        protected void applyValueToEditor() {
            if (editor() != null) {
                ((NumberStepper) editor()).setValue(value);
            }
        }

        /**
         * Restores the default number.
         *
         * @return true when the value changed
         */
        @Override
        protected boolean resetValue() {
            if (editor() == null) {
                if (Double.compare(value, defaultValue()) == 0) {
                    return false;
                }
                value = defaultValue();
                return true;
            }
            NumberStepper stepper = (NumberStepper) editor();
            double previous = value;
            stepper.setValue(defaultValue());
            value = stepper.value();
            return Double.compare(previous, value) != 0;
        }
    }

    /**
     * A choice property backed by a dropdown.
     */
    public static final class SelectProperty extends Property<String> {
        private final List<String> options;
        private int selectedIndex;

        private SelectProperty(PropertyGrid grid, String key, String label, String section,
                               List<String> options, int selectedIndex) {
            super(grid, key, label, section, defaultValueFor(options, selectedIndex));
            this.options = List.copyOf(options);
            this.selectedIndex = selectedIndex;
        }

        private static String defaultValueFor(List<String> options, int selectedIndex) {
            Objects.requireNonNull(options, "options");
            if (options.isEmpty()) {
                throw new IllegalArgumentException("A select property needs at least one option.");
            }
            for (String option : options) {
                Objects.requireNonNull(option, "option");
            }
            if (selectedIndex < -1 || selectedIndex >= options.size()) {
                throw new IllegalArgumentException("Selected index is out of range.");
            }
            return selectedIndex < 0 ? null : options.get(selectedIndex);
        }

        /**
         * Returns the selected option label.
         *
         * @return the selected label, or null when nothing is selected
         */
        @Override
        public String value() {
            return selectedIndex < 0 ? null : options.get(selectedIndex);
        }

        /**
         * Returns the option labels.
         *
         * @return an immutable list of options
         */
        public List<String> options() {
            return options;
        }

        /**
         * Returns the selected option index.
         *
         * @return the zero-based index, or -1 when nothing is selected
         */
        public int selectedIndex() {
            return selectedIndex;
        }

        /**
         * Selects an option and updates the dropdown.
         *
         * @param index zero-based option index, or -1 to clear the selection
         * @throws IllegalArgumentException if the index is out of range
         */
        public void setSelectedIndex(int index) {
            if (index < -1 || index >= options.size()) {
                throw new IllegalArgumentException("Selected index is out of range.");
            }
            if (selectedIndex == index) {
                return;
            }
            selectedIndex = index;
            runSilently(this::applyValueToEditor);
            notifyChanged();
        }

        /**
         * Creates the dropdown editor.
         *
         * @return a configured dropdown
         */
        @Override
        protected Widget createEditor() {
            Dropdown dropdown = new Dropdown();
            for (String option : options) {
                dropdown.addOption(option);
            }
            if (selectedIndex >= 0) {
                dropdown.setSelectedIndex(selectedIndex);
            }
            dropdown.setOnChange(option -> {
                if (isSilent()) {
                    return;
                }
                selectedIndex = dropdown.selectedIndex();
                notifyChanged();
            });
            return dropdown;
        }

        /**
         * Applies the selected index to the dropdown.
         */
        @Override
        protected void applyValueToEditor() {
            if (editor() == null) {
                return;
            }
            Dropdown dropdown = (Dropdown) editor();
            if (selectedIndex < 0) {
                dropdown.clearSelection();
            } else {
                dropdown.setSelectedIndex(selectedIndex);
            }
        }

        /**
         * Restores the default selection.
         *
         * @return true when the selection changed
         */
        @Override
        protected boolean resetValue() {
            int index = defaultValue() == null ? -1 : options.indexOf(defaultValue());
            if (selectedIndex == index) {
                return false;
            }
            selectedIndex = index;
            applyValueToEditor();
            return true;
        }
    }

    private static String requireSection(String section) {
        Objects.requireNonNull(section, "section");
        if (section.isBlank()) {
            throw new IllegalArgumentException("Section title must not be blank.");
        }
        return section;
    }
}
