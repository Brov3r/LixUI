package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A container that arranges children in a grid of rows and columns.
 * Children fill cells in insertion order, row by row. Columns and rows are defined as
 * {@link GridTrack} values, so each track is either fixed or proportional. Rows beyond the defined
 * ones are added implicitly and sized to their content; when no columns are defined the grid uses
 * a single column that fills the content width. Proportional tracks share the space left after
 * fixed and content-sized tracks and gaps.
 * <p>
 * Children always fill their assigned cell, so {@link Widget#grow()} is ignored. Invisible children
 * take no cell and keep their bounds.
 */
public final class Grid extends LayoutContainer {
    private List<GridTrack> columns = List.of(GridTrack.proportional(1));
    private List<GridTrack> rows = List.of();
    private float gap;

    /**
     * Creates an empty grid with a single stretching column and no gaps.
     */
    public Grid() {
    }

    /**
     * Returns the column tracks in left-to-right order.
     *
     * @return an immutable list of column tracks
     */
    public List<GridTrack> columns() {
        return columns;
    }

    /**
     * Replaces the column tracks.
     * An empty array leaves a single column that fills the content width.
     *
     * @param columns column tracks in left-to-right order
     * @throws NullPointerException if the array or any track is null
     */
    public void setColumns(GridTrack... columns) {
        Objects.requireNonNull(columns, "columns");
        this.columns = columns.length == 0
                ? List.of(GridTrack.proportional(1))
                : List.of(columns);
    }

    /**
     * Returns the row tracks in top-to-bottom order.
     *
     * @return an immutable list of row tracks
     */
    public List<GridTrack> rows() {
        return rows;
    }

    /**
     * Replaces the row tracks.
     * Rows beyond the supplied tracks are added implicitly and sized to their content.
     *
     * @param rows row tracks in top-to-bottom order
     * @throws NullPointerException if the array or any track is null
     */
    public void setRows(GridTrack... rows) {
        Objects.requireNonNull(rows, "rows");
        this.rows = List.of(rows);
    }

    /**
     * Returns the gap between adjacent columns and rows.
     *
     * @return the gap in logical UI units
     */
    public float gap() {
        return gap;
    }

    /**
     * Sets the gap between adjacent columns and rows.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public void setGap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Grid gap must be finite and non-negative.");
        }
        this.gap = gap;
    }

    /**
     * Measures fixed tracks, proportional tracks and implicit rows from the children.
     * Fixed tracks report their configured size, while proportional and implicit tracks report the
     * largest child measured in that track.
     *
     * @param constraints available and required dimensions
     * @return the natural grid size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = availableWidth(constraints);
        float availableHeight = availableHeight(constraints);
        int columnCount = Math.max(1, columns.size());
        List<Widget> visible = visibleChildren();
        int rowCount = rowCount(visible.size(), columnCount);
        float[] columnBasis = new float[columnCount];
        float[] rowBasis = new float[rowCount];
        for (int index = 0; index < visible.size(); index++) {
            Size size = visible.get(index).measure(
                    LayoutConstraints.loose(availableWidth, availableHeight));
            int column = index % columnCount;
            int row = index / columnCount;
            columnBasis[column] = Math.max(columnBasis[column], size.width());
            rowBasis[row] = Math.max(rowBasis[row], size.height());
        }
        float width = padding() * 2 + gapTotal(columnCount);
        for (int index = 0; index < columnCount; index++) {
            width += naturalTrackSize(columns, index, columnBasis[index]);
        }
        float height = padding() * 2 + gapTotal(rowCount);
        for (int index = 0; index < rowCount; index++) {
            height += naturalTrackSize(rows, index, rowBasis[index]);
        }
        return new Size(width, height);
    }

    /**
     * Resolves the track sizes inside the padded content area and places every visible child in its
     * cell. Extra visible children create implicit content-sized rows.
     */
    @Override
    protected void onLayout() {
        int columnCount = Math.max(1, columns.size());
        List<Widget> visible = visibleChildren();
        int rowCount = rowCount(visible.size(), columnCount);
        float[] columnBasis = new float[columnCount];
        float[] rowBasis = new float[rowCount];
        for (int index = 0; index < visible.size(); index++) {
            Size size = visible.get(index).measure(
                    LayoutConstraints.loose(contentWidth(), contentHeight()));
            int column = index % columnCount;
            int row = index / columnCount;
            columnBasis[column] = Math.max(columnBasis[column], size.width());
            rowBasis[row] = Math.max(rowBasis[row], size.height());
        }
        float[] widths = resolveTracks(columns, columnBasis, contentWidth());
        float[] heights = resolveTracks(rows, rowBasis, contentHeight());
        float[] columnOffsets = trackOffsets(widths);
        float[] rowOffsets = trackOffsets(heights);
        for (int index = 0; index < visible.size(); index++) {
            int column = index % columnCount;
            int row = index / columnCount;
            visible.get(index).layout(new Rect(
                    padding() + columnOffsets[column],
                    padding() + rowOffsets[row],
                    widths[column],
                    heights[row]));
        }
    }

    private List<Widget> visibleChildren() {
        List<Widget> visible = new ArrayList<>();
        for (Widget child : children()) {
            if (child.isVisible()) {
                visible.add(child);
            }
        }
        return visible;
    }

    private int rowCount(int childCount, int columnCount) {
        int required = (childCount + columnCount - 1) / columnCount;
        return Math.max(rows.size(), required);
    }

    private static float naturalTrackSize(List<GridTrack> tracks, int index, float basis) {
        if (index >= tracks.size()) {
            return basis;
        }
        return tracks.get(index) instanceof GridTrack.Fixed fixed ? fixed.size() : basis;
    }

    private float[] resolveTracks(List<GridTrack> tracks, float[] basis, float available) {
        int count = basis.length;
        float[] resolved = new float[count];
        float fixedSize = 0;
        float totalWeight = 0;
        for (int index = 0; index < count; index++) {
            if (index >= tracks.size()) {
                fixedSize += basis[index];
            } else if (tracks.get(index) instanceof GridTrack.Fixed fixed) {
                fixedSize += fixed.size();
            } else {
                totalWeight += ((GridTrack.Proportional) tracks.get(index)).weight();
            }
        }
        float free = Math.max(0, available - gapTotal(count) - fixedSize);
        for (int index = 0; index < count; index++) {
            if (index >= tracks.size()) {
                resolved[index] = basis[index];
            } else if (tracks.get(index) instanceof GridTrack.Fixed fixed) {
                resolved[index] = fixed.size();
            } else if (totalWeight > 0) {
                resolved[index] = free * ((GridTrack.Proportional) tracks.get(index)).weight()
                        / totalWeight;
            }
        }
        return resolved;
    }

    private float[] trackOffsets(float[] sizes) {
        float[] offsets = new float[sizes.length];
        float offset = 0;
        for (int index = 0; index < sizes.length; index++) {
            offsets[index] = offset;
            offset += sizes[index] + gap;
        }
        return offsets;
    }

    private float gapTotal(int trackCount) {
        return trackCount > 1 ? gap * (trackCount - 1) : 0;
    }
}
