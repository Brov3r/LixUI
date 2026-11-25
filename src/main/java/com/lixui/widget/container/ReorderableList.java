package com.lixui.widget.container;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.RenderManager;
import com.lixui.widget.DragSession;
import com.lixui.widget.DropZone;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A table with a leading grip column whose rows can be reordered by dragging.
 * Dragging the grip of a selected row moves the whole selection as one block. The selection does
 * not have to be contiguous: moved rows keep their display order and become one contiguous block.
 * A line shows the insertion point, and releasing over the list reorders the internal rows and
 * invokes {@link #setOnReorder(ReorderListener)}. Releasing outside, pressing Escape or detaching
 * the list cancels the gesture. Sorting is disabled because the reordered order is the display order.
 * A list reorders only its own rows; cross-list group transfers are not supported.
 */
public final class ReorderableList extends Panel {
    private static final String PREVIEW_ROW = "Move row";
    private static final String PREVIEW_ROWS = "Move rows";
    private static final float GRIP_WIDTH = 28;
    private static final float GRIP_MARK_WIDTH = 10;
    private static final float GRIP_MARK_GAP = 4;

    private final String dragKind = "reorder-" + System.identityHashCode(this);
    private final GripTable table;
    private final ReorderDropZone dropZone;
    private ReorderListener onReorder;

    /**
     * Creates an empty reorderable list with a leading grip column.
     */
    public ReorderableList() {
        table = new GripTable(this);
        table.setSelectionMode(Table.SelectionMode.MULTIPLE);
        table.setDragSelectionEnabled(false);
        table.setSortingEnabled(false);
        table.setPreserveSelectionOnPress(true);
        Table.Column grip = table.addColumn("", GRIP_WIDTH);
        grip.setSortable(false);
        grip.setMinimumWidth(GRIP_WIDTH);
        dropZone = new ReorderDropZone(table, dragKind);
        dropZone.setOnDrop((source, kind, payload) ->
                payload instanceof ReorderPayload reorder && applyReorder(reorder.rows()));
        add(dropZone);
    }

    /**
     * Returns the data columns in display order, excluding the internal grip column.
     *
     * @return an immutable snapshot of the data columns
     */
    public List<Table.Column> columns() {
        List<Table.Column> all = table.columns();
        return List.copyOf(all.subList(1, all.size()));
    }

    /**
     * Appends a data column with the default width after the grip column.
     *
     * @param title column title
     * @return the added data column
     * @throws NullPointerException if {@code title} is null
     */
    public Table.Column addColumn(String title) {
        return table.addColumn(title);
    }

    /**
     * Appends a data column with an explicit width after the grip column.
     *
     * @param title column title
     * @param width positive initial width in logical UI units
     * @return the added data column
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the width is non-finite or not positive
     */
    public Table.Column addColumn(String title, float width) {
        return table.addColumn(title, width);
    }

    /**
     * Removes a data column.
     *
     * @param column data column returned by {@link #addColumn}
     * @return true when the column was present
     * @throws NullPointerException if {@code column} is null
     */
    public boolean removeColumn(Table.Column column) {
        Objects.requireNonNull(column, "column");
        if (!columns().contains(column)) {
            return false;
        }
        return table.removeColumn(column);
    }

    /**
     * Returns the rows in display order.
     * The exposed rows contain the internal leading grip cell at index zero.
     *
     * @return an immutable snapshot of the rows
     */
    public List<Table.Row> rows() {
        return table.rows();
    }

    /**
     * Appends a row and inserts its leading grip cell.
     *
     * @param row row with data cells in column order
     * @return the internal row added to the table
     * @throws NullPointerException if {@code row} is null
     */
    public Table.Row addRow(Table.Row row) {
        Objects.requireNonNull(row, "row");
        Table.Row internal = new Table.Row(Table.Cell.of(""));
        for (Table.Cell cell : row.cells()) {
            internal.add(cell);
        }
        return table.addRow(internal);
    }

    /**
     * Appends a row from text-only data cells and inserts its leading grip cell.
     *
     * @param texts cell texts in data column order
     * @return the internal row added to the table
     * @throws NullPointerException if {@code texts} or one of its entries is null
     */
    public Table.Row addRow(String... texts) {
        Objects.requireNonNull(texts, "texts");
        Table.Row internal = new Table.Row(Table.Cell.of(""));
        for (String text : texts) {
            internal.add(Table.Cell.of(text));
        }
        return table.addRow(internal);
    }

    /**
     * Removes a row by reference.
     *
     * @param row row to remove
     * @return true when the row was present
     * @throws NullPointerException if {@code row} is null
     */
    public boolean removeRow(Table.Row row) {
        return table.removeRow(Objects.requireNonNull(row, "row"));
    }

    /**
     * Removes every row and clears the selection.
     */
    public void clearRows() {
        table.clearRows();
    }

    /**
     * Returns the row selection behavior.
     *
     * @return the current selection mode
     */
    public Table.SelectionMode selectionMode() {
        return table.selectionMode();
    }

    /**
     * Sets the row selection behavior.
     *
     * @param selectionMode selection mode
     * @throws NullPointerException if {@code selectionMode} is null
     */
    public void setSelectionMode(Table.SelectionMode selectionMode) {
        table.setSelectionMode(Objects.requireNonNull(selectionMode, "selectionMode"));
    }

    /**
     * Returns the selected rows in selection order.
     *
     * @return an immutable snapshot of the selected rows
     */
    public List<Table.Row> selectedRows() {
        return table.selectedRows();
    }

    /**
     * Replaces the row selection.
     *
     * @param rows rows to select
     * @throws NullPointerException if {@code rows} or one of its entries is null
     */
    public void setSelectedRows(Collection<Table.Row> rows) {
        table.setSelectedRows(rows);
    }

    /**
     * Selects a single row and clears the previous selection.
     *
     * @param row row to select
     * @throws NullPointerException if {@code row} is null
     * @throws IllegalArgumentException if the row does not belong to this list
     */
    public void selectRow(Table.Row row) {
        table.selectRow(Objects.requireNonNull(row, "row"));
    }

    /**
     * Clears the row selection.
     */
    public void clearSelection() {
        table.clearSelection();
    }

    /**
     * Returns the callback invoked after the selected rows change.
     *
     * @return the selection listener, or null when none is assigned
     */
    public Consumer<List<Table.Row>> selectionListener() {
        return table.selectionListener();
    }

    /**
     * Assigns a callback invoked after the selected rows change.
     *
     * @param selectionListener listener receiving an immutable snapshot, or null to clear it
     */
    public void setSelectionListener(Consumer<List<Table.Row>> selectionListener) {
        table.setSelectionListener(selectionListener);
    }

    /**
     * Returns the header height.
     *
     * @return the header height in logical UI units
     */
    public float headerHeight() {
        return table.headerHeight();
    }

    /**
     * Sets the header height.
     *
     * @param headerHeight positive header height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public void setHeaderHeight(float headerHeight) {
        table.setHeaderHeight(headerHeight);
    }

    /**
     * Returns the row height.
     *
     * @return the row height in logical UI units
     */
    public float rowHeight() {
        return table.rowHeight();
    }

    /**
     * Sets the row height.
     *
     * @param rowHeight positive row height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public void setRowHeight(float rowHeight) {
        table.setRowHeight(rowHeight);
    }

    /**
     * Reports whether the list fills the available width.
     *
     * @return true when the list stretches to the available width
     */
    public boolean isFillWidth() {
        return table.isFillWidth();
    }

    /**
     * Enables or disables filling the available width.
     *
     * @param fillWidth true to stretch the list to the available width
     */
    public void setFillWidth(boolean fillWidth) {
        table.setFillWidth(fillWidth);
    }

    /**
     * Returns the callback invoked after a drag changes the row order.
     *
     * @return the reorder listener, or null when none is assigned
     */
    public ReorderListener onReorder() {
        return onReorder;
    }

    /**
     * Assigns a callback invoked after the internal rows have been reordered.
     * The callback runs after the rows are already in their new order. {@code from} is the original
     * index of the first moved row and {@code to} is the final index where the moved block starts.
     * Non-contiguous selections move as one block while keeping their relative display order.
     *
     * @param onReorder reorder listener, or null to clear it
     */
    public void setOnReorder(ReorderListener onReorder) {
        this.onReorder = onReorder;
    }

    /**
     * Measures the internal table with the supplied constraints.
     *
     * @param constraints available and required dimensions
     * @return the natural table size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return table.measure(constraints);
    }

    /**
     * Stretches the internal table and its drop target over the list bounds.
     */
    @Override
    protected void onLayout() {
        dropZone.layout(localBounds());
    }

    /**
     * Draws no surface of its own; the internal table paints the list.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
    }

    private boolean applyReorder(List<Table.Row> movedRows) {
        List<Table.Row> current = new ArrayList<>(table.rows());
        List<Table.Row> block = new ArrayList<>();
        List<Integer> sourceIndices = new ArrayList<>();
        for (int index = 0; index < current.size(); index++) {
            Table.Row row = current.get(index);
            if (movedRows.contains(row)) {
                block.add(row);
                sourceIndices.add(index);
            }
        }
        if (block.isEmpty() || block.size() != movedRows.size()) {
            return false;
        }
        int from = sourceIndices.get(0);
        DragSession session = context() == null ? null : context().dragSession();
        int target = session != null && session.source() == table
                ? table.insertionIndexAtViewport(session.y()) : table.dropIndex();
        int slot = Math.clamp(target, 0, current.size());
        int destination = slot;
        for (int index : sourceIndices) {
            if (index < slot) {
                destination--;
            }
        }
        List<Table.Row> reordered = new ArrayList<>(current);
        for (int index = sourceIndices.size() - 1; index >= 0; index--) {
            reordered.remove((int) sourceIndices.get(index));
        }
        destination = Math.clamp(destination, 0, reordered.size());
        reordered.addAll(destination, block);
        if (sameRows(current, reordered)) {
            return true;
        }
        table.replaceRows(reordered);
        if (onReorder != null) {
            onReorder.onReorder(from, destination);
        }
        return true;
    }

    private static boolean sameRows(List<Table.Row> first, List<Table.Row> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (first.get(index) != second.get(index)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Receives the original and final index of a reordered row block.
     */
    @FunctionalInterface
    public interface ReorderListener {
        /**
         * Called after the internal row order changed.
         * The moved rows are already at their final positions. Gaps in a non-contiguous selection
         * are closed, so the block is contiguous at {@code to}.
         *
         * @param from original index of the first moved row
         * @param to final index where the moved block starts
         */
        void onReorder(int from, int to);
    }

    private record ReorderPayload(List<Table.Row> rows) {
        private ReorderPayload {
            rows = List.copyOf(rows);
        }
    }

    private static final class ReorderDropZone extends DropZone {
        private final GripTable table;

        private ReorderDropZone(GripTable table, String kind) {
            this.table = table;
            setAcceptedKind(kind);
            add(table);
        }

        /**
         * Keeps the drop target transparent so the table remains visible.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
        }

        /**
         * Stretches the table across the drop target bounds.
         */
        @Override
        protected void onLayout() {
            table.layout(localBounds());
        }
    }

    private static final class GripTable extends Table {
        private final ReorderableList owner;
        private int gripPressRow = -1;
        private int dropIndex = -1;
        private List<Table.Row> dragRows = List.of();

        private GripTable(ReorderableList owner) {
            this.owner = owner;
        }

        /**
         * Selects the row under a grip press and remembers it for the next drag.
         *
         * @param x horizontal pointer position relative to the table
         * @param y vertical pointer position relative to the table
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the table consumed the press
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            boolean handled = super.onPointerPress(x, y, button, modifiers);
            gripPressRow = -1;
            if (button == MouseButton.LEFT && isGripColumn(x)) {
                int row = rowAt(y);
                if (row >= 0) {
                    gripPressRow = row;
                }
            }
            return handled;
        }

        /**
         * Starts a row drag from the grip column.
         *
         * @param x horizontal pointer position relative to the table
         * @param y vertical pointer position relative to the table
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return true when a reorder drag was started
         */
        @Override
        protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT || gripPressRow < 0 || sortOrder() != SortOrder.NONE) {
                return false;
            }
            List<Table.Row> displayed = displayedRows();
            if (gripPressRow >= displayed.size()) {
                return false;
            }
            Table.Row pressed = displayed.get(gripPressRow);
            List<Table.Row> selected = selectedRows();
            List<Table.Row> moved = new ArrayList<>();
            if (selected.contains(pressed)) {
                for (Table.Row row : displayed) {
                    if (selected.contains(row)) {
                        moved.add(row);
                    }
                }
            } else {
                moved.add(pressed);
            }
            if (moved.isEmpty() || context() == null || context().isDragging()) {
                return false;
            }
            dragRows = List.copyOf(moved);
            dropIndex = insertionIndexAt(y);
            String preview = moved.size() == 1 ? PREVIEW_ROW : PREVIEW_ROWS;
            context().startDrag(this, button, owner.dragKind,
                    new ReorderPayload(dragRows), preview);
            return true;
        }

        /**
         * Tracks the insertion point while the rows are dragged.
         *
         * @param x horizontal pointer position relative to the table
         * @param y vertical pointer position relative to the table
         * @param button dragged button
         * @param modifiers modifiers captured at press time
         * @return true when the move belongs to the active reorder drag
         */
        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button == MouseButton.LEFT && !dragRows.isEmpty() && isDragSource()) {
                dropIndex = contains(x, y) ? insertionIndexAt(y) : -1;
                return true;
            }
            return super.onDrag(x, y, button, modifiers);
        }

        /**
         * Clears the insertion line when the drag ends.
         *
         * @param x horizontal pointer position relative to the table
         * @param y vertical pointer position relative to the table
         * @param button released button
         * @param modifiers modifiers captured at press time
         * @return the result of the table drag end hook
         */
        @Override
        protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
            boolean handled = super.onDragEnd(x, y, button, modifiers);
            clearDrag();
            return handled;
        }

        /**
         * Forgets the grip press after the pointer is released.
         *
         * @param x horizontal pointer position relative to the table
         * @param y vertical pointer position relative to the table
         * @param button released button
         * @param modifiers active modifiers
         * @return the result of the table release hook
         */
        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button,
                                          Modifiers modifiers) {
            gripPressRow = -1;
            return super.onPointerRelease(x, y, button, modifiers);
        }

        /**
         * Clears stale drag feedback when the session ends without a drag end callback.
         *
         * @param frame timing information for the current frame
         */
        @Override
        protected void onUpdate(FrameInfo frame) {
            super.onUpdate(frame);
            if (!dragRows.isEmpty() && !isDragSource()) {
                clearDrag();
            }
        }

        /**
         * Paints the table, its grip marks and the insertion line.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            super.onDraw(renderer, frame);
            drawGripMarks(renderer);
            drawInsertionLine(renderer);
        }

        private boolean isDragSource() {
            DragSession session = context() == null ? null : context().dragSession();
            return session != null && session.isActive() && session.source() == this;
        }

        private boolean isGripColumn(float x) {
            float gripRight = columnWidth(0) - scrollX();
            return gripRight > 0 && x >= 0 && x <= gripRight;
        }

        private int insertionIndexAt(float localY) {
            int count = displayedRows().size();
            float contentY = localY - headerHeight() + scrollY();
            int slot = (int) Math.floor(contentY / rowHeight() + 0.5f);
            return Math.clamp(slot, 0, count);
        }

        private int insertionIndexAtViewport(float viewportY) {
            return insertionIndexAt(viewportY - absolutePosition().y());
        }

        private int dropIndex() {
            return dropIndex;
        }

        private float dropIndexY() {
            return dropIndex < 0 ? 0 : headerHeight() + dropIndex * rowHeight() - scrollY();
        }

        private void clearDrag() {
            dragRows = List.of();
            dropIndex = -1;
            gripPressRow = -1;
        }

        private void drawGripMarks(RenderManager renderer) {
            float viewportHeight = rowViewportHeight();
            if (viewportHeight <= 0 || rowViewportWidth() <= 0) {
                return;
            }
            float gripRight = columnWidth(0) - scrollX();
            float markWidth = Math.min(GRIP_MARK_WIDTH, gripRight - 12);
            if (markWidth <= 0) {
                return;
            }
            Color color = context().theme().textMuted().withAlpha(0.7f);
            Point origin = absolutePosition();
            float markX = Math.max(4, (gripRight - markWidth) / 2);
            Rect clip = new Rect(origin.x(), origin.y() + headerHeight(),
                    rowViewportWidth(), viewportHeight);
            renderer.withClip(clip,
                    () -> drawGripRows(renderer, markX, markWidth, viewportHeight, color));
        }

        private void drawGripRows(RenderManager renderer, float markX, float markWidth,
                                  float viewportHeight, Color color) {
            float y = headerHeight() - scrollY();
            List<Table.Row> visible = displayedRows();
            for (int index = 0; index < visible.size(); index++) {
                if (y + rowHeight() > headerHeight()
                        && y < headerHeight() + viewportHeight) {
                    float center = y + rowHeight() / 2;
                    for (int row = -1; row <= 1; row++) {
                        renderer.rect(new Rect(markX, center + row * GRIP_MARK_GAP - 0.5f,
                                markWidth, 1), color);
                    }
                }
                y += rowHeight();
            }
        }

        private void drawInsertionLine(RenderManager renderer) {
            if (dropIndex < 0 || dragRows.isEmpty()) {
                return;
            }
            float viewportWidth = rowViewportWidth();
            float viewportHeight = rowViewportHeight();
            if (viewportWidth <= 0 || viewportHeight < 3) {
                return;
            }
            float y = Math.clamp(dropIndexY(), headerHeight() + 1,
                    headerHeight() + viewportHeight - 2);
            Point origin = absolutePosition();
            Color color = context().theme().primary();
            renderer.withClip(new Rect(origin.x(), origin.y() + headerHeight(),
                    viewportWidth, viewportHeight),
                    () -> renderer.rect(new Rect(0, y - 1, viewportWidth, 2), color));
        }
    }
}
