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

/**
 * Shared base for select fields that open a popup with a filter field above a scrollable option list.
 * Subclasses own the selection state, the closed field content and the behavior applied when an
 * option or free text is committed.
 */
abstract class FilterableSelect extends Panel {
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float DEFAULT_ITEM_HEIGHT = 24;
    private static final float DEFAULT_MAX_POPUP_HEIGHT = 220;
    private static final float ARROW_GAP = 8;
    private static final float POPUP_GAP = 4;

    private final List<SelectOption> options = new ArrayList<>();
    private final List<SelectOption> visibleOptions = new ArrayList<>();
    private final IconSet fieldIcons = new IconSet("arrow-down-04.svg");
    private final IconSet listIcons = new IconSet("arrow-down-04.svg", "tick.svg");
    private final FilterInput filter = new FilterInput();
    private final PopupContent popupContent = new PopupContent();
    private final Popup popup = new Popup();
    private final OptionList optionList;

    private String placeholder = "Select...";
    private boolean refreshingFilter;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float itemHeight = DEFAULT_ITEM_HEIGHT;
    private float maxPopupHeight = DEFAULT_MAX_POPUP_HEIGHT;

    /**
     * Creates an empty select field with the filter popup.
     */
    protected FilterableSelect() {
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
        List<SelectOption> selected = selectedOptionsSnapshot();
        closePopup();
        this.options.clear();
        for (SelectOption option : options) {
            this.options.add(Objects.requireNonNull(option, "option"));
        }
        remapSelection(selected);
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
        List<SelectOption> selected = selectedOptionsSnapshot();
        boolean removed = options.remove(option);
        if (removed) {
            closePopup();
            remapSelection(selected);
        }
        return removed;
    }

    /**
     * Removes every option and closes the popup.
     */
    public final void clearOptions() {
        closePopup();
        options.clear();
        remapSelection(List.of());
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
     * Returns an option by index.
     *
     * @param index zero-based option index
     * @return the option, or null when the index is out of range
     */
    protected final SelectOption optionAt(int index) {
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    /**
     * Returns the number of options.
     *
     * @return the option count
     */
    protected final int optionCount() {
        return options.size();
    }

    /**
     * Finds an option by reference in the current list.
     *
     * @param option option to find
     * @return the zero-based index, or -1 when the option is not present
     */
    protected final int optionIndexOf(SelectOption option) {
        for (int index = 0; index < options.size(); index++) {
            if (options.get(index) == option) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Appends an option inside a subclass operation that wraps the call.
     *
     * @param option option to append
     * @throws NullPointerException if {@code option} is null
     */
    protected final void addOptionInternal(SelectOption option) {
        options.add(Objects.requireNonNull(option, "option"));
    }

    /**
     * Creates and appends an option with a text label and value.
     *
     * @param label option label
     * @return the added option
     * @throws NullPointerException if {@code label} is null
     */
    protected final SelectOption addLabeledOption(String label) {
        SelectOption option = new SelectOption(label);
        addOptionInternal(option);
        return option;
    }

    /**
     * Resolves the text color used for muted placeholder content.
     *
     * @param fallback color used when the field is not attached
     * @return the muted text color
     */
    protected final Color mutedColor(Color fallback) {
        return context() == null ? fallback : context().theme().textMuted();
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
        Size content = onFieldContentSize(font);
        float padding = style == null ? 0 : style.padding();
        float width = content.width() + padding * 2 + iconSize + ARROW_GAP;
        float height = content.height() + padding * 2;
        return new Size(Math.max(minimumWidth(), Math.min(maximumWidth(), width)),
                Math.max(minimumHeight(), Math.min(maximumHeight(), height)));
    }

    /**
     * Paints the field surface, the subclass content and the popup arrow.
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
        if (context() == null || popup.isOpen() || (options.isEmpty() && !canOpenWithNoOptions())) {
            return;
        }
        refreshingFilter = true;
        filter.setText("");
        refreshingFilter = false;
        rebuildVisibleOptions();
        optionList.setHighlighted(initialHighlight());
        float padding = popup.padding();
        float contentWidth = Math.max(0, bounds().width() - padding * 2);
        Rect anchor = new Rect(absolutePosition().x(), absolutePosition().y(),
                bounds().width(), bounds().height());
        // Attach the popup first so the filter measures with its context style and font.
        popup.open(context(), anchor, contentWidth, maxPopupHeight);
        float filterHeight = filter.measure(LayoutConstraints.loose(contentWidth, maxPopupHeight))
                .height();
        float listLimit = Math.max(0, maxPopupHeight - padding * 2 - filterHeight - POPUP_GAP);
        float listHeight = Math.min(optionList.preferredHeight(), listLimit);
        float height = filterHeight + POPUP_GAP + listHeight;
        popup.open(context(), anchor, contentWidth, height);
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

    private boolean isVisibleOptionSelected(int index) {
        if (index < 0 || index >= visibleOptions.size()) {
            return false;
        }
        int originalIndex = optionIndexOf(visibleOptions.get(index));
        return originalIndex >= 0 && isOptionSelected(originalIndex);
    }

    private int initialHighlight() {
        if (visibleOptions.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < visibleOptions.size(); index++) {
            SelectOption option = visibleOptions.get(index);
            int originalIndex = optionIndexOf(option);
            if (originalIndex >= 0 && !option.isDisabled()
                    && isOptionSelected(originalIndex)) {
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
        String text = filter.text().strip();
        if (!acceptsFreeText(text)) {
            return;
        }
        dismissPopup();
        commitFreeText(text);
    }

    private void activateVisible(int index) {
        if (index < 0 || index >= visibleOptions.size()
                || visibleOptions.get(index).isDisabled()) {
            return;
        }
        int originalIndex = optionIndexOf(visibleOptions.get(index));
        if (originalIndex < 0) {
            return;
        }
        if (closesOnActivate()) {
            dismissPopup();
        }
        activateOption(originalIndex);
    }

    private List<SelectOption> selectedOptionsSnapshot() {
        List<SelectOption> selected = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            if (isOptionSelected(index)) {
                selected.add(options.get(index));
            }
        }
        return selected;
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

    /**
     * Measures the content of the closed field.
     *
     * @param font resolved field font, or null when no font is available
     * @return the content size without padding and the arrow
     */
    protected abstract Size onFieldContentSize(Font font);

    /**
     * Paints the content of the closed field.
     *
     * @param renderer drawing manager for the current frame
     * @param content content rectangle inside the field padding and arrow slot
     * @param color resolved text color for the current state
     * @param state current widget state
     */
    protected abstract void drawFieldContent(RenderManager renderer, Rect content,
                                             Color color, WidgetState state);

    /**
     * Reports whether an original option index is selected.
     *
     * @param index zero-based option index
     * @return true when the option is selected
     */
    protected abstract boolean isOptionSelected(int index);

    /**
     * Applies activation for an original option index after the popup has been dismissed when the
     * subclass closes on activation.
     *
     * @param index zero-based option index
     */
    protected abstract void activateOption(int index);

    /**
     * Reports whether activating an option closes the popup before the activation callback runs.
     *
     * @return true to close after activation
     */
    protected abstract boolean closesOnActivate();

    /**
     * Rebuilds the selection after the option list changes.
     *
     * @param selectedOptions options that were selected before the change, in display order
     */
    protected abstract void remapSelection(List<SelectOption> selectedOptions);

    /**
     * Reports whether the popup may open when there are no options.
     *
     * @return true when free text entry still makes the empty popup useful
     */
    protected boolean canOpenWithNoOptions() {
        return false;
    }

    /**
     * Reports whether typed filter text may be committed as a free value.
     *
     * @param text stripped filter text
     * @return true when the subclass accepts the text
     */
    protected abstract boolean acceptsFreeText(String text);

    /**
     * Commits typed filter text after the popup has been dismissed.
     *
     * @param text stripped filter text
     */
    protected abstract void commitFreeText(String text);

    private final class PopupContent extends LayoutContainer {
        /**
         * Stacks the filter above the option list.
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
