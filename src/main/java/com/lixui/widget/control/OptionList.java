package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
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
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.container.ScrollBar;

import java.util.List;

/**
 * A scrollable option list hosted inside a {@link com.lixui.widget.Popup}.
 * The list owns pointer highlighting, keyboard navigation and scrolling; the owning select widget
 * supplies the selection model and activation behavior.
 */
final class OptionList extends Widget {
    /**
     * Selection callbacks used by the owning select widget.
     */
    interface SelectionModel {
        /**
         * Reports whether an option index is selected.
         *
         * @param index zero-based option index
         * @return true when the option is selected
         */
        boolean isSelected(int index);

        /**
         * Activates an option index.
         *
         * @param index zero-based option index
         */
        void activate(int index);
    }

    private static final float BAR_THICKNESS = 6;
    private static final float BAR_MARGIN = 2;
    private static final float ICON_GAP = 6;
    private static final float CHECK_SIZE = 14;

    private final SelectionModel model;
    private final Runnable closeAction;
    private final IconSet icons;
    private final ScrollBar verticalBar = new ScrollBar(ScrollBar.Orientation.VERTICAL);

    private List<SelectOption> options = List.of();
    private float itemHeight;
    private float iconSize;
    private float columnPadding = 6;
    private float maximumScroll;
    private float scrollY;
    private int highlighted = -1;
    private int hovered = -1;

    /**
     * Creates an option list for a popup.
     *
     * @param model selection model supplied by the owning select widget
     * @param closeAction action that closes the hosting popup
     * @param icons shared icons used for check marks
     * @param itemHeight fixed row height in logical UI units
     * @param iconSize option icon edge length in logical UI units
     */
    OptionList(SelectionModel model, Runnable closeAction, IconSet icons,
               float itemHeight, float iconSize) {
        this.model = model;
        this.closeAction = closeAction;
        this.icons = icons;
        this.itemHeight = itemHeight;
        this.iconSize = iconSize;
        setFocusable(true);
        setClipToBounds(true);
        verticalBar.setValueListener(value -> setScroll(value * maximumScroll));
        add(verticalBar);
        setVisibleIfChanged(verticalBar, false);
    }

    /**
     * Replaces the displayed options and resets scrolling.
     *
     * @param options options to display
     */
    void setOptions(List<SelectOption> options) {
        this.options = List.copyOf(options);
        scrollY = 0;
        highlighted = -1;
        hovered = -1;
        verticalBar.setValue(0);
    }

    /**
     * Returns the option row height.
     *
     * @return the row height in logical UI units
     */
    float itemHeight() {
        return itemHeight;
    }

    /**
     * Sets the option row height.
     *
     * @param itemHeight positive row height in logical UI units
     */
    void setItemHeight(float itemHeight) {
        this.itemHeight = itemHeight;
    }

    /**
     * Sets the option icon edge length.
     *
     * @param iconSize positive icon size in logical UI units
     */
    void setIconSize(float iconSize) {
        this.iconSize = iconSize;
    }

    /**
     * Sets the highlighted row and scrolls it into view.
     *
     * @param index zero-based row index, or -1 to clear the highlight
     */
    void setHighlighted(int index) {
        highlighted = index >= 0 && index < options.size() ? index : -1;
        scrollToHighlighted();
    }

    /**
     * Returns the currently highlighted row.
     *
     * @return the zero-based row index, or -1 when no row is highlighted
     */
    int highlighted() {
        return highlighted;
    }

    /**
     * Computes the preferred list height for the current options.
     *
     * @return the content height in logical UI units
     */
    float preferredHeight() {
        return options.size() * itemHeight;
    }

    /**
     * Returns the scroll offset.
     *
     * @return the offset in logical UI units
     */
    float scrollY() {
        return scrollY;
    }

    /**
     * Measures the full option list.
     *
     * @param constraints available and required dimensions
     * @return the natural list size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(constraints.maxWidth(),
                Math.min(options.size() * itemHeight, constraints.maxHeight()));
    }

    /**
     * Updates scrollbar geometry and clamps the scroll offset.
     */
    @Override
    protected void onLayout() {
        float width = localBounds().width();
        float height = localBounds().height();
        maximumScroll = Math.max(0, options.size() * itemHeight - height);
        scrollY = Math.clamp(scrollY, 0, maximumScroll);
        boolean visible = maximumScroll > 0;
        setVisibleIfChanged(verticalBar, visible);
        if (visible) {
            verticalBar.layout(new Rect(width - BAR_MARGIN - BAR_THICKNESS, BAR_MARGIN,
                    BAR_THICKNESS, Math.max(0, height - BAR_MARGIN * 2)));
            verticalBar.setVisibleFraction(options.size() * itemHeight > 0
                    ? height / (options.size() * itemHeight) : 1);
        }
        verticalBar.setValue(maximumScroll > 0 ? scrollY / maximumScroll : 0);
    }

    /**
     * Paints the visible option rows.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty() || options.isEmpty()) {
            return;
        }
        WidgetStyle style = style();
        Theme theme = context().theme();
        Font font = font();
        ensureIcons();
        Color textColor = style == null ? theme.text() : style.text(WidgetState.NORMAL);
        Color disabledColor = style == null ? theme.textMuted() : style.disabledText();
        Color hoverColor = Color.mix(theme.surface(), theme.primary(), 0.18f);
        Color highlightColor = Color.mix(theme.surface(), theme.primary(), 0.24f);
        Color separator = theme.border().withAlpha(0.45f);
        float rowWidth = localBounds().width() - (verticalBar.isVisible() ? 10 : 0);
        for (int index = 0; index < options.size(); index++) {
            float top = index * itemHeight - scrollY;
            if (top + itemHeight <= 0) {
                continue;
            }
            if (top >= localBounds().height()) {
                break;
            }
            if (index == hovered || index == highlighted) {
                renderer.rect(new Rect(0, top, rowWidth, itemHeight),
                        index == highlighted ? highlightColor : hoverColor);
            }
            drawRow(renderer, theme, font, options.get(index), index, top, rowWidth,
                    textColor, disabledColor);
            if (index < options.size() - 1) {
                renderer.rect(new Rect(columnPadding, top + itemHeight - 1,
                        Math.max(0, rowWidth - columnPadding * 2), 1), separator);
            }
        }
    }

    private void drawRow(RenderManager renderer, Theme theme, Font font, SelectOption option,
                         int index, float top, float rowWidth,
                         Color textColor, Color disabledColor) {
        Color color = option.isDisabled() ? disabledColor : textColor;
        float x = columnPadding;
        if (model.isSelected(index)) {
            renderer.icon(icons.get(1), new Rect(x, top + (itemHeight - CHECK_SIZE) / 2,
                    CHECK_SIZE, CHECK_SIZE), ImageStyle.ICON, theme.primary());
        }
        x += CHECK_SIZE + ICON_GAP;
        if (option.icon() != null) {
            renderer.icon(option.icon(), new Rect(x, top + (itemHeight - iconSize) / 2,
                    iconSize, iconSize), ImageStyle.ICON, color);
            x += iconSize + ICON_GAP;
        }
        float textWidth = Math.max(0, rowWidth - columnPadding - x);
        if (font != null && textWidth > 0) {
            renderer.text(font, option.label(), x, top + itemHeight / 2, color,
                    new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
    }

    private void ensureIcons() {
        if (context() == null) {
            return;
        }
        int size = Math.max(1, Math.round(CHECK_SIZE * context().density()));
        if (!icons.matches(context(), size)) {
            icons.reload(context(), size);
        }
    }

    /**
     * Tracks the hovered row.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so pointer movement can bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        hovered = indexAt(y);
        return false;
    }

    /**
     * Clears hover feedback when the pointer leaves the list.
     */
    @Override
    protected void onPointerExit() {
        hovered = -1;
    }

    /**
     * Returns a pointer cursor over selectable rows.
     *
     * @return the pointer cursor over a row, otherwise the default cursor
     */
    @Override
    public Cursor cursor() {
        return hovered >= 0 ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /**
     * Activates the pressed option row.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press was consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        int index = indexAt(y);
        if (index < 0 || options.get(index).isDisabled()) {
            return false;
        }
        highlighted = index;
        model.activate(index);
        return true;
    }

    /**
     * Scrolls the option list with the mouse wheel.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        if (maximumScroll <= 0 || deltaY == 0) {
            return false;
        }
        float next = Math.clamp(scrollY - deltaY * itemHeight, 0, maximumScroll);
        if (next == scrollY) {
            return false;
        }
        scrollY = next;
        verticalBar.setValue(maximumScroll > 0 ? scrollY / maximumScroll : 0);
        return true;
    }

    /**
     * Moves the highlight or activates it with the keyboard.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        return switch (key) {
            case UP -> moveHighlight(-1);
            case DOWN -> moveHighlight(1);
            case HOME -> highlightEdge(true);
            case END -> highlightEdge(false);
            case PAGE_UP -> movePage(-1);
            case PAGE_DOWN -> movePage(1);
            case ENTER, SPACE -> activateHighlighted();
            case ESCAPE -> {
                closeAction.run();
                yield true;
            }
            default -> false;
        };
    }

    private boolean moveHighlight(int delta) {
        if (options.isEmpty()) {
            return false;
        }
        int start = highlighted < 0 ? (delta > 0 ? -1 : options.size()) : highlighted;
        int index = start + delta;
        while (index >= 0 && index < options.size() && options.get(index).isDisabled()) {
            index += delta;
        }
        if (index < 0 || index >= options.size()) {
            return true;
        }
        highlighted = index;
        scrollToHighlighted();
        return true;
    }

    private boolean highlightEdge(boolean first) {
        if (options.isEmpty()) {
            return false;
        }
        int index = first ? 0 : options.size() - 1;
        int delta = first ? 1 : -1;
        while (index >= 0 && index < options.size() && options.get(index).isDisabled()) {
            index += delta;
        }
        if (index >= 0 && index < options.size()) {
            highlighted = index;
            scrollToHighlighted();
        }
        return true;
    }

    private boolean movePage(int direction) {
        int count = Math.max(1, (int) (localBounds().height() / itemHeight));
        for (int step = 0; step < count; step++) {
            if (!moveHighlight(direction)) {
                break;
            }
        }
        return true;
    }

    private boolean activateHighlighted() {
        if (highlighted < 0 || highlighted >= options.size()
                || options.get(highlighted).isDisabled()) {
            return false;
        }
        model.activate(highlighted);
        return true;
    }

    private void setScroll(float scrollY) {
        this.scrollY = Math.clamp(scrollY, 0, maximumScroll);
        if (maximumScroll > 0) {
            verticalBar.setValue(this.scrollY / maximumScroll);
        }
    }

    private void scrollToHighlighted() {
        if (highlighted < 0) {
            return;
        }
        float top = highlighted * itemHeight;
        float bottom = top + itemHeight;
        if (top < scrollY) {
            setScroll(top);
        } else if (bottom > scrollY + localBounds().height()) {
            setScroll(bottom - localBounds().height());
        }
    }

    private int indexAt(float y) {
        if (options.isEmpty() || y < 0 || y >= localBounds().height()) {
            return -1;
        }
        int index = (int) Math.floor((y + scrollY) / itemHeight);
        return index >= 0 && index < options.size() ? index : -1;
    }
}
