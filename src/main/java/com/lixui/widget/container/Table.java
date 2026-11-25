package com.lixui.widget.container;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
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
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A scrollable data table with sortable and resizable columns.
 * The header stays fixed while rows scroll vertically. Columns scroll horizontally as one unit, and
 * the last column fills the remaining viewport width so its right edge always matches the table
 * content edge when the columns fit. Header and row cells may show an icon before their text.
 * Rows can be selected with clicks, Ctrl and Shift modifiers, or a marquee rectangle.
 */
public class Table extends Panel {
    /**
     * The active sort direction of a table.
     */
    public enum SortOrder {
        /** Rows keep their insertion order. */
        NONE,
        /** Rows are sorted in ascending order. */
        ASCENDING,
        /** Rows are sorted in descending order. */
        DESCENDING
    }

    /**
     * The row selection behavior of a table.
     */
    public enum SelectionMode {
        /** Rows cannot be selected. */
        NONE,
        /** At most one row can be selected at a time. */
        SINGLE,
        /** Multiple rows can be selected with Ctrl and Shift. */
        MULTIPLE
    }

    private static final float BAR_THICKNESS = 8;
    private static final float DEFAULT_BAR_MARGIN = 4;
    private static final float DEFAULT_HEADER_HEIGHT = 32;
    private static final float DEFAULT_ROW_HEIGHT = 30;
    private static final float DEFAULT_COLUMN_WIDTH = 120;
    private static final float DEFAULT_MIN_COLUMN_WIDTH = 48;
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float DEFAULT_COLUMN_PADDING = 8;
    private static final float ICON_GAP = 6;
    private static final float WHEEL_STEP = 48;
    private static final float RESIZE_GRIP = 6;

    private final List<Column> columns = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private List<Row> sortedRows;
    private List<Object> sortedKeys;
    private final Set<Row> selectedRows = new LinkedHashSet<>();
    private final IconSet sortIcons = new IconSet("arrow-up-04.svg", "arrow-down-04.svg");
    private final ScrollBar verticalBar = new ScrollBar(ScrollBar.Orientation.VERTICAL);
    private final ScrollBar horizontalBar = new ScrollBar(ScrollBar.Orientation.HORIZONTAL);

    private float headerHeight = DEFAULT_HEADER_HEIGHT;
    private float rowHeight = DEFAULT_ROW_HEIGHT;
    private float minimumColumnWidth = DEFAULT_MIN_COLUMN_WIDTH;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float columnPadding = DEFAULT_COLUMN_PADDING;
    private float scrollBarMargin = DEFAULT_BAR_MARGIN;
    private boolean columnResizingEnabled = true;
    private boolean sortingEnabled = true;
    private boolean fillWidth;
    private SortOrder sortOrder = SortOrder.NONE;
    private int sortColumn = -1;
    private SelectionMode selectionMode = SelectionMode.NONE;
    private boolean dragSelectionEnabled = true;
    private boolean preserveSelectionOnPress;
    private Row selectionAnchor;
    private boolean selectionDragging;
    private float marqueeStartX;
    private float marqueeStartY;
    private float marqueeCurrentX;
    private float marqueeCurrentY;
    private Consumer<List<Row>> selectionListener;

    private float[] displayWidths = new float[0];
    private float viewportWidth;
    private float viewportHeight;
    private float totalWidth;
    private float rowsHeight;
    private float maximumScrollX;
    private float maximumScrollY;
    private float scrollX;
    private float scrollY;
    private boolean verticalBarVisible;
    private boolean horizontalBarVisible;

    private float pointerX;
    private float pointerY;
    private boolean pointerInside;
    private int resizeColumn = -1;
    private float resizeStartX;
    private float resizeStartWidth;
    private boolean suppressClick;

    /**
     * Creates an empty table with fixed header and scrollbars.
     */
    public Table() {
        setClipToBounds(true);
        verticalBar.setValueListener(value -> onScrollBarValue(true, value));
        horizontalBar.setValueListener(value -> onScrollBarValue(false, value));
        verticalBar.setVisible(false);
        horizontalBar.setVisible(false);
        add(verticalBar);
        add(horizontalBar);
    }

    /**
     * Returns the header height.
     *
     * @return the header height in logical UI units
     */
    public final float headerHeight() {
        return headerHeight;
    }

    /**
     * Sets the header height.
     *
     * @param headerHeight positive header height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public final void setHeaderHeight(float headerHeight) {
        if (!Float.isFinite(headerHeight) || headerHeight <= 0) {
            throw new IllegalArgumentException("Header height must be finite and positive.");
        }
        this.headerHeight = headerHeight;
    }

    /**
     * Returns the row height.
     *
     * @return the row height in logical UI units
     */
    public final float rowHeight() {
        return rowHeight;
    }

    /**
     * Sets the row height.
     *
     * @param rowHeight positive row height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public final void setRowHeight(float rowHeight) {
        if (!Float.isFinite(rowHeight) || rowHeight <= 0) {
            throw new IllegalArgumentException("Row height must be finite and positive.");
        }
        this.rowHeight = rowHeight;
    }

    /**
     * Returns the minimum width applied while resizing a column.
     *
     * @return the minimum column width in logical UI units
     */
    public final float minimumColumnWidth() {
        return minimumColumnWidth;
    }

    /**
     * Sets the minimum width applied while resizing a column.
     *
     * @param minimumColumnWidth positive minimum width in logical UI units
     * @throws IllegalArgumentException if the width is non-finite or not positive
     */
    public final void setMinimumColumnWidth(float minimumColumnWidth) {
        if (!Float.isFinite(minimumColumnWidth) || minimumColumnWidth <= 0) {
            throw new IllegalArgumentException("Minimum column width must be finite and positive.");
        }
        this.minimumColumnWidth = minimumColumnWidth;
    }

    /**
     * Returns the icon edge length used by headers and cells.
     *
     * @return the icon size in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the icon edge length used by headers and cells.
     *
     * @param iconSize positive icon size in logical UI units
     * @throws IllegalArgumentException if the size is non-finite or not positive
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the horizontal padding inside header and row cells.
     *
     * @return the cell padding in logical UI units
     */
    public final float columnPadding() {
        return columnPadding;
    }

    /**
     * Sets the horizontal padding inside header and row cells.
     *
     * @param columnPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is non-finite or negative
     */
    public final void setColumnPadding(float columnPadding) {
        if (!Float.isFinite(columnPadding) || columnPadding < 0) {
            throw new IllegalArgumentException("Column padding must be finite and non-negative.");
        }
        this.columnPadding = columnPadding;
    }

    /**
     * Returns the inset of the scrollbars from the table edges.
     *
     * @return the scrollbar inset in logical UI units
     */
    public final float scrollBarMargin() {
        return scrollBarMargin;
    }

    /**
     * Sets the inset of the scrollbars from the table edges.
     *
     * @param scrollBarMargin non-negative inset in logical UI units
     * @throws IllegalArgumentException if the margin is non-finite or negative
     */
    public final void setScrollBarMargin(float scrollBarMargin) {
        if (!Float.isFinite(scrollBarMargin) || scrollBarMargin < 0) {
            throw new IllegalArgumentException("Scrollbar margin must be finite and non-negative.");
        }
        this.scrollBarMargin = scrollBarMargin;
    }

    /**
     * Reports whether column widths can be changed by dragging header separators.
     *
     * @return true when column resizing is enabled
     */
    public final boolean isColumnResizingEnabled() {
        return columnResizingEnabled;
    }

    /**
     * Enables or disables column resizing by dragging header separators.
     *
     * @param columnResizingEnabled true to allow column resizing
     */
    public final void setColumnResizingEnabled(boolean columnResizingEnabled) {
        this.columnResizingEnabled = columnResizingEnabled;
    }

    /**
     * Reports whether the table expands to the full width offered by its measurement constraints.
     *
     * @return true when the table fills the available width
     */
    public final boolean isFillWidth() {
        return fillWidth;
    }

    /**
     * Enables or disables filling the available width during measurement.
     * Height still follows the header and row count.
     *
     * @param fillWidth true to stretch the table to the available width
     */
    public final void setFillWidth(boolean fillWidth) {
        this.fillWidth = fillWidth;
    }

    /**
     * Reports whether clicking a sortable header changes the row order.
     *
     * @return true when header sorting is enabled
     */
    public final boolean isSortingEnabled() {
        return sortingEnabled;
    }

    /**
     * Enables or disables header sorting.
     *
     * @param sortingEnabled true to allow header sorting
     */
    public final void setSortingEnabled(boolean sortingEnabled) {
        this.sortingEnabled = sortingEnabled;
        if (!sortingEnabled) {
            clearSort();
        }
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
     * @throws NullPointerException if {@code selectionMode} is null
     */
    public final void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
        if (selectionMode == SelectionMode.NONE) {
            clearSelection();
            return;
        }
        if (selectionMode == SelectionMode.SINGLE && selectedRows.size() > 1) {
            Row last = null;
            for (Row row : selectedRows) {
                last = row;
            }
            LinkedHashSet<Row> single = new LinkedHashSet<>();
            if (last != null) {
                single.add(last);
            }
            applySelection(single);
            selectionAnchor = last;
        }
    }

    /**
     * Reports whether dragging with the left button draws a marquee selection rectangle.
     *
     * @return true when marquee selection is enabled
     */
    public final boolean isDragSelectionEnabled() {
        return dragSelectionEnabled;
    }

    /**
     * Enables or disables marquee selection by dragging with the left button.
     * A translucent rectangle covers the dragged area and selects every row it intersects.
     * Marquee selection is only used in {@link SelectionMode#MULTIPLE}.
     *
     * @param dragSelectionEnabled true to allow marquee selection
     */
    public final void setDragSelectionEnabled(boolean dragSelectionEnabled) {
        this.dragSelectionEnabled = dragSelectionEnabled;
        if (!dragSelectionEnabled) {
            selectionDragging = false;
        }
    }

    /**
     * Returns the selected rows in selection order.
     *
     * @return an immutable snapshot of the selected rows
     */
    public final List<Row> selectedRows() {
        return List.copyOf(selectedRows);
    }

    /**
     * Reports whether a row is selected.
     *
     * @param row row to test
     * @return true when the row is selected
     * @throws NullPointerException if {@code row} is null
     */
    public final boolean isRowSelected(Row row) {
        return selectedRows.contains(Objects.requireNonNull(row, "row"));
    }

    /**
     * Replaces the selection with the supplied rows.
     * Rows that do not belong to this table are ignored. With {@link SelectionMode#SINGLE}, only the
     * first row is kept, and with {@link SelectionMode#NONE} the selection is cleared.
     *
     * @param rows rows to select
     * @throws NullPointerException if {@code rows} or one of its entries is null
     */
    public final void setSelectedRows(Collection<Row> rows) {
        Objects.requireNonNull(rows, "rows");
        LinkedHashSet<Row> next = new LinkedHashSet<>();
        for (Row row : rows) {
            Objects.requireNonNull(row, "row");
            if (indexOfRow(row) >= 0) {
                next.add(row);
                if (selectionMode == SelectionMode.SINGLE) {
                    break;
                }
            }
        }
        if (selectionMode == SelectionMode.NONE) {
            next.clear();
        }
        applySelection(next);
        selectionAnchor = next.isEmpty() ? null : next.iterator().next();
    }

    /**
     * Selects a single row and clears the previous selection.
     *
     * @param row row to select
     * @throws NullPointerException if {@code row} is null
     * @throws IllegalArgumentException if the row does not belong to this table
     */
    public final void selectRow(Row row) {
        Objects.requireNonNull(row, "row");
        if (indexOfRow(row) < 0) {
            throw new IllegalArgumentException("Row does not belong to this table.");
        }
        if (selectionMode == SelectionMode.NONE) {
            clearSelection();
            return;
        }
        LinkedHashSet<Row> next = new LinkedHashSet<>();
        next.add(row);
        applySelection(next);
        selectionAnchor = row;
    }

    /**
     * Clears the row selection and the anchor used by Shift selection.
     */
    public final void clearSelection() {
        applySelection(new LinkedHashSet<>());
        selectionAnchor = null;
        selectionDragging = false;
    }

    /**
     * Returns the callback invoked after the selected rows change.
     *
     * @return the selection listener, or null when none is assigned
     */
    public final Consumer<List<Row>> selectionListener() {
        return selectionListener;
    }

    /**
     * Assigns a callback invoked after the selected rows change.
     *
     * @param selectionListener listener receiving an immutable snapshot, or null to clear it
     */
    public final void setSelectionListener(Consumer<List<Row>> selectionListener) {
        this.selectionListener = selectionListener;
    }

    /**
     * Returns the columns in display order.
     *
     * @return an immutable snapshot of the columns
     */
    public final List<Column> columns() {
        return List.copyOf(columns);
    }

    /**
     * Returns the rows in insertion order.
     *
     * @return an immutable snapshot of the rows
     */
    public final List<Row> rows() {
        return List.copyOf(rows);
    }

    /**
     * Returns the active sort direction.
     *
     * @return the sort direction, never null
     */
    public final SortOrder sortOrder() {
        return sortOrder;
    }

    /**
     * Returns the sorted column index.
     *
     * @return the zero-based column index, or -1 when rows are unsorted
     */
    public final int sortColumn() {
        return sortColumn;
    }

    /**
     * Applies an explicit sort to a column.
     *
     * @param columnIndex zero-based column index
     * @param order sort direction
     * @throws IllegalArgumentException if the index is out of range or the order is {@link SortOrder#NONE}
     * @throws NullPointerException if {@code order} is null
     */
    public final void setSort(int columnIndex, SortOrder order) {
        Objects.requireNonNull(order, "order");
        if (columnIndex < 0 || columnIndex >= columns.size()) {
            throw new IllegalArgumentException("Column index is out of range.");
        }
        if (order == SortOrder.NONE) {
            throw new IllegalArgumentException("Use clearSort() to remove sorting.");
        }
        this.sortColumn = columnIndex;
        this.sortOrder = order;
        invalidateSortCache();
    }

    /**
     * Removes sorting and restores the insertion order.
     */
    public final void clearSort() {
        sortColumn = -1;
        sortOrder = SortOrder.NONE;
        invalidateSortCache();
    }

    /**
     * Returns the effective width of a column, including extra space assigned to the last column.
     *
     * @param columnIndex zero-based column index
     * @return the displayed width in logical UI units
     * @throws IllegalArgumentException if the index is out of range
     */
    public final float columnWidth(int columnIndex) {
        requireColumnIndex(columnIndex);
        computeLayout();
        return displayWidths[columnIndex];
    }

    /**
     * Returns the horizontal scroll offset.
     *
     * @return the offset in logical UI units
     */
    public final float scrollX() {
        return scrollX;
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
     * Sets both scroll offsets, clamped to the available content range.
     *
     * @param scrollX horizontal offset
     * @param scrollY vertical offset
     * @throws IllegalArgumentException if an offset is non-finite
     */
    public final void setScroll(float scrollX, float scrollY) {
        if (!Float.isFinite(scrollX) || !Float.isFinite(scrollY)) {
            throw new IllegalArgumentException("Scroll offsets must be finite.");
        }
        computeLayout();
        this.scrollX = Math.clamp(scrollX, 0, maximumScrollX);
        this.scrollY = Math.clamp(scrollY, 0, maximumScrollY);
        syncScrollbars();
    }

    /**
     * Scrolls the table by the supplied offsets.
     *
     * @param deltaX horizontal offset change
     * @param deltaY vertical offset change
     * @throws IllegalArgumentException if an offset or resulting position is non-finite
     */
    public final void scrollBy(float deltaX, float deltaY) {
        setScroll(scrollX + deltaX, scrollY + deltaY);
    }

    /**
     * Returns rows in display order after applying the active sort.
     *
     * @return an immutable snapshot of the displayed rows
     */
    public final List<Row> displayedRows() {
        return List.copyOf(rowsInDisplayOrder());
    }

    // The default order is cached until structure changes or an immutable key snapshot no longer matches.
    private List<Row> rowsInDisplayOrder() {
        if (sortOrder == SortOrder.NONE || sortColumn < 0 || sortColumn >= columns.size()) {
            return rows;
        }
        if (columns.get(sortColumn).comparator() != null) {
            invalidateSortCache();
            return sortRows();
        }
        if (sortedRows != null && sortKeysMatch()) {
            return sortedRows;
        }
        invalidateSortCache();
        List<Object> keys = immutableSortKeys();
        if (keys == null) {
            return sortRows();
        }
        sortedRows = sortRows();
        sortedKeys = keys;
        return sortedRows;
    }

    private boolean sortKeysMatch() {
        if (sortedKeys == null || sortedKeys.size() != rows.size()) {
            return false;
        }
        for (int index = 0; index < rows.size(); index++) {
            if (!Objects.equals(sortKey(rows.get(index)), sortedKeys.get(index))) {
                return false;
            }
        }
        return true;
    }

    private List<Object> immutableSortKeys() {
        List<Object> keys = new ArrayList<>(rows.size());
        for (Row row : rows) {
            Object key = sortKey(row);
            if (!(key instanceof String || key instanceof Boolean || key instanceof Character
                    || key instanceof Byte || key instanceof Short || key instanceof Integer
                    || key instanceof Long || key instanceof Float || key instanceof Double
                    || key instanceof Enum<?>)) {
                return null;
            }
            keys.add(key);
        }
        return keys;
    }

    private Object sortKey(Row row) {
        Cell cell = row.cell(sortColumn);
        return cell == null ? "" : cell.sortValue();
    }

    private List<Row> sortRows() {
        List<Row> sorted = new ArrayList<>(rows);
        if (sortOrder == SortOrder.DESCENDING) {
            sorted.sort((first, second) -> compareRows(second, first));
        } else {
            sorted.sort(this::compareRows);
        }
        return sorted;
    }

    private void invalidateSortCache() {
        sortedRows = null;
        sortedKeys = null;
    }

    /**
     * Returns the displayed row under a local vertical position.
     *
     * @param localY vertical pointer position relative to the table
     * @return the zero-based display row index, or -1 outside the row viewport
     */
    public final int rowAt(float localY) {
        computeLayout();
        return rowIndexAt(localY);
    }

    /**
     * Appends a column with the default width.
     *
     * @param title column title
     * @return the added column
     * @throws NullPointerException if {@code title} is null
     */
    public final Column addColumn(String title) {
        return addColumn(new Column(title, DEFAULT_COLUMN_WIDTH));
    }

    /**
     * Appends a column with an explicit width.
     *
     * @param title column title
     * @param width positive initial width in logical UI units
     * @return the added column
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the width is non-finite or not positive
     */
    public final Column addColumn(String title, float width) {
        return addColumn(new Column(title, width));
    }

    /**
     * Appends an existing column.
     *
     * @param column column to append
     * @return the added column
     * @throws NullPointerException if {@code column} is null
     */
    public final Column addColumn(Column column) {
        Column added = Objects.requireNonNull(column, "column");
        columns.add(added);
        invalidateSortCache();
        return added;
    }

    /**
     * Removes a column by reference.
     *
     * @param column column to remove
     * @return true when the column was present
     * @throws NullPointerException if {@code column} is null
     */
    public final boolean removeColumn(Column column) {
        Objects.requireNonNull(column, "column");
        int index = indexOfColumn(column);
        if (index < 0) {
            return false;
        }
        columns.remove(index);
        if (sortColumn == index) {
            clearSort();
        } else if (sortColumn > index) {
            sortColumn--;
        }
        invalidateSortCache();
        return true;
    }

    /**
     * Appends a row.
     *
     * @param row row to append
     * @return the added row
     * @throws NullPointerException if {@code row} is null
     */
    public final Row addRow(Row row) {
        Row added = Objects.requireNonNull(row, "row");
        rows.add(added);
        invalidateSortCache();
        return added;
    }

    /**
     * Appends a row with text-only cells.
     *
     * @param texts cell texts in column order
     * @return the added row
     * @throws NullPointerException if {@code texts} or one of its entries is null
     */
    public final Row addRow(String... texts) {
        return addRow(Row.of(texts));
    }

    /**
     * Removes a row by reference.
     *
     * @param row row to remove
     * @return true when the row was present
     * @throws NullPointerException if {@code row} is null
     */
    public final boolean removeRow(Row row) {
        Objects.requireNonNull(row, "row");
        int index = indexOfRow(row);
        if (index < 0) {
            return false;
        }
        rows.remove(index);
        invalidateSortCache();
        if (selectedRows.remove(row)) {
            if (selectionAnchor == row) {
                selectionAnchor = null;
            }
            notifySelectionChanged();
        }
        return true;
    }

    /**
     * Removes every row and resets the vertical scroll position.
     */
    public final void clearRows() {
        rows.clear();
        invalidateSortCache();
        scrollY = 0;
        clearSelection();
    }

    void setPreserveSelectionOnPress(boolean preserveSelectionOnPress) {
        this.preserveSelectionOnPress = preserveSelectionOnPress;
    }

    final float rowViewportWidth() {
        computeLayout();
        return viewportWidth;
    }

    final float rowViewportHeight() {
        computeLayout();
        return viewportHeight;
    }

    final void replaceRows(List<Row> newRows) {
        Objects.requireNonNull(newRows, "newRows");
        rows.clear();
        rows.addAll(newRows);
        invalidateSortCache();
    }

    /**
     * Measures the full table content without scrollbars.
     *
     * @param constraints available and required dimensions
     * @return the natural table size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float width = fillWidth ? constraints.maxWidth() : configuredWidth();
        float height = headerHeight + rows.size() * rowHeight;
        return new Size(Math.min(width, constraints.maxWidth()),
                Math.min(height, constraints.maxHeight()));
    }

    /**
     * Computes column metrics and positions the scrollbars.
     */
    @Override
    protected void onLayout() {
        computeLayout();
        float width = localBounds().width();
        float height = localBounds().height();
        setVisibleIfChanged(verticalBar, verticalBarVisible);
        setVisibleIfChanged(horizontalBar, horizontalBarVisible);
        if (verticalBarVisible) {
            verticalBar.layout(new Rect(
                    width - scrollBarMargin - BAR_THICKNESS,
                    headerHeight + scrollBarMargin,
                    BAR_THICKNESS,
                    Math.max(0, viewportHeight - scrollBarMargin * 2)));
            verticalBar.setVisibleFraction(rowsHeight > 0 ? viewportHeight / rowsHeight : 1);
        }
        if (horizontalBarVisible) {
            horizontalBar.layout(new Rect(
                    scrollBarMargin,
                    height - scrollBarMargin - BAR_THICKNESS,
                    Math.max(0, viewportWidth - scrollBarMargin * 2),
                    BAR_THICKNESS));
            horizontalBar.setVisibleFraction(totalWidth > 0 ? viewportWidth / totalWidth : 1);
        }
        syncScrollbars();
    }

    /**
     * Paints the table surface, header and visible rows.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        WidgetStyle style = resolvedStyle();
        if (style == null) {
            return;
        }
        WidgetState state = isEnabled() ? WidgetState.NORMAL : WidgetState.DISABLED;
        renderer.roundedRect(localBounds(), style.cornerRadius(), style.background(state));
        computeLayout();
        if (localBounds().isEmpty() || viewportWidth <= 0) {
            drawBorder(renderer, style, state);
            return;
        }
        Theme theme = context().theme();
        Color textColor = style.text(state);
        Color headerBackground = Color.mix(style.background(state), theme.primary(), 0.08f);
        Color gridColor = theme.border().withAlpha(0.55f);
        Point origin = absolutePosition();
        drawHeader(renderer, origin, theme, textColor, headerBackground, gridColor);
        drawRows(renderer, origin, theme, textColor, gridColor);
        drawBorder(renderer, style, state);
        drawMarquee(renderer, theme);
    }

    private void drawMarquee(RenderManager renderer, Theme theme) {
        if (!selectionDragging) {
            return;
        }
        Rect marquee = marqueeBounds();
        if (marquee.isEmpty()) {
            return;
        }
        renderer.rect(marquee, theme.primary().withAlpha(0.16f));
        renderer.strokeRect(marquee, 1, Paint.solid(theme.primary().withAlpha(0.9f)));
    }

    private WidgetStyle resolvedStyle() {
        if (hasLocalStyle()) {
            return style();
        }
        return context() == null ? null : context().theme().panelStyle();
    }

    private void drawBorder(RenderManager renderer, WidgetStyle style, WidgetState state) {
        float width = style.borderWidth();
        if (width <= 0) {
            return;
        }
        float inset = width / 2;
        Rect bounds = localBounds();
        Rect stroke = new Rect(bounds.x() + inset, bounds.y() + inset,
                Math.max(0, bounds.width() - width), Math.max(0, bounds.height() - width));
        if (stroke.isEmpty()) {
            return;
        }
        renderer.strokeRoundedRect(stroke, Math.max(0, style.cornerRadius() - inset), width,
                Paint.solid(style.border(state)));
    }

    private void drawHeader(RenderManager renderer, Point origin, Theme theme, Color textColor,
                            Color background, Color gridColor) {
        renderer.rect(new Rect(0, 0, localBounds().width(), headerHeight), background);
        Rect clip = new Rect(origin.x(), origin.y(), viewportWidth, headerHeight);
        renderer.withClip(clip, () -> drawHeaderCells(renderer, theme, textColor, gridColor));
        renderer.rect(new Rect(0, headerHeight - 1, localBounds().width(), 1), gridColor);
    }

    private void drawHeaderCells(RenderManager renderer, Theme theme, Color textColor, Color gridColor) {
        Font font = font();
        ensureSortIcons();
        Texture sortIcon = sortIcon();
        int hoveredHandle = resizeHandleAt(pointerX);
        float x = -scrollX;
        for (int index = 0; index < columns.size(); index++) {
            float width = displayWidths[index];
            if (x + width < 0) {
                x += width;
                continue;
            }
            if (x >= viewportWidth) {
                break;
            }
            Column column = columns.get(index);
            boolean sorted = sortIcon != null && sortColumn == index;
            Color color = sorted ? theme.primary() : textColor;
            float contentLeft = x + columnPadding;
            float contentRight = x + width - columnPadding;
            if (column.icon() != null) {
                float iconY = (headerHeight - iconSize) / 2;
                renderer.icon(column.icon(), new Rect(contentLeft, iconY, iconSize, iconSize),
                        ImageStyle.ICON, color);
                contentLeft += iconSize + ICON_GAP;
            }
            float textLimit = Math.max(0,
                    contentRight - contentLeft - (sorted ? iconSize + ICON_GAP : 0));
            float measured = font == null ? 0 : font.measure(column.title()).width();
            float textWidth = Math.min(measured, textLimit);
            if (font != null && textWidth > 0) {
                renderer.text(font, column.title(), contentLeft, headerHeight / 2, color,
                        cellTextStyle(textLimit));
            }
            if (sorted) {
                float iconX = Math.min(contentRight - iconSize,
                        contentLeft + textWidth + ICON_GAP);
                float iconY = (headerHeight - iconSize) / 2;
                renderer.icon(sortIcon, new Rect(iconX, iconY, iconSize, iconSize),
                        ImageStyle.ICON, color);
            }
            if (index < columns.size() - 1) {
                drawResizeGrip(renderer, theme, gridColor, index, x + width, hoveredHandle);
            }
            x += width;
        }
    }

    private void drawResizeGrip(RenderManager renderer, Theme theme, Color gridColor,
                                int index, float edge, int hoveredHandle) {
        if (index == hoveredHandle || index == resizeColumn) {
            renderer.rect(new Rect(edge - RESIZE_GRIP / 2, headerHeight * 0.12f,
                    RESIZE_GRIP, headerHeight * 0.76f), theme.primary().withAlpha(0.18f));
            renderer.rect(new Rect(edge - 1, headerHeight * 0.18f,
                    2, headerHeight * 0.64f), theme.primary());
            return;
        }
        renderer.rect(new Rect(edge - 0.5f, headerHeight * 0.25f,
                1, headerHeight * 0.5f), gridColor);
    }

    private void drawRows(RenderManager renderer, Point origin, Theme theme,
                          Color textColor, Color gridColor) {
        if (viewportHeight <= 0 || rows.isEmpty()) {
            return;
        }
        WidgetStyle style = resolvedStyle();
        Color background = style.background(isEnabled() ? WidgetState.NORMAL : WidgetState.DISABLED);
        Color alternate = Color.mix(background, theme.background(), 0.18f);
        Color hoverBackground = Color.mix(background, theme.primary(), 0.24f);
        Color selected = Color.mix(background, theme.primary(), 0.32f);
        Color selectedHover = Color.mix(background, theme.primary(), 0.42f);
        Rect clip = new Rect(origin.x(), origin.y() + headerHeight, viewportWidth, viewportHeight);
        renderer.withClip(clip, () -> {
            List<Row> visible = rowsInDisplayOrder();
            int hovered = hoveredRow();
            int first = Math.clamp((int) Math.floor(scrollY / rowHeight), 0,
                    Math.max(0, visible.size() - 1));
            float y = headerHeight - scrollY + first * rowHeight;
            for (int index = first; index < visible.size(); index++) {
                if (y >= headerHeight + viewportHeight) {
                    break;
                }
                if (y + rowHeight > headerHeight) {
                    Row row = visible.get(index);
                    boolean isSelected = selectedRows.contains(row);
                    Color rowColor;
                    if (isSelected) {
                        rowColor = index == hovered ? selectedHover : selected;
                    } else {
                        rowColor = index == hovered ? hoverBackground
                                : (index % 2 == 0 ? background : alternate);
                    }
                    renderer.rect(new Rect(0, y, viewportWidth, rowHeight), rowColor);
                    if (isSelected) {
                        renderer.rect(new Rect(0, y, 3, rowHeight), theme.primary());
                    } else if (index == hovered) {
                        renderer.rect(new Rect(0, y, 2, rowHeight),
                                theme.primary().withAlpha(0.7f));
                    }
                    drawRowCells(renderer, row, y, textColor, gridColor);
                }
                y += rowHeight;
            }
        });
    }

    private void drawRowCells(RenderManager renderer, Row row, float y,
                              Color textColor, Color gridColor) {
        Font font = font();
        float x = -scrollX;
        for (int index = 0; index < columns.size(); index++) {
            float width = displayWidths[index];
            if (x + width < 0) {
                x += width;
                continue;
            }
            if (x >= viewportWidth) {
                break;
            }
            Cell cell = row.cell(index);
            if (cell != null) {
                float contentLeft = x + columnPadding;
                float contentRight = x + width - columnPadding;
                if (cell.icon() != null) {
                    float iconY = y + (rowHeight - iconSize) / 2;
                    renderer.icon(cell.icon(), new Rect(contentLeft, iconY, iconSize, iconSize),
                            ImageStyle.ICON, textColor);
                    contentLeft += iconSize + ICON_GAP;
                }
                float textWidth = Math.max(0, contentRight - contentLeft);
                if (font != null && textWidth > 0 && !cell.text().isEmpty()) {
                    renderer.text(font, cell.text(), contentLeft, y + rowHeight / 2, textColor,
                            cellTextStyle(textWidth));
                }
            }
            if (index < columns.size() - 1) {
                renderer.rect(new Rect(x + width - 0.5f, y, 1, rowHeight), gridColor);
            }
            x += width;
        }
    }

    private static TextStyle cellTextStyle(float width) {
        return new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    }

    private void computeLayout() {
        float width = Math.max(0, localBounds().width());
        float height = Math.max(0, localBounds().height());
        float visibleHeader = Math.min(headerHeight, height);
        float availableRowsHeight = Math.max(0, height - visibleHeader);
        rowsHeight = rows.size() * rowHeight;
        float configuredWidth = configuredWidth();
        boolean needVertical = rowsHeight > availableRowsHeight;
        boolean needHorizontal = configuredWidth > width;
        for (int iteration = 0; iteration < 4; iteration++) {
            float nextWidth = Math.max(0, width - (needVertical ? barSpace() : 0));
            float nextHeight = Math.max(0,
                    availableRowsHeight - (needHorizontal ? barSpace() : 0));
            boolean nextVertical = rowsHeight > nextHeight;
            boolean nextHorizontal = configuredWidth > nextWidth;
            if (nextVertical == needVertical && nextHorizontal == needHorizontal) {
                break;
            }
            needVertical = nextVertical;
            needHorizontal = nextHorizontal;
        }
        viewportWidth = Math.max(0, width - (needVertical ? barSpace() : 0));
        viewportHeight = Math.max(0, availableRowsHeight - (needHorizontal ? barSpace() : 0));
        verticalBarVisible = rowsHeight > viewportHeight;
        horizontalBarVisible = configuredWidth > viewportWidth;
        maximumScrollY = Math.max(0, rowsHeight - viewportHeight);
        scrollY = Math.clamp(scrollY, 0, maximumScrollY);

        displayWidths = new float[columns.size()];
        float sum = 0;
        for (int index = 0; index < columns.size(); index++) {
            displayWidths[index] = columns.get(index).width();
            sum += displayWidths[index];
        }
        if (sum < viewportWidth && displayWidths.length > 0) {
            displayWidths[displayWidths.length - 1] += viewportWidth - sum;
            sum = viewportWidth;
        }
        totalWidth = sum;
        maximumScrollX = Math.max(0, totalWidth - viewportWidth);
        scrollX = Math.clamp(scrollX, 0, maximumScrollX);
    }

    private float configuredWidth() {
        float width = 0;
        for (Column column : columns) {
            width += column.width();
        }
        return width;
    }

    private float barSpace() {
        return BAR_THICKNESS + scrollBarMargin;
    }

    private void onScrollBarValue(boolean vertical, float value) {
        if (vertical) {
            setScroll(scrollX, value * maximumScrollY);
        } else {
            setScroll(value * maximumScrollX, scrollY);
        }
    }

    private void syncScrollbars() {
        verticalBar.setValue(maximumScrollY > 0 ? scrollY / maximumScrollY : 0);
        horizontalBar.setValue(maximumScrollX > 0 ? scrollX / maximumScrollX : 0);
    }

    private Texture sortIcon() {
        if (sortOrder == SortOrder.NONE || sortColumn < 0) {
            return null;
        }
        return sortIcons.get(sortOrder == SortOrder.ASCENDING ? 0 : 1);
    }

    private void ensureSortIcons() {
        if (sortOrder == SortOrder.NONE) {
            return;
        }
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(iconSize * context.density()));
        if (sortIcons.matches(context, size)) {
            return;
        }
        sortIcons.reload(context, size);
    }

    private void releaseSortIcons() {
        sortIcons.release(context());
    }

    /**
     * Releases the shared sort arrow icons when the table leaves the tree.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        releaseSortIcons();
    }

    /**
     * Drops cached sort icons so they are reloaded at the new density.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        releaseSortIcons();
    }

    /**
     * Returns the pointer cursor over sortable headers and column separators.
     *
     * @return the requested cursor shape
     */
    @Override
    public Cursor cursor() {
        if (!pointerInside) {
            return Cursor.DEFAULT;
        }
        computeLayout();
        if (resizeHandleAt(pointerX) >= 0) {
            return Cursor.RESIZE_HORIZONTAL;
        }
        if (pointerY >= 0 && pointerY <= headerHeight && pointerX >= 0
                && pointerX <= viewportWidth && sortableColumnAt(pointerX) >= 0) {
            return Cursor.POINTER;
        }
        if (selectionMode != SelectionMode.NONE && pointerX >= 0 && pointerX <= viewportWidth
                && rowIndexAt(pointerY) >= 0) {
            return Cursor.POINTER;
        }
        return Cursor.DEFAULT;
    }

    /**
     * Tracks the pointer for header hits and row highlighting.
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
        return false;
    }

    /**
     * Marks the pointer as inside for cursor and hover feedback.
     */
    @Override
    protected void onPointerEnter() {
        pointerInside = true;
    }

    /**
     * Clears hover feedback when the pointer leaves the table.
     */
    @Override
    protected void onPointerExit() {
        pointerInside = false;
    }

    /**
     * Starts a header resize drag or keeps the press for click handling.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when a resize drag was started
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        suppressClick = false;
        pointerX = x;
        pointerY = y;
        pointerInside = true;
        if (button != MouseButton.LEFT) {
            return false;
        }
        computeLayout();
        int handle = resizeHandleAt(x);
        if (handle >= 0) {
            resizeColumn = handle;
            resizeStartX = x;
            resizeStartWidth = displayWidths[handle];
            capturePointer(button);
            suppressClick = true;
            return true;
        }
        if (selectionMode == SelectionMode.NONE || y < headerHeight) {
            return false;
        }
        int rowIndex = rowIndexAt(y);
        if (rowIndex < 0) {
            clearSelection();
            return true;
        }
        Row row = rowsInDisplayOrder().get(rowIndex);
        selectionDragging = false;
        if (selectionMode == SelectionMode.MULTIPLE && modifiers.control()) {
            toggleSelection(row);
            selectionAnchor = row;
        } else if (selectionMode == SelectionMode.MULTIPLE && modifiers.shift()) {
            extendSelectionTo(row);
        } else if (preserveSelectionOnPress && selectionMode == SelectionMode.MULTIPLE
                && selectedRows.contains(row)) {
            selectionAnchor = row;
        } else {
            selectOnly(row);
            selectionAnchor = row;
            if (selectionMode == SelectionMode.MULTIPLE && dragSelectionEnabled) {
                selectionDragging = true;
                marqueeStartX = x;
                marqueeStartY = y;
                marqueeCurrentX = x;
                marqueeCurrentY = y;
            }
        }
        return true;
    }

    /**
     * Updates the resized column width while the header separator is dragged.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button dragged button
     * @param modifiers active modifiers
     * @return true when the drag was consumed
     */
    @Override
    protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        if (resizeColumn >= 0) {
            computeLayout();
            float width = resizeStartWidth + (x - resizeStartX);
            float minimum = Math.max(minimumColumnWidth, columns.get(resizeColumn).minimumWidth());
            float maximum = Math.max(minimum, maximumResizeWidth(resizeColumn));
            columns.get(resizeColumn).setWidth(Math.clamp(width, minimum, maximum));
            computeLayout();
            syncScrollbars();
            return true;
        }
        if (!selectionDragging) {
            return false;
        }
        computeLayout();
        marqueeCurrentX = x;
        marqueeCurrentY = y;
        applyMarqueeSelection();
        return true;
    }

    /**
     * Ends a column resize drag.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return false so the release can bubble
     */
    @Override
    protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT) {
            resizeColumn = -1;
            selectionDragging = false;
        }
        return false;
    }

    /**
     * Cancels an unfinished column resize or marquee drag when its capture is cancelled.
     *
     * @param button button whose capture was cancelled
     */
    @Override
    protected void onPointerCaptureCancelled(MouseButton button) {
        if (button == MouseButton.LEFT) {
            resizeColumn = -1;
            selectionDragging = false;
        }
    }

    /**
     * Finishes a resize press and clears the drag target.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return false so the release can bubble
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT) {
            resizeColumn = -1;
            selectionDragging = false;
        }
        return false;
    }

    /**
     * Sorts rows or clears the sort when a header cell is clicked.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the header consumed the click
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return handleHeaderClick(x, y, button);
    }

    /**
     * Advances the sort when a header cell is double-clicked.
     * Double clicks do not produce a regular click, so sorting handles both gestures.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button double-clicked button
     * @param modifiers active modifiers
     * @return true when the header consumed the double click
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return handleHeaderClick(x, y, button);
    }

    private boolean handleHeaderClick(float x, float y, MouseButton button) {
        if (suppressClick) {
            suppressClick = false;
            return false;
        }
        if (button != MouseButton.LEFT) {
            return false;
        }
        computeLayout();
        if (y < 0 || y > headerHeight || x < 0 || x > viewportWidth) {
            return false;
        }
        int index = sortableColumnAt(x);
        if (index < 0) {
            return false;
        }
        cycleSort(index);
        return true;
    }

    /**
     * Scrolls rows and columns with the mouse wheel.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        computeLayout();
        float nextX = scrollX;
        float nextY = scrollY;
        if (deltaY != 0) {
            if (maximumScrollY > 0) {
                nextY -= deltaY * WHEEL_STEP;
            } else if (maximumScrollX > 0) {
                nextX -= deltaY * WHEEL_STEP;
            }
        }
        if (deltaX != 0 && maximumScrollX > 0) {
            nextX += deltaX * WHEEL_STEP;
        }
        if (nextX == scrollX && nextY == scrollY) {
            return false;
        }
        setScroll(nextX, nextY);
        return true;
    }

    private void cycleSort(int index) {
        if (!sortingEnabled || !columns.get(index).isSortable()) {
            return;
        }
        if (sortColumn != index || sortOrder == SortOrder.NONE) {
            setSort(index, SortOrder.ASCENDING);
        } else if (sortOrder == SortOrder.ASCENDING) {
            setSort(index, SortOrder.DESCENDING);
        } else {
            clearSort();
        }
    }

    private int compareRows(Row first, Row second) {
        Column column = columns.get(sortColumn);
        Comparator<Row> comparator = column.comparator();
        if (comparator != null) {
            return comparator.compare(first, second);
        }
        return compareValues(sortKey(first), sortKey(second));
    }

    @SuppressWarnings("unchecked")
    private static int compareValues(Object first, Object second) {
        if (first instanceof Number firstNumber && second instanceof Number secondNumber) {
            return Double.compare(firstNumber.doubleValue(), secondNumber.doubleValue());
        }
        if (first instanceof String firstText && second instanceof String secondText) {
            return firstText.compareToIgnoreCase(secondText);
        }
        if (first instanceof Comparable<?> comparable) {
            try {
                return ((Comparable<Object>) comparable).compareTo(second);
            } catch (ClassCastException ignored) {
                // Fall through to textual comparison for values of different types.
            }
        }
        return String.valueOf(first).compareToIgnoreCase(String.valueOf(second));
    }

    private int hoveredRow() {
        return pointerInsideTable() ? rowIndexAt(pointerY) : -1;
    }

    /**
     * Reports whether the pointer is over this table or one of its children.
     * The cached flag alone is not enough because a child can take the hover without the table
     * receiving the matching exit when the pointer leaves the subtree.
     *
     * @return true when the currently hovered widget belongs to this table
     */
    private boolean pointerInsideTable() {
        if (!pointerInside) {
            return false;
        }
        UiContext context = context();
        if (context == null) {
            return true;
        }
        for (Widget widget = context.hovered(); widget != null; widget = widget.parent()) {
            if (widget == this) {
                return true;
            }
        }
        return false;
    }

    private int rowIndexAt(float y) {
        if (y <= headerHeight || y >= headerHeight + viewportHeight) {
            return -1;
        }
        int index = (int) Math.floor((y - headerHeight + scrollY) / rowHeight);
        return index >= 0 && index < rows.size() ? index : -1;
    }

    private void selectOnly(Row row) {
        LinkedHashSet<Row> next = new LinkedHashSet<>();
        next.add(row);
        applySelection(next);
    }

    private void toggleSelection(Row row) {
        LinkedHashSet<Row> next = new LinkedHashSet<>(selectedRows);
        if (!next.remove(row)) {
            next.add(row);
        }
        applySelection(next);
    }

    private void extendSelectionTo(Row row) {
        List<Row> displayed = rowsInDisplayOrder();
        int target = indexOfRow(displayed, row);
        if (target < 0) {
            return;
        }
        int anchor = selectionAnchor == null ? -1 : indexOfRow(displayed, selectionAnchor);
        if (anchor < 0) {
            anchor = target;
            selectionAnchor = row;
        }
        int from = Math.min(anchor, target);
        int to = Math.max(anchor, target);
        LinkedHashSet<Row> next = new LinkedHashSet<>();
        for (int index = from; index <= to; index++) {
            next.add(displayed.get(index));
        }
        applySelection(next);
    }

    private Rect marqueeBounds() {
        float left = Math.max(0, Math.min(marqueeStartX, marqueeCurrentX));
        float right = Math.min(viewportWidth, Math.max(marqueeStartX, marqueeCurrentX));
        float top = Math.max(headerHeight, Math.min(marqueeStartY, marqueeCurrentY));
        float bottom = Math.min(headerHeight + viewportHeight,
                Math.max(marqueeStartY, marqueeCurrentY));
        if (right <= left || bottom <= top) {
            return Rect.EMPTY;
        }
        return new Rect(left, top, right - left, bottom - top);
    }

    private void applyMarqueeSelection() {
        if (rows.isEmpty()) {
            applySelection(new LinkedHashSet<>());
            return;
        }
        float top = Math.max(headerHeight, Math.min(marqueeStartY, marqueeCurrentY));
        float bottom = Math.min(headerHeight + viewportHeight,
                Math.max(marqueeStartY, marqueeCurrentY));
        if (bottom <= top) {
            return;
        }
        float topOffset = top - headerHeight + scrollY;
        float bottomOffset = bottom - headerHeight + scrollY;
        int first = (int) Math.floor(topOffset / rowHeight);
        int last = (int) Math.ceil(bottomOffset / rowHeight) - 1;
        first = Math.clamp(first, 0, rows.size() - 1);
        last = Math.clamp(last, 0, rows.size() - 1);
        if (last < first) {
            applySelection(new LinkedHashSet<>());
            return;
        }
        List<Row> displayed = rowsInDisplayOrder();
        LinkedHashSet<Row> next = new LinkedHashSet<>();
        for (int index = first; index <= last; index++) {
            next.add(displayed.get(index));
        }
        applySelection(next);
    }

    private void applySelection(Collection<Row> selection) {
        LinkedHashSet<Row> next = new LinkedHashSet<>(selection);
        if (next.equals(selectedRows)) {
            return;
        }
        selectedRows.clear();
        selectedRows.addAll(next);
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.accept(List.copyOf(selectedRows));
        }
    }

    private static int indexOfRow(List<Row> rows, Row row) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index) == row) {
                return index;
            }
        }
        return -1;
    }

    private int sortableColumnAt(float x) {
        if (!sortingEnabled) {
            return -1;
        }
        int index = columnAt(x);
        return index >= 0 && columns.get(index).isSortable() ? index : -1;
    }

    private int columnAt(float x) {
        float position = x + scrollX;
        if (position < 0) {
            return -1;
        }
        for (int index = 0; index < displayWidths.length; index++) {
            if (position < displayWidths[index]) {
                return index;
            }
            position -= displayWidths[index];
        }
        return -1;
    }

    private int resizeHandleAt(float x) {
        if (!columnResizingEnabled || columns.size() < 2 || x < 0 || x > viewportWidth) {
            return -1;
        }
        if (pointerY < 0 || pointerY > headerHeight) {
            return -1;
        }
        float edge = -scrollX;
        int closest = -1;
        float closestDistance = RESIZE_GRIP;
        for (int index = 0; index < displayWidths.length - 1; index++) {
            edge += displayWidths[index];
            float distance = Math.abs(x - edge);
            if (edge >= 0 && edge <= viewportWidth && distance <= closestDistance) {
                closest = index;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private float maximumResizeWidth(int index) {
        if (index >= columns.size() - 1) {
            return Float.POSITIVE_INFINITY;
        }
        float configured = configuredWidth();
        if (configured > viewportWidth) {
            return Float.POSITIVE_INFINITY;
        }
        float lastWidth = columns.get(columns.size() - 1).width();
        float lastMinimum = columns.get(columns.size() - 1).minimumWidth();
        float fixedOthers = configured - lastWidth - columns.get(index).width();
        return viewportWidth - lastMinimum - fixedOthers;
    }

    private void requireColumnIndex(int index) {
        if (index < 0 || index >= columns.size()) {
            throw new IllegalArgumentException("Column index is out of range.");
        }
    }

    private int indexOfColumn(Column column) {
        for (int index = 0; index < columns.size(); index++) {
            if (columns.get(index) == column) {
                return index;
            }
        }
        return -1;
    }

    private int indexOfRow(Row row) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index) == row) {
                return index;
            }
        }
        return -1;
    }

    /**
     * A table column with a title, optional icon, width and sort settings.
     */
    public static final class Column {
        private String title;
        private Texture icon;
        private float width;
        private float minimumWidth = DEFAULT_MIN_COLUMN_WIDTH;
        private boolean sortable = true;
        private Comparator<Row> comparator;

        /**
         * Creates a column.
         *
         * @param title column title
         * @param width positive initial width in logical UI units
         * @throws NullPointerException if {@code title} is null
         * @throws IllegalArgumentException if the width is non-finite or not positive
         */
        public Column(String title, float width) {
            this.title = Objects.requireNonNull(title, "title");
            setWidth(width);
        }

        /**
         * Returns the column title.
         *
         * @return the title, never null
         */
        public String title() {
            return title;
        }

        /**
         * Sets the column title.
         *
         * @param title column title
         * @throws NullPointerException if {@code title} is null
         */
        public void setTitle(String title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /**
         * Returns the header icon.
         *
         * @return the icon texture, or null when the column has no icon
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Sets the header icon drawn before the title.
         *
         * @param icon icon texture, or null to clear it
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Returns the configured column width.
         *
         * @return the width in logical UI units
         */
        public float width() {
            return width;
        }

        /**
         * Sets the configured column width.
         *
         * @param width positive width in logical UI units
         * @throws IllegalArgumentException if the width is non-finite or not positive
         */
        public void setWidth(float width) {
            if (!Float.isFinite(width) || width <= 0) {
                throw new IllegalArgumentException("Column width must be finite and positive.");
            }
            this.width = width;
        }

        /**
         * Returns the minimum width used while resizing this column.
         *
         * @return the minimum width in logical UI units
         */
        public float minimumWidth() {
            return minimumWidth;
        }

        /**
         * Sets the minimum width used while resizing this column.
         *
         * @param minimumWidth positive minimum width in logical UI units
         * @throws IllegalArgumentException if the width is non-finite or not positive
         */
        public void setMinimumWidth(float minimumWidth) {
            if (!Float.isFinite(minimumWidth) || minimumWidth <= 0) {
                throw new IllegalArgumentException("Minimum column width must be finite and positive.");
            }
            this.minimumWidth = minimumWidth;
        }

        /**
         * Reports whether clicking the header sorts by this column.
         *
         * @return true when header sorting is allowed
         */
        public boolean isSortable() {
            return sortable;
        }

        /**
         * Enables or disables header sorting for this column.
         *
         * @param sortable true to allow header sorting
         */
        public void setSortable(boolean sortable) {
            this.sortable = sortable;
        }

        /**
         * Returns the custom row comparator used for sorting.
         *
         * @return the comparator, or null when cell values are compared
         */
        public Comparator<Row> comparator() {
            return comparator;
        }

        /**
         * Assigns a custom comparator used when this column is sorted.
         *
         * @param comparator row comparator, or null to compare cell values
         */
        public void setComparator(Comparator<Row> comparator) {
            this.comparator = comparator;
        }
    }

    /**
     * A table row containing cells in column order.
     */
    public static final class Row {
        private final List<Cell> cells = new ArrayList<>();

        /**
         * Creates a row from the supplied cells.
         *
         * @param cells cells in column order
         * @throws NullPointerException if {@code cells} or one of its entries is null
         */
        public Row(Cell... cells) {
            Objects.requireNonNull(cells, "cells");
            for (Cell cell : cells) {
                add(cell);
            }
        }

        /**
         * Creates a row with text-only cells.
         *
         * @param texts cell texts in column order
         * @return the row
         * @throws NullPointerException if {@code texts} or one of its entries is null
         */
        public static Row of(String... texts) {
            Objects.requireNonNull(texts, "texts");
            Row row = new Row();
            for (String text : texts) {
                row.add(Cell.of(text));
            }
            return row;
        }

        /**
         * Returns the cells in column order.
         *
         * @return an immutable snapshot of the cells
         */
        public List<Cell> cells() {
            return List.copyOf(cells);
        }

        /**
         * Returns the cell at a column index.
         *
         * @param index zero-based column index
         * @return the cell, or null when the row has no cell for that column
         */
        public Cell cell(int index) {
            return index >= 0 && index < cells.size() ? cells.get(index) : null;
        }

        /**
         * Appends a cell.
         *
         * @param cell cell to append
         * @return this row
         * @throws NullPointerException if {@code cell} is null
         */
        public Row add(Cell cell) {
            cells.add(Objects.requireNonNull(cell, "cell"));
            return this;
        }

        /**
         * Appends a text-only cell.
         *
         * @param text cell text
         * @return this row
         * @throws NullPointerException if {@code text} is null
         */
        public Row add(String text) {
            return add(Cell.of(text));
        }

        /**
         * Returns the number of cells.
         *
         * @return the cell count
         */
        public int size() {
            return cells.size();
        }
    }

    /**
     * A table cell with display text, an optional icon and an optional sort value.
     * The sort value defaults to the display text and lets numeric columns sort numerically.
     */
    public static final class Cell {
        private String text;
        private Texture icon;
        private Comparable<?> sortValue;

        private Cell(String text, Texture icon, Comparable<?> sortValue) {
            this.text = Objects.requireNonNull(text, "text");
            this.icon = icon;
            this.sortValue = sortValue;
        }

        /**
         * Creates a text-only cell.
         *
         * @param text display text
         * @return the cell
         * @throws NullPointerException if {@code text} is null
         */
        public static Cell of(String text) {
            return new Cell(text, null, null);
        }

        /**
         * Creates a cell with an icon and display text.
         *
         * @param text display text
         * @param icon icon texture, or null for no icon
         * @return the cell
         * @throws NullPointerException if {@code text} is null
         */
        public static Cell of(String text, Texture icon) {
            return new Cell(text, icon, null);
        }

        /**
         * Creates a cell with an icon, display text and an explicit sort value.
         *
         * @param text display text
         * @param icon icon texture, or null for no icon
         * @param sortValue value used when sorting the column, or null to use the text
         * @return the cell
         * @throws NullPointerException if {@code text} is null
         */
        public static Cell of(String text, Texture icon, Comparable<?> sortValue) {
            return new Cell(text, icon, sortValue);
        }

        /**
         * Returns the display text.
         *
         * @return the text, never null
         */
        public String text() {
            return text;
        }

        /**
         * Sets the display text.
         *
         * @param text display text
         * @throws NullPointerException if {@code text} is null
         */
        public void setText(String text) {
            this.text = Objects.requireNonNull(text, "text");
        }

        /**
         * Returns the cell icon.
         *
         * @return the icon texture, or null when the cell has no icon
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Sets the cell icon drawn before the text.
         *
         * @param icon icon texture, or null to clear it
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Returns the value used for sorting.
         *
         * @return the sort value, or the display text when no explicit value is assigned
         */
        public Comparable<?> sortValue() {
            return sortValue == null ? text : sortValue;
        }

        /**
         * Sets the value used for sorting.
         *
         * @param sortValue sort value, or null to use the display text
         */
        public void setSortValue(Comparable<?> sortValue) {
            this.sortValue = sortValue;
        }
    }
}
