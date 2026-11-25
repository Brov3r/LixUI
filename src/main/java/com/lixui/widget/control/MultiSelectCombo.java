package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A multi-choice select field with an editable filter that keeps its popup open while options are
 * toggled. The closed field shows the selected labels as chips; the popup rows show a check mark
 * and Enter toggles the highlighted row without closing the popup.
 */
public class MultiSelectCombo extends FilterableSelect {
    private final Set<Integer> selected = new LinkedHashSet<>();

    private BiConsumer<SelectOption, Boolean> onChange;

    /**
     * Creates an empty multi-select combobox.
     */
    public MultiSelectCombo() {
        setPlaceholder("None selected");
    }

    /**
     * Appends an option with a text label and value.
     *
     * @param label option label
     * @return the added option
     * @throws NullPointerException if {@code label} is null
     */
    public final SelectOption addOption(String label) {
        return addLabeledOption(label);
    }

    /**
     * Appends an existing option.
     *
     * @param option option to append
     * @return this combobox
     * @throws NullPointerException if {@code option} is null
     */
    public final MultiSelectCombo addOption(SelectOption option) {
        addOptionInternal(option);
        return this;
    }

    /**
     * Returns the selected option indices in ascending order.
     *
     * @return an immutable snapshot of selected indices
     */
    public final List<Integer> selectedIndices() {
        return selected.stream().sorted().toList();
    }

    /**
     * Returns the selected options in option order.
     *
     * @return an immutable snapshot of selected options
     */
    public final List<SelectOption> selectedOptions() {
        List<SelectOption> result = new ArrayList<>();
        for (int index : selectedIndices()) {
            SelectOption option = optionAt(index);
            if (option != null) {
                result.add(option);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the number of selected options.
     *
     * @return the selection count
     */
    public final int selectedCount() {
        return selected.size();
    }

    /**
     * Reports whether an option is selected.
     *
     * @param index zero-based option index
     * @return true when the option is selected
     */
    public final boolean isSelected(int index) {
        return selected.contains(index);
    }

    /**
     * Changes one option selection state.
     *
     * @param index zero-based option index
     * @param checked true to select the option, false to clear it
     * @throws IllegalArgumentException if the index is out of range or a disabled option is selected
     */
    public final void setSelected(int index, boolean checked) {
        if (index < 0 || index >= optionCount()) {
            throw new IllegalArgumentException("Option index is out of range.");
        }
        SelectOption option = optionAt(index);
        if (checked && option.isDisabled()) {
            throw new IllegalArgumentException("Option is disabled.");
        }
        boolean changed = checked ? selected.add(index) : selected.remove(index);
        if (changed) {
            notifyChange(option, checked);
        }
    }

    /**
     * Selects or clears every enabled option.
     *
     * @param checked true to select all enabled options, false to clear every option
     */
    public final void setAll(boolean checked) {
        for (int index = 0; index < optionCount(); index++) {
            SelectOption option = optionAt(index);
            if (checked && option.isDisabled()) {
                continue;
            }
            boolean changed = checked ? selected.add(index) : selected.remove(index);
            if (changed) {
                notifyChange(option, checked);
            }
        }
    }

    /**
     * Clears every selected option.
     */
    public final void clearSelection() {
        setAll(false);
    }

    /**
     * Returns the compact summary shown by the closed field.
     *
     * @return the first selected label with a remainder count, or the placeholder
     */
    public final String text() {
        return summary();
    }

    /**
     * Returns the selection callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final BiConsumer<SelectOption, Boolean> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked after one option changes.
     * The callback receives the option and its new checked state.
     *
     * @param onChange callback receiving the option and its new checked state, or null to clear it
     */
    public final void setOnChange(BiConsumer<SelectOption, Boolean> onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the placeholder, the selected chip strip or the compact summary used by {@link #text()}.
     *
     * @param font resolved field font, or null when no font is available
     * @return the content size without padding and the arrow
     */
    @Override
    protected Size onFieldContentSize(Font font) {
        ChipStrip strip = ChipStrip.of(selectedOptions());
        if (!strip.isEmpty()) {
            return strip.measure(font);
        }
        if (font == null) {
            return new Size(0, 0);
        }
        Size size = font.measure(summary());
        return new Size(size.width(), Math.max(size.height(), ChipStrip.CHIP_HEIGHT));
    }

    /**
     * Measures the placeholder, the selected chip strip or the compact summary within the
     * available content width.
     *
     * @param font resolved field font, or null when no font is available
     * @param availableWidth width available for the field content
     * @return the content size without padding and the arrow
     */
    @Override
    protected Size onFieldContentSize(Font font, float availableWidth) {
        ChipStrip strip = ChipStrip.of(selectedOptions());
        if (!strip.isEmpty()) {
            return strip.measure(font, availableWidth);
        }
        return onFieldContentSize(font);
    }

    /**
     * Paints the selected chip strip, the placeholder or the compact summary of disabled selections.
     *
     * @param renderer drawing manager for the current frame
     * @param content content rectangle inside the field padding and arrow slot
     * @param color resolved text color for the current state
     * @param state current widget state
     */
    @Override
    protected void drawFieldContent(RenderManager renderer, Rect content,
                                    Color color, WidgetState state) {
        Font font = font();
        ChipStrip strip = ChipStrip.of(selectedOptions());
        if (!strip.isEmpty()) {
            Theme theme = context().theme();
            Rect clip = new Rect(absolutePosition().x() + content.x(),
                    absolutePosition().y() + content.y(), content.width(), content.height());
            strip.paint(renderer, content, clip, font, theme, state == WidgetState.DISABLED);
            return;
        }
        String text = summary();
        if (font == null || text.isEmpty()) {
            return;
        }
        Color textColor = selected.isEmpty() ? mutedColor(color) : color;
        renderer.text(font, text, content.x(), content.y() + content.height() / 2, textColor,
                ellipsis(content.width()));
    }

    /**
     * Reports whether the option index is selected.
     *
     * @param index zero-based option index
     * @return true when the option is selected
     */
    @Override
    protected boolean isOptionSelected(int index) {
        return selected.contains(index);
    }

    /**
     * Toggles the activated option.
     *
     * @param index zero-based option index
     */
    @Override
    protected void activateOption(int index) {
        setSelected(index, !isSelected(index));
    }

    /**
     * Reports whether activating an option closes the popup.
     *
     * @return false so several options can be toggled in one session
     */
    @Override
    protected boolean closesOnActivate() {
        return false;
    }

    /**
     * Keeps every selected option that is still present and enabled.
     *
     * @param selectedOptions options that were selected before the change
     */
    @Override
    protected void remapSelection(List<SelectOption> selectedOptions) {
        List<Integer> remapped = new ArrayList<>();
        for (SelectOption option : selectedOptions) {
            int index = optionIndexOf(option);
            if (index >= 0 && !optionAt(index).isDisabled()) {
                remapped.add(index);
            }
        }
        selected.clear();
        selected.addAll(remapped);
    }

    /**
     * Rejects typed filter text as a value.
     *
     * @param text stripped filter text
     * @return false
     */
    @Override
    protected boolean acceptsFreeText(String text) {
        return false;
    }

    /**
     * Ignores free text because the field only accepts options.
     *
     * @param text stripped filter text
     */
    @Override
    protected void commitFreeText(String text) {
    }

    private String summary() {
        List<Integer> indices = selectedIndices();
        if (indices.isEmpty()) {
            return placeholder();
        }
        SelectOption first = optionAt(indices.get(0));
        String label = first == null ? placeholder() : first.label();
        return indices.size() > 1 ? label + " +" + (indices.size() - 1) : label;
    }

    private static TextStyle ellipsis(float width) {
        return new TextStyle(Math.max(0, width), 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    }

    private void notifyChange(SelectOption option, boolean checked) {
        if (onChange != null) {
            onChange.accept(option, checked);
        }
    }
}
