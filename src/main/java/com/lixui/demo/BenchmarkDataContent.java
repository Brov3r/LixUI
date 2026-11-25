package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.container.Table;
import com.lixui.widget.container.TreeView;
import com.lixui.widget.container.VirtualList;
import com.lixui.widget.layout.VBox;

import java.util.Locale;

/**
 * Stress content for the data scenario: a table, a virtual list and a tree filled with the same
 * requested row count.
 * Tree nodes are capped because every insertion rebuilds the visible row list, which would make the
 * build phase quadratic for ten thousand nodes.
 */
final class BenchmarkDataContent extends Panel {
    static final int TREE_NODE_LIMIT = 1_000;
    private static final float PANE_HEIGHT = 84;
    private static final float ROW_HEIGHT = 20;

    private final VBox body = new VBox();
    private final int requestedRows;
    private final Table table = new Table();
    private final VirtualList list = new VirtualList();
    private final TreeView tree = new TreeView();

    /**
     * Builds the data stress content.
     *
     * @param rows requested rows per data widget
     * @throws IllegalArgumentException if the row count is negative
     */
    BenchmarkDataContent(int rows) {
        if (rows < 0) {
            throw new IllegalArgumentException("Row count must be non-negative.");
        }
        requestedRows = rows;
        body.setGap(4);
        body.setPadding(2);
        configureTable(rows);
        configureList(rows);
        configureTree(rows);
        BenchmarkStage tableStage = new BenchmarkStage(PANE_HEIGHT);
        tableStage.add(table);
        BenchmarkStage listStage = new BenchmarkStage(PANE_HEIGHT);
        listStage.add(list);
        BenchmarkStage treeStage = new BenchmarkStage(PANE_HEIGHT);
        treeStage.add(tree);
        body.add(tableStage).add(listStage).add(treeStage);
        add(body);
    }

    /**
     * Measures the data panes through the inner box.
     *
     * @param constraints available and required dimensions
     * @return the natural content size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return body.measure(constraints);
    }

    /**
     * Stretches the data panes across the assigned content bounds.
     */
    @Override
    protected void onLayout() {
        body.layout(new Rect(0, 0, bounds().width(), bounds().height()));
    }

    /**
     * Returns the requested number of rows.
     *
     * @return rows per data widget requested by the scenario
     */
    int requestedRows() {
        return requestedRows;
    }

    /**
     * Returns the stress table.
     *
     * @return the table containing the requested number of rows
     */
    Table table() {
        return table;
    }

    /**
     * Returns the stress virtual list.
     *
     * @return the virtual list containing the requested number of rows
     */
    VirtualList list() {
        return list;
    }

    /**
     * Returns the stress tree.
     *
     * @return the tree with at most {@link #TREE_NODE_LIMIT} roots
     */
    TreeView tree() {
        return tree;
    }

    private void configureTable(int rows) {
        table.setFillWidth(true);
        table.setRowHeight(ROW_HEIGHT);
        table.setColumnResizingEnabled(false);
        table.setSortingEnabled(false);
        table.addColumn("Row", 64);
        table.addColumn("Name", 190);
        table.addColumn("Value", 72);
        for (int index = 0; index < rows; index++) {
            table.addRow(Integer.toString(index), "benchmark-row-" + index,
                    Integer.toString(index * 7 % 1000));
        }
    }

    private void configureList(int rows) {
        list.setRowHeight(ROW_HEIGHT);
        list.setItemCount(rows);
        list.setRowRenderer((index, bounds, state, renderer) -> drawListRow(index, bounds, renderer));
    }

    private void drawListRow(int index, Rect bounds, RenderManager renderer) {
        Font font = list.font();
        if (font == null) {
            return;
        }
        Color color = list.context() == null ? Color.WHITE : list.context().theme().text();
        renderer.text(font, String.format(Locale.ROOT, "row %d — benchmark entry", index),
                bounds.x() + 8, bounds.y() + bounds.height() / 2, color,
                HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
    }

    private void configureTree(int rows) {
        tree.setRowHeight(ROW_HEIGHT);
        tree.setExpansionDuration(0);
        int nodes = Math.min(rows, TREE_NODE_LIMIT);
        for (int index = 0; index < nodes; index++) {
            tree.addRoot("node-" + index);
        }
    }
}
