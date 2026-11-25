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
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A multi-choice select field that keeps its popup open while options are toggled.
 * Selected options are shown as chips in the closed field; the popup rows show a check mark.
 */
public class MultiSelect extends PopupSelect {
    private static final float CHIP_HEIGHT = 20;
    private static final float CHIP_PADDING = 9;
    private static final float CHIP_GAP = 4;

    private final Set<Integer> selected = new LinkedHashSet<>();
    private String placeholder = "None selected";
    private BiConsumer<SelectOption, Boolean> onChange;

    /**
     * Creates an empty multi-select field.
     */
    public MultiSelect() {
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
     * Returns the selection callback.
     *
     * @return the callback, or null when none is assigned
     */
    public final BiConsumer<SelectOption, Boolean> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked after one option changes.
     *
     * @param onChange callback receiving the option and its new checked state
     */
    public final void setOnChange(BiConsumer<SelectOption, Boolean> onChange) {
        this.onChange = onChange;
    }

    private void notifyChange(SelectOption option, boolean checked) {
        if (onChange != null) {
            onChange.accept(option, checked);
        }
    }

    /**
     * Measures the placeholder or a compact summary of the selected chips.
     *
     * @param font resolved field font, or null when no font is available
     * @return the content size without padding and the arrow
     */
    @Override
    protected Size fieldContentSize(Font font) {
        if (font == null) {
            return new Size(0, 0);
        }
        List<Integer> indices = selectedIndices();
        String text = placeholder;
        if (!indices.isEmpty()) {
            SelectOption first = optionAt(indices.get(0));
            text = first == null ? placeholder : first.label();
            if (indices.size() > 1) {
                text += " +" + (indices.size() - 1);
            }
        }
        Size size = font.measure(text);
        return new Size(size.width(), Math.max(size.height(), CHIP_HEIGHT));
    }

    /**
     * Paints selected chips or the placeholder.
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
        List<Integer> indices = selectedIndices();
        if (indices.isEmpty()) {
            if (font != null && !placeholder.isEmpty()) {
                renderer.text(font, placeholder, content.x(), content.y() + content.height() / 2,
                        disabledColor(color), ellipsis(content.width()));
            }
            return;
        }
        Theme theme = context().theme();
        Color chipFill = Color.mix(theme.surface(), theme.primary(), 0.18f);
        Color chipText = state == WidgetState.DISABLED ? disabledColor(color) : theme.primary();
        float chipHeight = Math.min(CHIP_HEIGHT, content.height());
        float chipY = content.y() + (content.height() - chipHeight) / 2;
        List<Float> widths = chipWidths(indices, font);
        float totalWidth = 0;
        for (int index = 0; index < widths.size(); index++) {
            totalWidth += widths.get(index) + (index > 0 ? CHIP_GAP : 0);
        }
        if (totalWidth <= content.width()) {
            drawChips(renderer, indices, widths, font, chipY, chipHeight,
                    chipFill, chipText, content.x());
            return;
        }
        String moreText = "+" + indices.size();
        float moreWidth = font == null ? 0 : font.measure(moreText).width();
        float available = Math.max(0, content.width() - moreWidth - CHIP_GAP);
        float used = 0;
        int drawn = 0;
        for (int index = 0; index < indices.size(); index++) {
            float chipWidth = Math.min(content.width(), widths.get(index));
            float gap = drawn > 0 ? CHIP_GAP : 0;
            if (used + gap + chipWidth > available) {
                break;
            }
            SelectOption option = optionAt(indices.get(index));
            if (option != null) {
                drawChip(renderer, content.x() + used + gap, chipY, chipWidth, chipHeight,
                        option.label(), font, chipFill, chipText);
            }
            used += gap + chipWidth;
            drawn++;
        }
        int remaining = indices.size() - drawn;
        float moreX = content.x() + used + (drawn > 0 ? CHIP_GAP : 0);
        moreX = Math.max(content.x(), Math.min(moreX, content.right() - moreWidth));
        if (font != null && remaining > 0) {
            renderer.text(font, "+" + remaining, moreX, content.y() + content.height() / 2,
                    chipText, ellipsis(Math.max(0, content.right() - moreX)));
        }
    }

    private List<Float> chipWidths(List<Integer> indices, Font font) {
        List<Float> widths = new ArrayList<>();
        for (int index : indices) {
            SelectOption option = optionAt(index);
            float textWidth = font == null || option == null ? 0 : font.measure(option.label()).width();
            widths.add(textWidth + CHIP_PADDING * 2);
        }
        return widths;
    }

    private void drawChips(RenderManager renderer, List<Integer> indices,
                           List<Float> widths, Font font, float chipY, float chipHeight,
                           Color chipFill, Color chipText, float startX) {
        float chipX = startX;
        for (int index = 0; index < indices.size(); index++) {
            SelectOption option = optionAt(indices.get(index));
            if (option == null) {
                continue;
            }
            drawChip(renderer, chipX, chipY, widths.get(index), chipHeight,
                    option.label(), font, chipFill, chipText);
            chipX += widths.get(index) + CHIP_GAP;
        }
    }

    private void drawChip(RenderManager renderer, float x, float y, float width, float height,
                          String label, Font font, Color fill, Color textColor) {
        renderer.roundedRect(new Rect(x, y, width, height), height / 2, fill);
        if (font != null) {
            renderer.text(font, label, x + CHIP_PADDING, y + height / 2, textColor,
                    ellipsis(Math.max(0, width - CHIP_PADDING * 2)));
        }
    }

    private Color disabledColor(Color fallback) {
        return context() == null ? fallback : context().theme().textMuted();
    }

    private static TextStyle ellipsis(float width) {
        return new TextStyle(Math.max(0, width), 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    }

    /**
     * Toggles the activated option.
     *
     * @param index zero-based option index
     */
    @Override
    protected void activate(int index) {
        setSelected(index, !isSelected(index));
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
     * Reports whether activating an option closes the popup.
     *
     * @return false so several options can be toggled in one session
     */
    @Override
    protected boolean closesOnActivate() {
        return false;
    }

    /**
     * Returns the first selected row to highlight when the popup opens.
     *
     * @return a selected index, or 0 when nothing is selected
     */
    @Override
    protected int initialHighlight() {
        return selected.isEmpty() ? 0 : selected.iterator().next();
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
}
