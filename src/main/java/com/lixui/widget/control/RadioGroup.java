package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.layout.LayoutContainer;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A vertical group of radio buttons with a single selection.
 * All options share one exclusive {@link ToggleGroup}. Clicking an option selects and focuses it,
 * while the arrow keys move the selection and focus to the next or previous visible, enabled option.
 * Disabled and hidden options are skipped and disabled options cannot be selected by the user. The
 * change callback receives the zero-based index of the newly selected option and runs once per
 * selection change.
 */
public class RadioGroup extends LayoutContainer {
    private static final float DEFAULT_GAP = 4;

    private final VBox content = new VBox();
    private final ToggleGroup group = new ToggleGroup();
    private final List<RadioBox> options = new ArrayList<>();
    private IntConsumer onChange;

    /**
     * Creates an empty radio group.
     */
    public RadioGroup() {
        group.setExclusive(true);
        content.setGap(DEFAULT_GAP);
        add(content);
    }

    /**
     * Appends an option with the supplied label.
     *
     * @param text option label
     * @return the added radio button for further configuration
     * @throws NullPointerException if {@code text} is null
     */
    public final RadioBox addOption(String text) {
        RadioBox option = new RadioBox(text);
        option.setGroup(group);
        option.setChangeObserver(checked -> {
            if (checked) {
                notifyChange(option);
            }
        });
        options.add(option);
        content.add(option);
        return option;
    }

    /**
     * Returns the options in insertion order.
     *
     * @return an immutable snapshot of the options
     */
    public final List<RadioBox> options() {
        return List.copyOf(options);
    }

    /**
     * Returns the number of options.
     *
     * @return the option count
     */
    public final int optionCount() {
        return options.size();
    }

    /**
     * Returns the selected option index.
     *
     * @return the zero-based selected index, or -1 when no option is selected
     */
    public final int selectedIndex() {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).isChecked()) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Selects an enabled option by index.
     * Selecting the current option does nothing.
     *
     * @param index zero-based option index
     * @throws IllegalArgumentException if the index is out of range or the option is disabled
     */
    public final void setSelectedIndex(int index) {
        if (index < 0 || index >= options.size()) {
            throw new IllegalArgumentException("Option index is out of range.");
        }
        RadioBox option = options.get(index);
        if (!option.isEnabled()) {
            throw new IllegalArgumentException("Option is disabled.");
        }
        option.setChecked(true);
    }

    /**
     * Returns the space between adjacent options.
     *
     * @return the gap in logical UI units
     */
    public final float gap() {
        return content.gap();
    }

    /**
     * Sets the space between adjacent options.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public final void setGap(float gap) {
        content.setGap(gap);
    }

    /**
     * Returns the callback invoked when the selected option changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public final IntConsumer onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked once with the zero-based index of the newly selected option.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the stacked options plus padding.
     *
     * @param constraints available and required dimensions
     * @return the natural group size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Size contentSize = content.measure(LayoutConstraints.loose(
                availableWidth(constraints), availableHeight(constraints)));
        return new Size(contentSize.width() + padding() * 2,
                contentSize.height() + padding() * 2);
    }

    /**
     * Stretches the option column over the padded group bounds.
     */
    @Override
    protected void onLayout() {
        content.layout(new Rect(padding(), padding(), contentWidth(), contentHeight()));
    }

    /**
     * Moves the selection and focus to the next or previous enabled option.
     * Up and left select the previous option; down and right select the next one. The search wraps
     * around the list and skips disabled and hidden options.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when an arrow key was handled
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        int direction = switch (key) {
            case UP, LEFT -> -1;
            case DOWN, RIGHT -> 1;
            default -> 0;
        };
        if (direction == 0) {
            return false;
        }
        int current = focusedIndex();
        if (current < 0) {
            current = selectedIndex();
        }
        if (current < 0) {
            current = direction > 0 ? -1 : options.size();
        }
        for (int step = 1; step <= options.size(); step++) {
            int candidate = Math.floorMod(current + direction * step, options.size());
            RadioBox option = options.get(candidate);
            if (!option.isVisible() || !option.isEnabled()) {
                continue;
            }
            if (context() != null) {
                context().requestFocus(option);
            }
            option.setChecked(true);
            return true;
        }
        return true;
    }

    private int focusedIndex() {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index).isFocused()) {
                return index;
            }
        }
        return -1;
    }

    private void notifyChange(RadioBox option) {
        int index = options.indexOf(option);
        if (index >= 0 && onChange != null) {
            onChange.accept(index);
        }
    }
}
