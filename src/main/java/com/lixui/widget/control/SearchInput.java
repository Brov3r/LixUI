package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

import java.util.function.Consumer;

/**
 * A search field with a leading magnifier icon and a trailing clear action.
 * Text changes restart a debounce timer and report the query after the delay, while Enter reports
 * it immediately. The clear cross appears only while the field has text and removes the value when
 * clicked. The icons reserve content space, so the text and caret never overlap them.
 */
public class SearchInput extends Input {
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float ICON_GAP = 6;
    private static final float DEFAULT_DEBOUNCE_SECONDS = 0.3f;
    private static final float CLEAR_MARK = 4;
    private static final float CLEAR_STROKE = 1.5f;

    private final DecorativeImage searchIcon = new DecorativeImage();
    private float iconSize = DEFAULT_ICON_SIZE;
    private float debounceSeconds = DEFAULT_DEBOUNCE_SECONDS;
    private float elapsedSinceChange;
    private boolean pendingSearch;
    private boolean clearHovered;
    private boolean clearPressed;
    private Consumer<String> onSearch;

    /**
     * Creates an empty search field with a leading icon.
     */
    public SearchInput() {
        this("");
    }

    /**
     * Creates a search field with text.
     *
     * @param text initial value
     * @throws NullPointerException if {@code text} is null
     */
    public SearchInput(String text) {
        super(text);
        searchIcon.setIcon("search.svg");
        searchIcon.setAutoSize(false);
        add(searchIcon);
        applyTheme();
    }

    /**
     * Returns the delay between the last text change and the search callback.
     *
     * @return the debounce delay in seconds
     */
    public final float debounceSeconds() {
        return debounceSeconds;
    }

    /**
     * Sets the delay between the last text change and the search callback.
     * A zero delay still waits for the next update frame.
     *
     * @param debounceSeconds non-negative delay in seconds
     * @throws IllegalArgumentException if the delay is negative or non-finite
     */
    public final void setDebounceSeconds(float debounceSeconds) {
        if (!Float.isFinite(debounceSeconds) || debounceSeconds < 0) {
            throw new IllegalArgumentException("Debounce delay must be finite and non-negative.");
        }
        this.debounceSeconds = debounceSeconds;
    }

    /**
     * Returns the callback invoked with the current query.
     *
     * @return the search callback, or null when none is assigned
     */
    public final Consumer<String> onSearch() {
        return onSearch;
    }

    /**
     * Assigns the callback invoked with the current query after the debounce delay or on Enter.
     *
     * @param onSearch search callback, or null to clear it
     */
    public final void setOnSearch(Consumer<String> onSearch) {
        this.onSearch = onSearch;
    }

    /**
     * Returns the pointer cursor over the clear action and the text cursor elsewhere.
     *
     * @return the pointer cursor while the clear action is hovered, otherwise the field cursor
     */
    @Override
    public Cursor cursor() {
        if (isEnabled() && clearHovered && isClearVisible()) {
            return Cursor.POINTER;
        }
        return super.cursor();
    }

    /**
     * Returns the content rectangle with the leading icon and the trailing clear action removed.
     *
     * @param widgetStyle resolved style used for the field
     * @return the content rectangle in widget-local coordinates
     */
    @Override
    protected Rect contentRect(WidgetStyle widgetStyle) {
        Rect base = super.contentRect(widgetStyle);
        if (base.isEmpty()) {
            return base;
        }
        float left = iconReservation(base);
        float right = isClearVisible() ? iconReservation(base) : 0;
        return new Rect(base.x() + left, base.y(),
                Math.max(0, base.width() - left - right), base.height());
    }

    /**
     * Adds the leading icon and the conditional clear action to the natural width.
     *
     * @param constraints available and required dimensions
     * @return the natural field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Size base = super.onMeasure(constraints);
        float extra = iconSize + ICON_GAP;
        if (isClearVisible()) {
            extra += iconSize + ICON_GAP;
        }
        return new Size(base.width() + extra, base.height());
    }

    /**
     * Places the leading magnifier inside the field content area.
     */
    @Override
    protected void onLayout() {
        super.onLayout();
        WidgetStyle style = resolvedStyle();
        if (style == null) {
            return;
        }
        Rect base = super.contentRect(style);
        float side = Math.min(iconSize, base.height());
        if (base.isEmpty() || side <= 0) {
            searchIcon.setVisible(false);
            return;
        }
        searchIcon.setVisible(true);
        searchIcon.layout(new Rect(base.x(), base.y() + (base.height() - side) / 2, side, side));
    }

    /**
     * Loads and tints the search icon when the field attaches.
     */
    @Override
    protected void onAttached() {
        applyTheme();
    }

    /**
     * Reapplies the theme color to the search icon after a context change.
     */
    @Override
    protected void onContextChanged() {
        applyTheme();
    }

    /**
     * Advances the debounce timer and reports the query when it elapses.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        super.onUpdate(frame);
        if (!pendingSearch) {
            return;
        }
        elapsedSinceChange += frame.deltaSeconds();
        if (elapsedSinceChange >= debounceSeconds) {
            fireSearch();
        }
    }

    /**
     * Restarts the debounce timer after the value changes.
     */
    @Override
    protected void onTextChanged() {
        super.onTextChanged();
        elapsedSinceChange = 0;
        pendingSearch = true;
    }

    /**
     * Reports the query immediately when Enter is pressed on the enabled field.
     */
    @Override
    protected void onSubmitted() {
        super.onSubmitted();
        fireSearch();
    }

    /**
     * Paints the field and the clear cross over the trailing content edge.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        if (localBounds().isEmpty() || !isClearVisible()) {
            return;
        }
        Rect clear = clearBounds();
        if (clear.isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        Color color = clearHovered || clearPressed ? theme.text() : theme.textMuted();
        float centerX = clear.x() + clear.width() / 2;
        float centerY = clear.y() + clear.height() / 2;
        float mark = Math.min(CLEAR_MARK, clear.width() * 0.22f);
        renderer.strokeLine(new Point(centerX - mark, centerY - mark),
                new Point(centerX + mark, centerY + mark), CLEAR_STROKE, Paint.solid(color));
        renderer.strokeLine(new Point(centerX - mark, centerY + mark),
                new Point(centerX + mark, centerY - mark), CLEAR_STROKE, Paint.solid(color));
    }

    /**
     * Tracks hover over the clear action.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return the result of the text selection handling
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        clearHovered = isClearVisible() && clearBounds().contains(x, y);
        return super.onPointerMove(x, y);
    }

    /**
     * Clears the clear-action hover when the pointer leaves.
     */
    @Override
    protected void onPointerExit() {
        super.onPointerExit();
        clearHovered = false;
        clearPressed = false;
    }

    /**
     * Prevents the text field from moving the caret when the clear action is pressed.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press belongs to the clear action
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT && isClearVisible() && clearBounds().contains(x, y)) {
            clearPressed = true;
            return true;
        }
        clearPressed = false;
        return super.onPointerPress(x, y, button, modifiers);
    }

    /**
     * Clears the pressed clear-action state after the button is released.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return the result of the text selection handling
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button,
                                       Modifiers modifiers) {
        clearPressed = false;
        return super.onPointerRelease(x, y, button, modifiers);
    }

    /**
     * Clears the value when a completed left click lands on the clear action.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the clear action consumed the click
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT && isClearVisible() && clearBounds().contains(x, y)) {
            setText("");
            return true;
        }
        return super.onClick(x, y, button, modifiers);
    }

    private void fireSearch() {
        pendingSearch = false;
        elapsedSinceChange = 0;
        if (onSearch != null) {
            onSearch.accept(text());
        }
    }

    private void applyTheme() {
        UiContext uiContext = context();
        if (uiContext != null) {
            searchIcon.setTint(uiContext.theme().textMuted());
        }
    }

    private boolean isClearVisible() {
        return !text().isEmpty();
    }

    private Rect clearBounds() {
        WidgetStyle style = resolvedStyle();
        if (style == null) {
            return Rect.EMPTY;
        }
        Rect base = super.contentRect(style);
        if (base.isEmpty()) {
            return Rect.EMPTY;
        }
        float side = Math.min(iconSize, base.height());
        if (side <= 0) {
            return Rect.EMPTY;
        }
        return new Rect(base.x() + base.width() - side,
                base.y() + (base.height() - side) / 2, side, side);
    }

    private float iconReservation(Rect base) {
        float side = Math.min(iconSize, base.height());
        return side <= 0 ? 0 : side + ICON_GAP;
    }

    private static final class DecorativeImage extends Image {
        /**
         * Keeps the icon from intercepting text selection in the field.
         *
         * @param x horizontal coordinate relative to the widget's top-left corner
         * @param y vertical coordinate relative to the widget's top-left corner
         * @return always null
         */
        @Override
        public Widget hitTest(float x, float y) {
            return null;
        }
    }
}
