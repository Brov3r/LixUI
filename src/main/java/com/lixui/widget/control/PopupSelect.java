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
import com.lixui.widget.overlay.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared base for select fields that open a scrollable option popup.
 * Subclasses provide the selected-row model and paint the closed field content.
 */
abstract class PopupSelect extends Panel {
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float DEFAULT_ITEM_HEIGHT = 24;
    private static final float DEFAULT_MAX_POPUP_HEIGHT = 220;
    private static final float ARROW_GAP = 8;

    private final List<SelectOption> options = new ArrayList<>();
    private final IconSet icons = new IconSet("arrow-down-04.svg", "tick.svg");
    private final OptionList optionList;
    private final Popup popup = new Popup();

    private float iconSize = DEFAULT_ICON_SIZE;
    private float itemHeight = DEFAULT_ITEM_HEIGHT;
    private float maxPopupHeight = DEFAULT_MAX_POPUP_HEIGHT;

    /**
     * Creates a closed select field.
     */
    protected PopupSelect() {
        setFocusable(true);
        optionList = new OptionList(new OptionList.SelectionModel() {
            @Override
            public boolean isSelected(int index) {
                return isOptionSelected(index);
            }

            @Override
            public void activate(int index) {
                activateOption(index);
            }
        }, this::dismissPopup, icons, itemHeight, iconSize);
        popup.setContent(optionList);
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
     * Returns the number of options.
     *
     * @return the option count
     */
    protected final int optionCount() {
        return options.size();
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

    private List<SelectOption> selectedOptionsSnapshot() {
        List<SelectOption> selected = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            if (isOptionSelected(index)) {
                selected.add(options.get(index));
            }
        }
        return selected;
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
     * @return this select field
     * @throws NullPointerException if {@code option} is null
     */
    public final PopupSelect addOption(SelectOption option) {
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
     * Returns the icon edge length used by the field and popup.
     *
     * @return the icon size in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the icon edge length used by the field and popup.
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
     * Opens the option popup below or above the field.
     */
    protected final void openPopup() {
        if (options.isEmpty() || context() == null || popup.isOpen()) {
            return;
        }
        ensureIcons();
        optionList.setOptions(options);
        optionList.setHighlighted(initialHighlight());
        float listWidth = Math.max(0, bounds().width() - popup.padding() * 2);
        float listHeight = Math.min(optionList.preferredHeight(),
                Math.max(itemHeight, maxPopupHeight - popup.padding() * 2));
        Rect anchor = new Rect(absolutePosition().x(), absolutePosition().y(),
                bounds().width(), bounds().height());
        popup.open(context(), anchor, listWidth, listHeight);
        context().requestFocus(optionList);
    }

    /**
     * Closes the option popup.
     */
    protected final void closePopup() {
        popup.close();
    }

    /**
     * Closes the popup after returning keyboard focus to the closed field.
     */
    protected final void dismissPopup() {
        if (!popup.isOpen()) {
            return;
        }
        if (context() != null && isEnabled() && isVisible()) {
            context().requestFocus(this);
        }
        closePopup();
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
     * Paints the field surface, subclass content and the popup arrow.
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
        ensureIcons();
        float arrowX = localBounds().width() - padding - iconSize;
        renderer.icon(icons.get(0), new Rect(arrowX, (localBounds().height() - iconSize) / 2,
                iconSize, iconSize), ImageStyle.ICON, style.text(WidgetState.NORMAL));
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

    /**
     * Requests the pointer cursor for the field.
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
     * Opens the popup with Enter, Space or an arrow key.
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
     * Releases the shared field and popup icons when the field leaves the tree.
     */
    @Override
    protected void onDetached() {
        closePopup();
        super.onDetached();
        icons.release(context());
    }

    /**
     * Closes the popup and drops cached icons after a context change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        closePopup();
        icons.release(context());
    }

    private void activateOption(int index) {
        if (index < 0 || index >= options.size() || options.get(index).isDisabled()) {
            return;
        }
        if (closesOnActivate()) {
            dismissPopup();
        }
        activate(index);
    }

    private void ensureIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(iconSize * context.density()));
        if (!icons.matches(context, size)) {
            icons.reload(context, size);
        }
    }

    /**
     * Returns the content size of the closed field.
     *
     * @param font resolved field font, or null when no font is available
     * @return the content size without padding and the arrow
     */
    protected abstract Size fieldContentSize(Font font);

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
     * Reports whether an option index is currently selected.
     *
     * @param index zero-based option index
     * @return true when the option is selected
     */
    protected abstract boolean isOptionSelected(int index);

    /**
     * Applies a new selection after an option is activated.
     *
     * @param index zero-based option index
     */
    protected abstract void activate(int index);

    /**
     * Reports whether activating an option closes the popup.
     *
     * @return true to close after activation
     */
    protected abstract boolean closesOnActivate();

    /**
     * Returns the initially highlighted option index.
     *
     * @return the zero-based index, or -1 for no highlight
     */
    protected abstract int initialHighlight();

    /**
     * Rebuilds the selection after the option list changes.
     *
     * @param selectedOptions options that were selected before the change, in display order
     */
    protected abstract void remapSelection(List<SelectOption> selectedOptions);
}
