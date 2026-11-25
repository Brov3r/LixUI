package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
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
import com.lixui.widget.layout.LayoutContainer;
import com.lixui.widget.overlay.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public class Combobox extends Panel {
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float DEFAULT_ITEM_HEIGHT = 24;
    private static final float DEFAULT_MAX_POPUP_HEIGHT = 220;
    private static final float ARROW_GAP = 8;
    private static final float POPUP_GAP = 4;
    private static final float ICON_GAP = 6;

    private final List<SelectOption> options = new ArrayList<>();
    private final List<SelectOption> visibleOptions = new ArrayList<>();
    private final IconSet fieldIcons = new IconSet("arrow-down-04.svg");
    private final IconSet listIcons = new IconSet("arrow-down-04.svg", "tick.svg");
    private final FilterInput filter = new FilterInput();
    private final PopupContent popupContent = new PopupContent();
    private final Popup popup = new Popup();
    private final OptionList optionList;

    private String placeholder = "Select...";
    private String customValue;
    private boolean allowCustom;
    private boolean refreshingFilter;
    private boolean highlightScrollPending;
    private int selectedIndex = -1;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float itemHeight = DEFAULT_ITEM_HEIGHT;
    private float maxPopupHeight = DEFAULT_MAX_POPUP_HEIGHT;
    private Consumer<SelectOption> onChange;

    /**
     * Creates an empty combobox with the default placeholder.
     */
    public Combobox() {
        setFocusable(true);
        optionList = new OptionList(new OptionList.SelectionModel() {
            @Override
            public boolean isSelected(int index) {
                return isVisibleOptionSelected(index);
            }

            @Override
            public void activate(int index) {
                activateVisible(index);
            }
        }, this::dismissPopup, listIcons, itemHeight, iconSize);
        filter.setOnChange(value -> refreshFilter());
        popupContent.add(filter).add(optionList);
        popup.setContent(popupContent);
    }

    /**
     * Returns the option list in display order.
     *
     * @return an immutable snapshot of the options
     */
    public final List<SelectOption> options() {
        return List.copyOf(options);
    }

    /**
     * Replaces every option and closes the popup.
     *
     * @param options new options
     * @throws NullPointerException if {@code options} or one of its entries is null
     */
    public final void setOptions(List<SelectOption> options) {
        Objects.requireNonNull(options, "options");
        SelectOption selected = selectedOption();
        closePopup();
        this.options.clear();
        for (SelectOption option : options) {
            this.options.add(Objects.requireNonNull(option, "option"));
        }
        restoreSelection(selected);
    }

    /**
     * Appends an option with a text label and value.
     *
     * @param label option label
     * @return the added option
     * @throws NullPointerException if {@code label} is null
     */
    public final SelectOption addOption(String label) {
        SelectOption option = new SelectOption(label);
        addOption(option);
        return option;
    }

    /**
     * Appends an existing option.
     *
     * @param option option to append
     * @return this combobox
     * @throws NullPointerException if {@code option} is null
     */
    public final Combobox addOption(SelectOption option) {
        options.add(Objects.requireNonNull(option, "option"));
        return this;
    }

    /**
     * Removes an option by reference.
     *
     * @param option option to remove
     * @return true when the option was present
     * @throws NullPointerException if {@code option} is null
     */
    public final boolean removeOption(SelectOption option) {
        Objects.requireNonNull(option, "option");
        SelectOption selected = selectedOption();
        boolean removed = options.remove(option);
        if (removed) {
            closePopup();
            restoreSelection(selected);
        }
        return removed;
    }

    /**
     * Removes every option and closes the popup.
     */
    public final void clearOptions() {
        closePopup();
        options.clear();
        selectedIndex = -1;
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
        return selectedIndex >= 0 && selectedIndex < options.size() ? options.get(selectedIndex) : null;
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
        if (index < -1 || index >= options.size()) {
            throw new IllegalArgumentException("Option index is out of range.");
        }
        if (index >= 0 && options.get(index).isDisabled()) {
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
        for (int index = 0; index < options.size(); index++) {
            SelectOption option = options.get(index);
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
     * Assigns a callback invoked after the value changes.
     * The callback receives the selected option, or null for a custom or cleared value.
     *
     * @param onChange callback receiving the selected option, or null to clear it
     */
    public final void setOnChange(Consumer<SelectOption> onChange) {
        this.onChange = onChange;
    }

    /**
     * Returns the icon edge length used by the field and popup rows.
     *
     * @return the icon size in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the icon edge length used by the field and popup rows.
     *
     * @param iconSize positive icon size in logical UI units
     * @throws IllegalArgumentException if the size is non-finite or not positive
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
        optionList.setIconSize(iconSize);
    }

    /**
     * Returns the popup row height.
     *
     * @return the row height in logical UI units
     */
    public final float itemHeight() {
        return itemHeight;
    }

    /**
     * Sets the popup row height.
     *
     * @param itemHeight positive row height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public final void setItemHeight(float itemHeight) {
        if (!Float.isFinite(itemHeight) || itemHeight <= 0) {
            throw new IllegalArgumentException("Item height must be finite and positive.");
        }
        this.itemHeight = itemHeight;
        optionList.setItemHeight(itemHeight);
    }

    /**
     * Returns the maximum popup surface height.
     *
     * @return the maximum height in logical UI units
     */
    public final float maxPopupHeight() {
        return maxPopupHeight;
    }

    /**
     * Sets the maximum popup surface height.
     *
     * @param maxPopupHeight positive maximum height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public final void setMaxPopupHeight(float maxPopupHeight) {
        if (!Float.isFinite(maxPopupHeight) || maxPopupHeight <= 0) {
            throw new IllegalArgumentException("Maximum popup height must be finite and positive.");
        }
        this.maxPopupHeight = maxPopupHeight;
    }

    /**
     * Reports whether the popup is currently open.
     *
     * @return true while the popup is visible
     */
    public final boolean isPopupOpen() {
        return popup.isOpen();
    }

    /**
     * Returns the control style from the current UI context.
     *
     * @return the context control style, or null when the field is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        return context() == null ? null : context().theme().controlStyle();
    }

    /**
     * Measures the field content and the arrow slot.
     *
     * @param constraints available and required dimensions
     * @return the natural field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        WidgetStyle style = resolvedStyle();
        Font font = font();
        Size content = fieldContentSize(font);
        float padding = style == null ? 0 : style.padding();
        float width = content.width() + padding * 2 + iconSize + ARROW_GAP;
        float height = content.height() + padding * 2;
        return new Size(Math.max(minimumWidth(), Math.min(maximumWidth(), width)),
                Math.max(minimumHeight(), Math.min(maximumHeight(), height)));
    }

    /**
     * Paints the field surface, the current value and the popup arrow.
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
        Color color = style.text(state);
        float padding = style.padding();
        float contentWidth = Math.max(0, localBounds().width() - padding * 2
                - iconSize - ARROW_GAP);
        Rect content = new Rect(padding, padding, contentWidth,
                Math.max(0, localBounds().height() - padding * 2));
        drawFieldContent(renderer, content, color, state);
        ensureFieldIcons();
        float arrowX = localBounds().width() - padding - iconSize;
        renderer.icon(fieldIcons.get(0), new Rect(arrowX, (localBounds().height() - iconSize) / 2,
                iconSize, iconSize), ImageStyle.ICON, style.text(WidgetState.NORMAL));
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
     * Opens the popup on a left-button click.
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
     * Opens the popup on a left-button double click.
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
     * Opens the popup with Enter, Space or an arrow key and closes it with Escape.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ESCAPE && popup.isOpen()) {
            dismissPopup();
            return true;
        }
        if (key == Key.ENTER || key == Key.SPACE || key == Key.UP || key == Key.DOWN) {
            openPopup();
            return true;
        }
        return false;
    }

    /**
     * Closes the popup and releases cached icons when the field leaves the tree.
     */
    @Override
    protected void onDetached() {
        popup.close();
        super.onDetached();
        fieldIcons.release(context());
        listIcons.release(context());
    }

    /**
     * Closes the popup and drops cached icons after a context change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        popup.close();
        fieldIcons.release(context());
        listIcons.release(context());
    }

    private void openPopup() {
        if (context() == null || popup.isOpen() || (options.isEmpty() && !allowCustom)) {
            return;
        }
        refreshingFilter = true;
        filter.setText("");
        refreshingFilter = false;
        rebuildVisibleOptions();
        optionList.setHighlighted(initialHighlight());
        float padding = popup.padding();
        float contentWidth = Math.max(0, bounds().width() - padding * 2);
        float filterHeight = filter.measure(LayoutConstraints.loose(contentWidth, maxPopupHeight))
                .height();
        float listLimit = Math.max(0, maxPopupHeight - padding * 2 - filterHeight - POPUP_GAP);
        float listHeight = Math.min(optionList.preferredHeight(), listLimit);
        float height = filterHeight + POPUP_GAP + listHeight;
        Rect anchor = new Rect(absolutePosition().x(), absolutePosition().y(),
                bounds().width(), bounds().height());
        popup.open(context(), anchor, contentWidth, height);
        highlightScrollPending = true;
        context().requestFocus(filter);
    }

    private void closePopup() {
        popup.close();
    }

    private void dismissPopup() {
        if (!popup.isOpen()) {
            return;
        }
        if (context() != null && isEnabled() && isVisible()) {
            context().requestFocus(this);
        }
        closePopup();
    }

    private void refreshFilter() {
        if (refreshingFilter || !popup.isOpen()) {
            return;
        }
        rebuildVisibleOptions();
        optionList.setHighlighted(firstAvailableHighlight());
    }

    private void rebuildVisibleOptions() {
        String query = filter.text().strip().toLowerCase(Locale.ROOT);
        visibleOptions.clear();
        for (SelectOption option : options) {
            if (query.isEmpty() || option.label().toLowerCase(Locale.ROOT).contains(query)) {
                visibleOptions.add(option);
            }
        }
        optionList.setOptions(visibleOptions);
    }

    private int initialHighlight() {
        if (visibleOptions.isEmpty()) {
            return -1;
        }
        SelectOption selected = selectedOption();
        if (selected != null) {
            int index = indexOfIdentity(visibleOptions, selected);
            if (index >= 0 && !visibleOptions.get(index).isDisabled()) {
                return index;
            }
        }
        return firstAvailableHighlight();
    }

    private int firstAvailableHighlight() {
        for (int index = 0; index < visibleOptions.size(); index++) {
            if (!visibleOptions.get(index).isDisabled()) {
                return index;
            }
        }
        return -1;
    }

    private void moveHighlight(int delta) {
        if (visibleOptions.isEmpty()) {
            return;
        }
        int start = optionList.highlighted();
        int index = start < 0 ? (delta > 0 ? -1 : visibleOptions.size()) : start;
        index += delta;
        while (index >= 0 && index < visibleOptions.size()
                && visibleOptions.get(index).isDisabled()) {
            index += delta;
        }
        if (index >= 0 && index < visibleOptions.size()) {
            optionList.setHighlighted(index);
        }
    }

    private void commitFilterEntry() {
        int index = optionList.highlighted();
        if (index >= 0 && index < visibleOptions.size()) {
            activateVisible(index);
            return;
        }
        if (!allowCustom) {
            return;
        }
        String custom = filter.text().strip();
        if (custom.isEmpty()) {
            return;
        }
        if (selectedIndex == -1 && custom.equals(customValue)) {
            dismissPopup();
            return;
        }
        selectedIndex = -1;
        customValue = custom;
        dismissPopup();
        notifyChange();
    }

    private void activateVisible(int index) {
        if (index < 0 || index >= visibleOptions.size()
                || visibleOptions.get(index).isDisabled()) {
            return;
        }
        SelectOption option = visibleOptions.get(index);
        int originalIndex = indexOfIdentity(options, option);
        if (originalIndex < 0) {
            return;
        }
        boolean changed = selectedIndex != originalIndex || customValue != null;
        selectedIndex = originalIndex;
        customValue = null;
        dismissPopup();
        if (changed) {
            notifyChange();
        }
    }

    private boolean isVisibleOptionSelected(int index) {
        if (index < 0 || index >= visibleOptions.size()) {
            return false;
        }
        SelectOption selected = selectedOption();
        return selected != null && selected == visibleOptions.get(index);
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(selectedOption());
        }
    }

    private void restoreSelection(SelectOption selected) {
        if (selected == null) {
            selectedIndex = -1;
            return;
        }
        int index = indexOfIdentity(options, selected);
        selectedIndex = index >= 0 && !options.get(index).isDisabled() ? index : -1;
    }

    private static int indexOfIdentity(List<SelectOption> options, SelectOption option) {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index) == option) {
                return index;
            }
        }
        return -1;
    }

    private Size fieldContentSize(Font font) {
        if (font == null) {
            return new Size(0, 0);
        }
        String text = text();
        if (text.isEmpty()) {
            text = placeholder;
        }
        Size size = font.measure(text);
        SelectOption option = selectedOption();
        if (option != null && option.icon() != null) {
            size = new Size(size.width() + iconSize + ICON_GAP,
                    Math.max(size.height(), iconSize));
        }
        return size;
    }

    private void drawFieldContent(RenderManager renderer, Rect content,
                                  Color color, WidgetState state) {
        Font font = font();
        String text = text();
        SelectOption option = selectedOption();
        Color textColor = text.isEmpty() ? mutedColor(color) : color;
        String label = text.isEmpty() ? placeholder : text;
        float x = content.x();
        if (option != null && option.icon() != null) {
            renderer.icon(option.icon(), new Rect(x, content.y() + (content.height() - iconSize) / 2,
                    iconSize, iconSize), ImageStyle.ICON, color);
            x += iconSize + ICON_GAP;
        }
        float width = Math.max(0, content.right() - x);
        if (font != null && width > 0) {
            renderer.text(font, label, x, content.y() + content.height() / 2, textColor,
                    new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
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

    private WidgetStyle resolvedStyle() {
        if (hasLocalStyle()) {
            return style();
        }
        return context() == null ? null : context().theme().controlStyle();
    }

    private Color mutedColor(Color fallback) {
        return context() == null ? fallback : context().theme().textMuted();
    }

    private void ensureFieldIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(iconSize * context.density()));
        if (!fieldIcons.matches(context, size)) {
            fieldIcons.reload(context, size);
        }
    }

    private final class PopupContent extends LayoutContainer {
        /**
         * Stacks the filter above the option list and reveals the initial highlight once the list
         * has its final height.
         */
        @Override
        protected void onLayout() {
            float width = bounds().width();
            float height = bounds().height();
            float filterHeight = Math.min(height,
                    filter.measure(LayoutConstraints.loose(width, height)).height());
            filter.layout(new Rect(0, 0, width, filterHeight));
            float listTop = Math.min(height, filterHeight + POPUP_GAP);
            optionList.layout(new Rect(0, listTop, width, Math.max(0, height - listTop)));
            if (highlightScrollPending) {
                highlightScrollPending = false;
                int index = optionList.highlighted();
                if (index >= 0) {
                    optionList.setHighlighted(index);
                }
            }
        }
    }

    private final class FilterInput extends Input {
        /**
         * Handles list navigation and commit keys before the text field editing shortcuts.
         *
         * @param key pressed key
         * @param modifiers active modifiers
         * @param repeat whether this is a key repeat
         * @return true when the key was consumed
         */
        @Override
        protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
            if (key == Key.UP) {
                moveHighlight(-1);
                return true;
            }
            if (key == Key.DOWN) {
                moveHighlight(1);
                return true;
            }
            if (key == Key.ENTER || key == Key.KEYPAD_ENTER) {
                if (!repeat) {
                    commitFilterEntry();
                }
                return true;
            }
            if (key == Key.ESCAPE) {
                dismissPopup();
                return true;
            }
            return super.onKeyPressed(key, modifiers, repeat);
        }
    }
}
