package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;

/**
 * A scrollable list that draws and hit-tests only the rows inside its viewport.
 * Row heights can be fixed or supplied per index. Fixed heights use constant memory and arithmetic
 * row lookup. Variable heights are cached in a double-precision prefix-sum table, so locating the
 * first visible row and mapping a pointer position to a row both take logarithmic time. A row
 * renderer paints the visible content, the list paints selection and hover feedback, and a vertical
 * {@link ScrollBar} follows the scroll position.
 * The list supports no, single and multiple selection. Multiple selection follows the usual
 * Ctrl-toggle and Shift-range conventions. The keyboard moves the active row, selects it according
 * to the selection mode and keeps it visible.
 */
public class VirtualList extends Panel {
    /**
     * The row selection behavior of a virtual list.
     */
    public enum SelectionMode {
        /** Rows cannot be selected. */
        NONE,
        /** At most one row is selected at a time. */
        SINGLE,
        /** Multiple rows can be selected with Ctrl, Shift or the Space key. */
        MULTIPLE
    }

    /**
     * Describes the interaction state of a row passed to a {@link RowRenderer}.
     *
     * @param selected whether the row is selected
     * @param hovered whether the pointer is over the row
     * @param active whether the row is the keyboard cursor
     */
    public record RowState(boolean selected, boolean hovered, boolean active) {
    }

    /**
     * Paints a single row that is currently inside the viewport.
     */
    @FunctionalInterface
    public interface RowRenderer {
        /**
         * Paints a visible row.
         *
         * @param index zero-based row index
         * @param bounds row bounds in the list local coordinate space
         * @param state interaction state of the row
         * @param renderer drawing manager for the current frame
         */
        void render(int index, Rect bounds, RowState state, RenderManager renderer);
    }

    private static final float DEFAULT_ROW_HEIGHT = 28;
    private static final float BAR_THICKNESS = 8;
    private static final float BAR_MARGIN = 4;
    private static final float WHEEL_STEP = 48;

    private final ScrollBar verticalBar = new ScrollBar(ScrollBar.Orientation.VERTICAL);
    private final Set<Integer> selected = new LinkedHashSet<>();

    private SelectionMode selectionMode = SelectionMode.NONE;
    private RowRenderer rowRenderer;
    private Consumer<List<Integer>> selectionListener;
    private IntToDoubleFunction rowHeightProvider;
    private float rowHeight = DEFAULT_ROW_HEIGHT;
    private int itemCount;
    private double[] prefix;
    private boolean metricsDirty = true;
    private double contentHeight;
    private float scrollY;
    private int activeIndex = -1;
    private int anchorIndex = -1;
    private int hoveredIndex = -1;
    private float pointerX;
    private float pointerY;
    private boolean pointerInside;

    /**
     * Creates an empty virtual list with clipping and keyboard focus enabled.
     */
    public VirtualList() {
        setClipToBounds(true);
        setFocusable(true);
        verticalBar.setValueListener(value -> setScrollY(value * maxScrollY()));
        setVisibleIfChanged(verticalBar, false);
        add(verticalBar);
    }

    /**
     * Returns the row index used as the keyboard cursor.
     *
     * @return the zero-based active row index, or -1 when no row is active
     */
    public final int activeIndex() {
        return activeIndex;
    }

    /**
     * Returns the number of rows.
     *
     * @return the non-negative row count
     */
    public final int itemCount() {
        return itemCount;
    }

    /**
     * Replaces the number of rows and invalidates the cached row heights.
     * The scroll position is clamped on the next layout. Selected and active rows that no longer
     * exist are dropped. Fixed-height lists keep no per-row state, so the count may be very large.
     *
     * @param itemCount non-negative row count
     * @throws IllegalArgumentException if the count is negative or too large for variable heights
     */
    public final void setItemCount(int itemCount) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("Item count must be non-negative.");
        }
        if (rowHeightProvider != null) {
            validateVariableRowCount(itemCount);
        }
        if (this.itemCount == itemCount) {
            return;
        }
        this.itemCount = itemCount;
        metricsDirty = true;
        if (activeIndex >= itemCount) {
            activeIndex = -1;
        }
        if (anchorIndex >= itemCount) {
            anchorIndex = -1;
        }
        LinkedHashSet<Integer> next = new LinkedHashSet<>();
        for (Integer index : selected) {
            if (index < itemCount) {
                next.add(index);
            }
        }
        applySelection(next);
        if (itemCount == 0) {
            scrollY = 0;
            hoveredIndex = -1;
        }
    }

    /**
     * Returns the fixed row height used when no height provider is assigned.
     *
     * @return the row height in logical UI units
     */
    public final float rowHeight() {
        return rowHeight;
    }

    /**
     * Sets a fixed row height and clears the variable height provider.
     *
     * @param rowHeight positive row height in logical UI units
     * @throws IllegalArgumentException if the height is not finite or not positive
     */
    public final void setRowHeight(float rowHeight) {
        if (!Float.isFinite(rowHeight) || rowHeight <= 0) {
            throw new IllegalArgumentException("Row height must be finite and positive.");
        }
        this.rowHeight = rowHeight;
        rowHeightProvider = null;
        invalidateRowHeights();
    }

    /**
     * Returns the variable row height provider.
     *
     * @return the provider, or null when a fixed height is used
     */
    public final IntToDoubleFunction rowHeightProvider() {
        return rowHeightProvider;
    }

    /**
     * Assigns a variable row height provider.
     * The provider is evaluated lazily once per row and cached in a prefix-sum table. Call
     * {@link #invalidateRowHeights()} after the underlying data changes.
     *
     * @param rowHeightProvider provider returning a positive height for an index
     * @throws NullPointerException if the provider is null
     * @throws IllegalArgumentException if the current row count is too large for variable heights
     */
    public final void setRowHeightProvider(IntToDoubleFunction rowHeightProvider) {
        validateVariableRowCount(itemCount);
        this.rowHeightProvider = Objects.requireNonNull(rowHeightProvider, "rowHeightProvider");
        invalidateRowHeights();
    }

    /**
     * Removes the variable height provider and restores the fixed row height.
     */
    public final void clearRowHeightProvider() {
        rowHeightProvider = null;
        invalidateRowHeights();
    }

    /**
     * Discards the cached prefix sums so the next query re-evaluates every row height.
     */
    public final void invalidateRowHeights() {
        metricsDirty = true;
    }

    /**
     * Returns the total height of all rows.
     * With a fixed row height the result is computed arithmetically without visiting rows. With
     * variable heights the first call after a data or height change evaluates the provider for every
     * row and stores a double-precision prefix-sum table.
     *
     * @return the content height in logical UI units, saturated at {@link Float#MAX_VALUE}
     * @throws IllegalArgumentException if the provider returns a non-positive or non-finite height,
     *                                  or the row count is too large for variable heights
     */
    public final float contentHeight() {
        ensureMetrics();
        return toFloat(contentHeight);
    }

    /**
     * Returns the row renderer.
     *
     * @return the renderer, or null when rows have no custom content
     */
    public final RowRenderer rowRenderer() {
        return rowRenderer;
    }

    /**
     * Assigns the renderer invoked for every visible row.
     *
     * @param rowRenderer row renderer, or null to draw only selection and hover feedback
     */
    public final void setRowRenderer(RowRenderer rowRenderer) {
        this.rowRenderer = rowRenderer;
    }

    /**
     * Returns the row selection behavior.
     *
     * @return the selection mode, never null
     */
    public final SelectionMode selectionMode() {
        return selectionMode;
    }

    /**
     * Sets the row selection behavior.
     * Switching to {@link SelectionMode#NONE} clears the selection. Switching to
     * {@link SelectionMode#SINGLE} keeps at most the most recently selected row.
     *
     * @param selectionMode selection mode
     * @throws NullPointerException if the mode is null
     */
    public final void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
        if (selectionMode == SelectionMode.NONE) {
            clearSelection();
            return;
        }
        if (selectionMode == SelectionMode.SINGLE && selected.size() > 1) {
            Integer last = null;
            for (Integer index : selected) {
                last = index;
            }
            LinkedHashSet<Integer> next = new LinkedHashSet<>();
            if (last != null) {
                next.add(last);
            }
            applySelection(next);
            anchorIndex = last == null ? -1 : last;
        }
    }

    /**
     * Returns the selected row indices in selection order.
     *
     * @return an immutable snapshot of the selected indices
     */
    public final List<Integer> selectedIndices() {
        return List.copyOf(selected);
    }

    /**
     * Reports whether a row is selected.
     *
     * @param index zero-based row index
     * @return true when the row is selected
     */
    public final boolean isSelected(int index) {
        return selected.contains(index);
    }

    /**
     * Replaces the selection with the supplied rows.
     * Out-of-range indices are ignored. In {@link SelectionMode#SINGLE} only the first valid index
     * is kept, and in {@link SelectionMode#NONE} the selection stays empty.
     *
     * @param indices row indices to select
     * @throws NullPointerException if the collection or one of its entries is null
     */
    public final void setSelectedIndices(Collection<Integer> indices) {
        Objects.requireNonNull(indices, "indices");
        LinkedHashSet<Integer> next = new LinkedHashSet<>();
        if (selectionMode != SelectionMode.NONE) {
            for (Integer index : indices) {
                Objects.requireNonNull(index, "index");
                if (index < 0 || index >= itemCount) {
                    continue;
                }
                next.add(index);
                if (selectionMode == SelectionMode.SINGLE) {
                    break;
                }
            }
        }
        applySelection(next);
        anchorIndex = next.isEmpty() ? -1 : next.iterator().next();
        if (!next.contains(activeIndex)) {
            activeIndex = anchorIndex;
        }
    }

    /**
     * Clears the selection and the anchor used by Shift selection.
     */
    public final void clearSelection() {
        applySelection(new LinkedHashSet<>());
        anchorIndex = -1;
    }

    /**
     * Returns the callback invoked after the selected rows change.
     *
     * @return the selection listener, or null when none is assigned
     */
    public final Consumer<List<Integer>> selectionListener() {
        return selectionListener;
    }

    /**
     * Assigns a callback invoked after the selected rows change.
     *
     * @param selectionListener listener receiving an immutable snapshot, or null to clear it
     */
    public final void setSelectionListener(Consumer<List<Integer>> selectionListener) {
        this.selectionListener = selectionListener;
    }

    /**
     * Returns the vertical scroll offset.
     *
     * @return the offset in logical UI units
     */
    public final float scrollY() {
        return scrollY;
    }

    /**
     * Sets the vertical scroll offset, clamped to the available content range.
     *
     * @param scrollY vertical offset in logical UI units
     * @throws IllegalArgumentException if the offset is not finite
     */
    public final void setScrollY(float scrollY) {
        if (!Float.isFinite(scrollY)) {
            throw new IllegalArgumentException("Scroll offset must be finite.");
        }
        this.scrollY = Math.clamp(scrollY, 0, maxScrollY());
        syncScrollbar();
        updateHoveredIndex();
    }

    /**
     * Scrolls the list by the supplied vertical offset.
     *
     * @param deltaY vertical offset change in logical UI units
     * @throws IllegalArgumentException if the resulting offset is not finite
     */
    public final void scrollBy(float deltaY) {
        setScrollY(scrollY + deltaY);
    }

    /**
     * Returns the largest valid vertical scroll offset.
     *
     * @return the maximum offset in logical UI units
     */
    public final float maxScrollY() {
        ensureMetrics();
        return toFloat(Math.max(0, contentHeight - localBounds().height()));
    }

    /**
     * Scrolls the viewport so the supplied row is fully visible.
     *
     * @param index zero-based row index
     * @throws IllegalArgumentException if the index is out of range
     */
    public final void scrollToRow(int index) {
        if (index < 0 || index >= itemCount) {
            throw new IllegalArgumentException("Row index is out of range.");
        }
        ensureMetrics();
        double top = rowTop(index);
        double bottom = rowBottom(index);
        float height = localBounds().height();
        if (top < scrollY) {
            setScrollY((float) top);
        } else if (bottom > scrollY + height) {
            setScrollY((float) (bottom - height));
        }
    }

    /**
     * Returns the row under a local vertical position.
     * The result is limited to rows inside the viewport, so positions in the empty space below a
     * short list return -1.
     *
     * @param localY vertical pointer position relative to the list top-left corner
     * @return the zero-based row index, or -1 outside the rows
     */
    public final int rowAt(float localY) {
        ensureMetrics();
        if (itemCount == 0 || localY < 0 || localY >= localBounds().height()) {
            return -1;
        }
        double position = (double) scrollY + localY;
        if (position < 0 || position >= contentHeight) {
            return -1;
        }
        int index = indexAt(position);
        return index >= 0 && index < itemCount ? index : -1;
    }

    /**
     * Measures the list with its current size.
     *
     * @param constraints available and required dimensions
     * @return the current size clamped to the constraints
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(bounds().width(), bounds().height());
    }

    /**
     * Clamps the scroll position and positions the scrollbar for the current size.
     */
    @Override
    protected void onLayout() {
        ensureMetrics();
        scrollY = Math.clamp(scrollY, 0, maxScrollY());
        float width = localBounds().width();
        float height = localBounds().height();
        boolean barVisible = maxScrollY() > 0;
        setVisibleIfChanged(verticalBar, barVisible);
        if (barVisible) {
            verticalBar.layout(new Rect(width - BAR_MARGIN - BAR_THICKNESS, BAR_MARGIN,
                    BAR_THICKNESS, Math.max(0, height - BAR_MARGIN * 2)));
            verticalBar.setVisibleFraction(contentHeight > 0
                    ? toFloat(height / contentHeight) : 1);
        }
        syncScrollbar();
    }

    /**
     * Paints the surface, the visible row backgrounds and the visible row content.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        if (itemCount == 0 || localBounds().isEmpty()) {
            return;
        }
        ensureMetrics();
        if (contentHeight <= 0) {
            return;
        }
        Theme theme = context().theme();
        WidgetStyle style = surfaceStyle();
        Color surface = style == null ? theme.surface() : style.background(WidgetState.NORMAL);
        Color hover = Color.mix(surface, theme.primary(), 0.16f);
        Color selection = Color.mix(surface, theme.primary(), 0.32f);
        float width = rowWidth();
        float height = localBounds().height();
        double limit = scrollY + height;
        int index = Math.min(indexAt(scrollY), itemCount);
        for (; index < itemCount && rowTop(index) < limit; index++) {
            float top = (float) (rowTop(index) - scrollY);
            float currentHeight = (float) (rowBottom(index) - rowTop(index));
            if (top + currentHeight <= 0 || top >= height) {
                continue;
            }
            boolean rowSelected = selected.contains(index);
            Rect rowBounds = new Rect(0, top, width, currentHeight);
            if (rowSelected) {
                renderer.rect(rowBounds, selection);
            } else if (index == hoveredIndex) {
                renderer.rect(rowBounds, hover);
            }
            if (index == activeIndex && isFocused()) {
                renderer.rect(new Rect(0, top, 2, currentHeight), theme.primary());
            }
            if (rowRenderer != null) {
                rowRenderer.render(index, rowBounds,
                        new RowState(rowSelected, index == hoveredIndex, index == activeIndex), renderer);
            }
        }
    }

    /**
     * Tracks the pointer position for hover feedback.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so pointer movement can bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        pointerX = x;
        pointerY = y;
        pointerInside = true;
        updateHoveredIndex();
        return false;
    }

    /**
     * Marks the pointer as inside for hover feedback.
     */
    @Override
    protected void onPointerEnter() {
        pointerInside = true;
        updateHoveredIndex();
    }

    /**
     * Clears hover feedback when the pointer leaves the list.
     */
    @Override
    protected void onPointerExit() {
        pointerInside = false;
        hoveredIndex = -1;
    }

    /**
     * Returns a pointer cursor over rows.
     *
     * @return the pointer cursor over a row, otherwise the default cursor
     */
    @Override
    public Cursor cursor() {
        return hoveredIndex >= 0 ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /**
     * Selects the pressed row or clears the selection when the press misses every row.
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
        int index = rowAt(y);
        activeIndex = index;
        if (index < 0) {
            if (!selected.isEmpty()) {
                clearSelection();
            }
            return true;
        }
        switch (selectionMode) {
            case NONE -> {
            }
            case SINGLE -> {
                selectOnly(index);
                anchorIndex = index;
            }
            case MULTIPLE -> {
                if (modifiers.control()) {
                    toggle(index);
                    anchorIndex = index;
                } else if (modifiers.shift()) {
                    extendTo(index);
                } else {
                    selectOnly(index);
                    anchorIndex = index;
                }
            }
            default -> {
            }
        }
        return true;
    }

    /**
     * Moves the active row or toggles it with the keyboard.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        return switch (key) {
            case UP -> moveActive(-1, modifiers);
            case DOWN -> moveActive(1, modifiers);
            case HOME -> moveActiveTo(0, modifiers);
            case END -> moveActiveTo(itemCount - 1, modifiers);
            case PAGE_UP -> movePage(-1, modifiers);
            case PAGE_DOWN -> movePage(1, modifiers);
            case SPACE -> toggleActive();
            default -> false;
        };
    }

    /**
     * Scrolls the list with the mouse wheel.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        float maximum = maxScrollY();
        if (maximum <= 0 || deltaY == 0) {
            return false;
        }
        float next = Math.clamp(scrollY - deltaY * WHEEL_STEP, 0, maximum);
        if (next == scrollY) {
            return false;
        }
        setScrollY(next);
        return true;
    }

    private void ensureMetrics() {
        if (!metricsDirty) {
            return;
        }
        if (rowHeightProvider == null) {
            prefix = null;
            contentHeight = (double) itemCount * rowHeight;
            metricsDirty = false;
            return;
        }
        validateVariableRowCount(itemCount);
        double[] next = new double[itemCount + 1];
        double total = 0;
        for (int index = 0; index < itemCount; index++) {
            next[index] = total;
            total += heightAt(index);
        }
        next[itemCount] = total;
        prefix = next;
        contentHeight = total;
        metricsDirty = false;
    }

    private double heightAt(int index) {
        double height = rowHeightProvider.applyAsDouble(index);
        if (!Double.isFinite(height) || height <= 0) {
            throw new IllegalArgumentException("Row height must be finite and positive.");
        }
        return height;
    }

    private double rowTop(int index) {
        return rowHeightProvider == null ? index * (double) rowHeight : prefix[index];
    }

    private double rowBottom(int index) {
        return rowHeightProvider == null ? (index + 1) * (double) rowHeight : prefix[index + 1];
    }

    private int indexAt(double position) {
        if (rowHeightProvider == null) {
            if (position <= 0 || rowHeight <= 0) {
                return 0;
            }
            double index = Math.floor(position / rowHeight);
            return index >= itemCount ? itemCount : (int) index;
        }
        int low = 0;
        int high = itemCount - 1;
        int result = itemCount;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (prefix[middle + 1] > position) {
                result = middle;
                high = middle - 1;
            } else {
                low = middle + 1;
            }
        }
        return result;
    }

    private static void validateVariableRowCount(int itemCount) {
        if (itemCount == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Row count is too large for variable heights.");
        }
    }

    private static float toFloat(double value) {
        return value >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) value;
    }

    private int firstVisibleIndex() {
        return Math.clamp(indexAt(scrollY), 0, Math.max(0, itemCount - 1));
    }

    private int lastVisibleIndex() {
        if (itemCount == 0) {
            return -1;
        }
        float position = scrollY + Math.max(0, localBounds().height() - 0.001f);
        return Math.clamp(indexAt(position), 0, itemCount - 1);
    }

    private boolean moveActive(int delta, Modifiers modifiers) {
        if (itemCount == 0) {
            return false;
        }
        int next;
        if (activeIndex < 0) {
            next = delta > 0 ? firstVisibleIndex() : lastVisibleIndex();
        } else {
            next = Math.clamp(activeIndex + delta, 0, itemCount - 1);
        }
        return activate(next, modifiers);
    }

    private boolean moveActiveTo(int index, Modifiers modifiers) {
        if (itemCount == 0) {
            return false;
        }
        return activate(Math.clamp(index, 0, itemCount - 1), modifiers);
    }

    private boolean movePage(int direction, Modifiers modifiers) {
        if (itemCount == 0) {
            return false;
        }
        if (activeIndex < 0) {
            return activate(direction > 0 ? firstVisibleIndex() : lastVisibleIndex(), modifiers);
        }
        int span = Math.max(1, lastVisibleIndex() - firstVisibleIndex() + 1);
        return moveActive(direction * span, modifiers);
    }

    private boolean activate(int index, Modifiers modifiers) {
        activeIndex = index;
        if (selectionMode == SelectionMode.SINGLE) {
            selectOnly(index);
            anchorIndex = index;
        } else if (selectionMode == SelectionMode.MULTIPLE) {
            if (modifiers.shift()) {
                extendTo(index);
            } else {
                selectOnly(index);
                anchorIndex = index;
            }
        }
        scrollToRow(index);
        return true;
    }

    private boolean toggleActive() {
        if (activeIndex < 0 || selectionMode == SelectionMode.NONE) {
            return false;
        }
        if (selectionMode == SelectionMode.SINGLE) {
            selectOnly(activeIndex);
        } else {
            toggle(activeIndex);
            anchorIndex = activeIndex;
        }
        return true;
    }

    private void selectOnly(int index) {
        LinkedHashSet<Integer> next = new LinkedHashSet<>();
        next.add(index);
        applySelection(next);
    }

    private void toggle(int index) {
        LinkedHashSet<Integer> next = new LinkedHashSet<>(selected);
        if (!next.remove(index)) {
            next.add(index);
        }
        applySelection(next);
    }

    private void extendTo(int index) {
        if (anchorIndex < 0 || anchorIndex >= itemCount) {
            anchorIndex = index;
        }
        int from = Math.min(anchorIndex, index);
        int to = Math.max(anchorIndex, index);
        LinkedHashSet<Integer> next = new LinkedHashSet<>();
        for (int current = from; current <= to; current++) {
            next.add(current);
        }
        applySelection(next);
        activeIndex = index;
    }

    private void applySelection(LinkedHashSet<Integer> next) {
        if (selected.equals(next)) {
            return;
        }
        selected.clear();
        selected.addAll(next);
        if (selectionListener != null) {
            selectionListener.accept(List.copyOf(selected));
        }
    }

    private void updateHoveredIndex() {
        hoveredIndex = pointerInside && pointerX >= 0 && pointerX < rowWidth()
                ? rowAt(pointerY) : -1;
    }

    private void syncScrollbar() {
        float maximum = maxScrollY();
        verticalBar.setValue(maximum > 0 ? scrollY / maximum : 0);
    }

    private float rowWidth() {
        return Math.max(0, localBounds().width()
                - (verticalBar.isVisible() ? BAR_THICKNESS + BAR_MARGIN : 0));
    }
}
