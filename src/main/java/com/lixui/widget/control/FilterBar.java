package com.lixui.widget.control;

import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.HBox;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A horizontal filter bar with a debounced search field and one dropdown per filter.
 * The bar exposes the current query, the selected value of every filter and the number of active
 * filters. Selecting or clearing a dropdown updates the state immediately, while the query is
 * reported after the search debounce delay or on Enter.
 * <p>
 * Filter keys are unique and define the order of the dropdowns and of the values map.
 */
public class FilterBar extends Widget {
    private static final float DEFAULT_GAP = 8;
    private static final float DEFAULT_FILTER_WIDTH = 140;

    private final HBox row = new HBox();
    private final SearchInput search = new SearchInput();
    private final Map<String, Dropdown> filters = new LinkedHashMap<>();
    private Consumer<State> onChange;
    private String query = "";
    private boolean silent;

    /**
     * Creates an empty filter bar with a search field.
     */
    public FilterBar() {
        row.setGap(DEFAULT_GAP);
        row.setCrossAlignment(HBox.CrossAlignment.CENTER);
        search.setGrow(1);
        search.setOnSearch(this::updateQuery);
        row.add(search);
        add(row);
    }

    /**
     * Appends a dropdown filter.
     *
     * @param key unique filter key used in the state map
     * @param placeholder text shown while no option is selected
     * @param options option labels
     * @return the created dropdown for further customization
     * @throws NullPointerException if an argument or option is null
     * @throws IllegalArgumentException if the key is blank, already registered, or no option is given
     */
    public final Dropdown addFilter(String key, String placeholder, String... options) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("Filter key must not be blank.");
        }
        if (filters.containsKey(key)) {
            throw new IllegalArgumentException("Filter key is already registered: " + key);
        }
        if (options.length == 0) {
            throw new IllegalArgumentException("A filter needs at least one option.");
        }
        Dropdown dropdown = new Dropdown();
        dropdown.setPlaceholder(Objects.requireNonNull(placeholder, "placeholder"));
        dropdown.setMinimumSize(DEFAULT_FILTER_WIDTH, 0);
        for (String option : options) {
            dropdown.addOption(Objects.requireNonNull(option, "option"));
        }
        dropdown.setChangeObserver(ignored -> {
            if (!silent) {
                notifyChange();
            }
        });
        filters.put(key, dropdown);
        row.add(dropdown);
        return dropdown;
    }

    /**
     * Returns the current debounced query.
     *
     * @return the query, or an empty string when the search field is empty
     */
    public final String query() {
        return query;
    }

    /**
     * Replaces the query and the search field text.
     * The callback runs immediately when the query changes; the pending debounced search does not
     * report the same query twice.
     *
     * @param query new query
     * @throws NullPointerException if {@code query} is null
     */
    public final void setQuery(String query) {
        updateQuery(Objects.requireNonNull(query, "query"));
        search.setText(query);
    }

    /**
     * Returns the selected value of a filter.
     *
     * @param key filter key
     * @return the selected label, or null when the filter is not active
     * @throws IllegalArgumentException if the key is not registered
     */
    public final String value(String key) {
        return dropdown(key).selectedOption() == null
                ? null : dropdown(key).selectedOption().label();
    }

    /**
     * Selects a filter value.
     *
     * @param key filter key
     * @param value option label, or null to clear the filter
     * @throws IllegalArgumentException if the key is not registered or the value has no option
     */
    public final void setValue(String key, String value) {
        Dropdown dropdown = dropdown(key);
        if (value == null) {
            dropdown.clearSelection();
            return;
        }
        for (int index = 0; index < dropdown.optionCount(); index++) {
            if (dropdown.optionAt(index).label().equals(value)) {
                dropdown.setSelectedIndex(index);
                return;
            }
        }
        throw new IllegalArgumentException("Filter has no option with the value: " + value);
    }

    /**
     * Returns the active filter values in registration order.
     *
     * @return an immutable map from filter key to selected label
     */
    public final Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, Dropdown> entry : filters.entrySet()) {
            SelectOption option = entry.getValue().selectedOption();
            if (option != null) {
                values.put(entry.getKey(), option.label());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Returns the number of filters with a selected value.
     * The query is not counted as a filter.
     *
     * @return the active filter count
     */
    public final int activeFilterCount() {
        int count = 0;
        for (Dropdown dropdown : filters.values()) {
            if (dropdown.selectedOption() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a snapshot of the current query, values and active filter count.
     *
     * @return the immutable filter state
     */
    public final State filterState() {
        return new State(query, values(), activeFilterCount());
    }

    /**
     * Clears the query and every filter selection.
     * The callback runs once when something actually changed.
     */
    public final void clear() {
        boolean changed = !query.isEmpty() || activeFilterCount() > 0;
        silent = true;
        try {
            query = "";
            search.setText("");
            for (Dropdown dropdown : filters.values()) {
                dropdown.clearSelection();
            }
        } finally {
            silent = false;
        }
        if (changed) {
            notifyChange();
        }
    }

    /**
     * Returns the delay between the last search text change and the query update.
     *
     * @return the debounce delay in seconds
     */
    public final float debounceSeconds() {
        return search.debounceSeconds();
    }

    /**
     * Sets the delay between the last search text change and the query update.
     *
     * @param debounceSeconds non-negative delay in seconds
     * @throws IllegalArgumentException if the delay is negative or non-finite
     */
    public final void setDebounceSeconds(float debounceSeconds) {
        search.setDebounceSeconds(debounceSeconds);
    }

    /**
     * Returns the callback invoked when the query or a filter value changes.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<State> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked with an immutable state snapshot after a change.
     *
     * @param onChange callback, or null to clear it
     */
    public final void setOnChange(Consumer<State> onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the search field and the filter dropdowns.
     *
     * @param constraints available and required dimensions
     * @return the natural bar size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return row.measure(constraints);
    }

    /**
     * Places the search field and the filter dropdowns across the bar.
     */
    @Override
    protected void onLayout() {
        row.layout(localBounds());
    }

    private void updateQuery(String next) {
        if (query.equals(next) || silent) {
            return;
        }
        query = next;
        notifyChange();
    }

    private Dropdown dropdown(String key) {
        Dropdown dropdown = filters.get(key);
        if (dropdown == null) {
            throw new IllegalArgumentException("Unknown filter key: " + key);
        }
        return dropdown;
    }

    private void notifyChange() {
        if (onChange != null && !silent) {
            onChange.accept(filterState());
        }
    }

    /**
     * An immutable filter bar state.
     *
     * @param query normalized query text
     * @param values selected filter values keyed by filter key
     * @param activeFilters number of filters with a selected value
     */
    public record State(String query, Map<String, String> values, int activeFilters) {
        /**
         * Creates a state snapshot.
         *
         * @param query normalized query text
         * @param values selected filter values
         * @param activeFilters number of active filters
         * @throws NullPointerException if {@code query} or {@code values} is null
         * @throws IllegalArgumentException if the active count is negative
         */
        public State {
            Objects.requireNonNull(query, "query");
            Objects.requireNonNull(values, "values");
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            if (activeFilters < 0) {
                throw new IllegalArgumentException("Active filter count must be non-negative.");
            }
        }
    }
}
