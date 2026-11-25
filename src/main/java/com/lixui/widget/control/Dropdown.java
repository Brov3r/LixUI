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
 * A single-choice select field that opens a scrollable option popup.
 * The field shows the selected option or a placeholder and closes the popup after a selection.
 */
public class Dropdown extends PopupSelect {
    private static final float ICON_GAP = 6;

    private int selectedIndex = -1;
    private String placeholder = "Select...";
    private Consumer<SelectOption> onChange;

    /**
     * Creates an empty dropdown with the default placeholder.
     */
    public Dropdown() {
    }

    /**
     * Returns the selected option index.
     *
     * @return the zero-based index, or -1 when nothing is selected
     */
    public final int selectedIndex() {
        return selectedIndex;
    }

    /**
     * Returns the selected option.
     *
     * @return the selected option, or null when nothing is selected
     */
    public final SelectOption selectedOption() {
        return optionAt(selectedIndex);
    }

    /**
     * Returns the selected option value.
     *
     * @return the selected value, or null when nothing is selected
     */
    public final Object selectedValue() {
        SelectOption option = selectedOption();
        return option == null ? null : option.value();
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
        if (selectedIndex == index) {
            return;
        }
        selectedIndex = index;
        notifyChange();
    }

    /**
     * Clears the selection.
     */
    public final void clearSelection() {
        if (selectedIndex == -1) {
            return;
        }
        selectedIndex = -1;
        notifyChange();
    }

    /**
     * Returns the text shown when nothing is selected.
     *
     * @return the placeholder text
     */
    public final String placeholder() {
        return placeholder;
    }

    /**
     * Sets the text shown when nothing is selected.
     *
     * @param placeholder placeholder text
     * @throws NullPointerException if {@code placeholder} is null
     */
    public final void setPlaceholder(String placeholder) {
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
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
     * Assigns a callback invoked after the selection changes.
     *
     * @param onChange callback receiving the selected option, or null when cleared
     */
    public final void setOnChange(Consumer<SelectOption> onChange) {
        this.onChange = onChange;
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(selectedOption());
        }
    }

    /**
     * Measures the selected label or placeholder.
     *
     * @param font resolved field font, or null when no font is available
     * @return the content size without padding and the arrow
     */
    @Override
    protected Size fieldContentSize(Font font) {
        if (font == null) {
            return new Size(0, 0);
        }
        SelectOption option = selectedOption();
        String text = option == null ? placeholder : option.label();
        Size size = font.measure(text);
        if (option != null && option.icon() != null) {
            size = new Size(size.width() + iconSize() + ICON_GAP,
                    Math.max(size.height(), iconSize()));
        }
        return size;
    }

    /**
     * Paints the selected option icon and label, or the placeholder.
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
        SelectOption option = selectedOption();
        String text = option == null ? placeholder : option.label();
        Color textColor = option == null ? disabledTextColor(color) : color;
        float x = content.x();
        if (option != null && option.icon() != null) {
            renderer.icon(option.icon(), new Rect(x, content.y() + (content.height() - iconSize()) / 2,
                    iconSize(), iconSize()), ImageStyle.ICON, color);
            x += iconSize() + ICON_GAP;
        }
        float width = Math.max(0, content.right() - x);
        if (font != null && width > 0) {
            renderer.text(font, text, x, content.y() + content.height() / 2, textColor,
                    new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
    }

    private Color disabledTextColor(Color fallback) {
        return context() == null ? fallback : context().theme().textMuted();
    }

    /**
     * Stores the activated option.
     *
     * @param index zero-based option index
     */
    @Override
    protected void activate(int index) {
        selectedIndex = index;
        notifyChange();
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
     * Reports whether activating an option closes the popup.
     *
     * @return true for a single-choice dropdown
     */
    @Override
    protected boolean closesOnActivate() {
        return true;
    }

    /**
     * Returns the selected row to highlight when the popup opens.
     *
     * @return the selected index, or -1 when nothing is selected
     */
    @Override
    protected int initialHighlight() {
        return selectedIndex >= 0 ? selectedIndex : 0;
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
}
