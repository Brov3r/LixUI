package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.WidgetState;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A single-choice select field with an editable filter and a scrollable option popup.
 * The closed field shows the selected option label, a custom value or a placeholder. Opening the
 * popup focuses a filter field above the option list; typing narrows the rows, the arrow keys move
 * the highlight, Enter selects it and Escape dismisses the popup without changing the value. When
 * custom values are allowed, Enter on text without a highlighted row commits the typed text as the
 * field value. A click on a row selects it as well.
 */
public class Combobox extends FilterableSelect {
    private static final float ICON_GAP = 6;

    private String customValue;
    private boolean allowCustom;
    private int selectedIndex = -1;
    private Consumer<SelectOption> onChange;

    /**
     * Creates an empty combobox with the default placeholder.
     */
    public Combobox() {
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
    public final Combobox addOption(SelectOption option) {
        addOptionInternal(option);
        return this;
    }

    /**
     * Returns the selected option index.
     *
     * @return the zero-based index, or -1 when nothing is selected or a custom value is active
     */
    public final int selectedIndex() {
        return selectedIndex;
    }

    /**
     * Returns the selected option.
     *
     * @return the selected option, or null when nothing is selected or a custom value is active
     */
    public final SelectOption selectedOption() {
        return optionAt(selectedIndex);
    }

    /**
     * Returns the current value.
     *
     * @return the selected option value, the custom text, or null when nothing is selected
     */
    public final Object value() {
        SelectOption option = selectedOption();
        return option == null ? customValue : option.value();
    }

    /**
     * Returns the label shown by the closed field.
     *
     * @return the selected option label, the custom text, or an empty string when nothing is selected
     */
    public final String text() {
        SelectOption option = selectedOption();
        if (option != null) {
            return option.label();
        }
        return customValue == null ? "" : customValue;
    }

    /**
     * Selects an option index.
     * Selecting an already selected option does not notify the listener.
     *
     * @param index zero-based option index, or -1 to clear the selection
     * @throws IllegalArgumentException if the index is out of range or the option is disabled
     */
    public final void setSelectedIndex(int index) {
        if (index < -1 || index >= optionCount()) {
            throw new IllegalArgumentException("Option index is out of range.");
        }
        if (index >= 0 && optionAt(index).isDisabled()) {
            throw new IllegalArgumentException("Option is disabled.");
        }
        if (selectedIndex == index && customValue == null) {
            return;
        }
        selectedIndex = index;
        customValue = null;
        notifyChange();
    }

    /**
     * Selects the option with the supplied value or assigns a custom value.
     * A value that matches no option is accepted as custom text only while custom values are
     * allowed. Passing null clears the selection.
     *
     * @param value option value or custom text, or null to clear the selection
     * @throws IllegalArgumentException if no option matches and custom values are disabled, or if
     *                                  the matching option is disabled
     */
    public final void setValue(Object value) {
        if (value == null) {
            if (selectedIndex == -1 && customValue == null) {
                return;
            }
            selectedIndex = -1;
            customValue = null;
            notifyChange();
            return;
        }
        for (int index = 0; index < optionCount(); index++) {
            SelectOption option = optionAt(index);
            if (Objects.equals(option.value(), value)) {
                if (option.isDisabled()) {
                    throw new IllegalArgumentException("Option is disabled.");
                }
                setSelectedIndex(index);
                return;
            }
        }
        if (!allowCustom) {
            throw new IllegalArgumentException("Value does not match any option.");
        }
        String text = String.valueOf(value);
        if (selectedIndex == -1 && text.equals(customValue)) {
            return;
        }
        selectedIndex = -1;
        customValue = text;
        notifyChange();
    }

    /**
     * Reports whether Enter may commit typed text as a custom value.
     *
     * @return true when custom values are accepted
     */
    public final boolean isAllowCustom() {
        return allowCustom;
    }

    /**
     * Enables or disables custom text values.
     * Disabling custom values clears an active custom value.
     *
     * @param allowCustom true to accept typed text that matches no option
     */
    public final void setAllowCustom(boolean allowCustom) {
        this.allowCustom = allowCustom;
        if (!allowCustom && customValue != null) {
            customValue = null;
            notifyChange();
        }
    }

    /**
     * Returns the selection change callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<SelectOption> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked after the value changes.
     * The callback receives the selected option, or null for a custom or cleared value.
     *
     * @param onChange callback receiving the selected option, or null to clear it
     */
    public final void setOnChange(Consumer<SelectOption> onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the selected label, custom text or placeholder and the selected icon.
     *
     * @param font resolved field font, or null when no font is available
     * @return the content size without padding and the arrow
     */
    @Override
    protected Size onFieldContentSize(Font font) {
        if (font == null) {
            return new Size(0, 0);
        }
        String text = text();
        if (text.isEmpty()) {
            text = placeholder();
        }
        Size size = font.measure(text);
        SelectOption option = selectedOption();
        if (option != null && option.icon() != null) {
            size = new Size(size.width() + iconSize() + ICON_GAP,
                    Math.max(size.height(), iconSize()));
        }
        return size;
    }

    /**
     * Paints the selected option icon and label, the custom text or the placeholder.
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
        String text = text();
        SelectOption option = selectedOption();
        Color textColor = text.isEmpty() ? mutedColor(color) : color;
        String label = text.isEmpty() ? placeholder() : text;
        float x = content.x();
        if (option != null && option.icon() != null) {
            renderer.icon(option.icon(),
                    new Rect(x, content.y() + (content.height() - iconSize()) / 2,
                            iconSize(), iconSize()), ImageStyle.ICON, color);
            x += iconSize() + ICON_GAP;
        }
        float width = Math.max(0, content.right() - x);
        if (font != null && width > 0) {
            renderer.text(font, label, x, content.y() + content.height() / 2, textColor,
                    new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
    }

    /**
     * Reports whether the option index is selected.
     *
     * @param index zero-based option index
     * @return true when the option is selected
     */
    @Override
    protected boolean isOptionSelected(int index) {
        return selectedIndex == index;
    }

    /**
     * Stores the activated option and notifies the selection listener.
     *
     * @param index zero-based option index
     */
    @Override
    protected void activateOption(int index) {
        boolean changed = selectedIndex != index || customValue != null;
        selectedIndex = index;
        customValue = null;
        if (changed) {
            notifyChange();
        }
    }

    /**
     * Reports whether activating an option closes the popup.
     *
     * @return true for a single-choice combobox
     */
    @Override
    protected boolean closesOnActivate() {
        return true;
    }

    /**
     * Keeps the selected option when it is still present and enabled.
     *
     * @param selectedOptions options that were selected before the change
     */
    @Override
    protected void remapSelection(List<SelectOption> selectedOptions) {
        if (selectedOptions.size() != 1) {
            selectedIndex = -1;
            return;
        }
        int index = optionIndexOf(selectedOptions.get(0));
        selectedIndex = index >= 0 && !optionAt(index).isDisabled() ? index : -1;
    }

    /**
     * Reports whether the popup may open without options.
     *
     * @return true while custom values are allowed
     */
    @Override
    protected boolean canOpenWithNoOptions() {
        return allowCustom;
    }

    /**
     * Accepts non-empty typed text while custom values are allowed.
     *
     * @param text stripped filter text
     * @return true when the text may become the custom value
     */
    @Override
    protected boolean acceptsFreeText(String text) {
        return allowCustom && !text.isEmpty();
    }

    /**
     * Stores typed text as the custom value.
     *
     * @param text stripped filter text
     */
    @Override
    protected void commitFreeText(String text) {
        if (selectedIndex == -1 && text.equals(customValue)) {
            return;
        }
        selectedIndex = -1;
        customValue = text;
        notifyChange();
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(selectedOption());
        }
    }
}
