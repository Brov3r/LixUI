package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;

/**
 * A simple tile grid that gives every benchmark child a fixed slot.
 * Fixed slots keep naturally large widgets, such as charts, from expanding the stress scene to an
 * unmanageable height while still exercising their real layout and drawing code.
 */
final class BenchmarkTileGrid extends Panel {
    private final float tileWidth;
    private final float tileHeight;
    private final float gap;

    /**
     * Creates an empty tile grid.
     *
     * @param tileWidth positive tile width in logical UI units
     * @param tileHeight positive tile height in logical UI units
     * @param gap non-negative gap between tiles
     * @throws IllegalArgumentException if a dimension is not finite or positive, or the gap is negative
     */
    BenchmarkTileGrid(float tileWidth, float tileHeight, float gap) {
        if (!Float.isFinite(tileWidth) || tileWidth <= 0
                || !Float.isFinite(tileHeight) || tileHeight <= 0) {
            throw new IllegalArgumentException("Tile dimensions must be finite and positive.");
        }
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Tile gap must be finite and non-negative.");
        }
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.gap = gap;
        setClipToBounds(true);
    }

    /**
     * Reports the height required by the current number of tiles.
     *
     * @param constraints available and required dimensions
     * @return the grid size at the available width
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        int columns = columnsFor(constraints.maxWidth());
        int rows = rowsFor(columns);
        float height = rows == 0 ? 0 : rows * tileHeight + (rows - 1) * gap;
        return new Size(constraints.maxWidth(), height);
    }

    /**
     * Places every child in its fixed tile slot.
     */
    @Override
    protected void onLayout() {
        int columns = columnsFor(bounds().width());
        int index = 0;
        for (Widget child : children()) {
            if (child.parent() != this || !child.isVisible()) {
                continue;
            }
            int column = index % columns;
            int row = index / columns;
            child.layout(new Rect(column * (tileWidth + gap), row * (tileHeight + gap),
                    tileWidth, tileHeight));
            index++;
        }
    }

    private int columnsFor(float width) {
        return Math.max(1, (int) ((Math.max(0, width) + gap) / (tileWidth + gap)));
    }

    private int rowsFor(int columns) {
        int count = 0;
        for (Widget child : children()) {
            if (child.isVisible()) {
                count++;
            }
        }
        return count == 0 ? 0 : (count + columns - 1) / columns;
    }
}
