package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.FocusChanged;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.theme.WidgetState;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestTexture;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableTest {
    @Test
    void rejectsInvalidConfiguration() {
        Table table = new Table();

        assertThrows(IllegalArgumentException.class, () -> table.setHeaderHeight(0));
        assertThrows(IllegalArgumentException.class, () -> table.setHeaderHeight(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> table.setRowHeight(-1));
        assertThrows(IllegalArgumentException.class, () -> table.setMinimumColumnWidth(0));
        assertThrows(IllegalArgumentException.class, () -> table.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> table.setColumnPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> table.setScrollBarMargin(-1));
        assertThrows(NullPointerException.class, () -> table.addColumn((String) null));
        assertThrows(IllegalArgumentException.class, () -> table.addColumn("A", 0));
        assertThrows(NullPointerException.class, () -> table.addColumn((Table.Column) null));
        assertThrows(NullPointerException.class, () -> table.addRow((Table.Row) null));
        assertThrows(NullPointerException.class, () -> table.addRow((String[]) null));
        assertThrows(NullPointerException.class, () -> Table.Row.of((String) null));
        assertThrows(NullPointerException.class, () -> Table.Cell.of(null));
        assertThrows(IllegalArgumentException.class, () -> table.setSort(0, Table.SortOrder.ASCENDING));

        table.addColumn("A", 100);
        assertThrows(NullPointerException.class, () -> table.setSort(0, null));
        assertThrows(IllegalArgumentException.class, () -> table.setSort(0, Table.SortOrder.NONE));
        assertThrows(IllegalArgumentException.class, () -> table.columnWidth(1));
        assertThrows(NullPointerException.class, () -> new Table.Column(null, 100));
        assertThrows(IllegalArgumentException.class, () -> new Table.Column("A", -1));
    }

    @Test
    void sortsRowsWhenHeaderIsClicked() {
        WidgetManager manager = manager();
        Table table = new Table();
        table.setBounds(new Rect(0, 0, 300, 200));
        table.addColumn("Name", 150);
        table.addRow("Beta");
        table.addRow("Alpha");
        manager.add(table);
        manager.render();

        click(manager, 50, 10);
        assertEquals(Table.SortOrder.ASCENDING, table.sortOrder());
        assertEquals(List.of("Alpha", "Beta"), texts(table.displayedRows()));

        click(manager, 50, 10);
        assertEquals(Table.SortOrder.DESCENDING, table.sortOrder());
        assertEquals(List.of("Beta", "Alpha"), texts(table.displayedRows()));

        click(manager, 50, 10);
        assertEquals(Table.SortOrder.NONE, table.sortOrder());
        assertEquals(List.of("Beta", "Alpha"), texts(table.displayedRows()));

        table.setSortingEnabled(false);
        click(manager, 50, 10);
        assertEquals(Table.SortOrder.NONE, table.sortOrder());
    }

    @Test
    void resizesPenultimateColumnWithoutMovingTheLastRightEdge() {
        WidgetManager manager = manager();
        Table table = new Table();
        table.setBounds(new Rect(0, 0, 400, 200));
        table.addColumn("A", 100);
        table.addColumn("B", 100);
        table.addColumn("C", 100);
        table.addRow("A", "B", "C");
        manager.add(table);
        manager.render();

        assertEquals(200, table.columnWidth(2), 0.001f);

        drag(manager, 200, 280, 10);

        assertEquals(100, table.columnWidth(0), 0.001f);
        assertEquals(180, table.columnWidth(1), 0.001f);
        assertEquals(120, table.columnWidth(2), 0.001f);
        assertEquals(400, table.columnWidth(0) + table.columnWidth(1) + table.columnWidth(2),
                0.001f);
        assertEquals(Table.SortOrder.NONE, table.sortOrder());
    }

    @Test
    void columnResizingCanBeDisabled() {
        WidgetManager manager = manager();
        Table table = new Table();
        table.setBounds(new Rect(0, 0, 400, 200));
        table.addColumn("A", 100);
        table.addColumn("B", 100);
        table.addColumn("C", 100);
        manager.add(table);
        manager.render();
        table.setColumnResizingEnabled(false);

        drag(manager, 200, 280, 10);

        assertEquals(100, table.columnWidth(1), 0.001f);
    }

    @Test
    void clampsHorizontalScrollAtTheLastColumnEdge() {
        WidgetManager manager = manager();
        Table table = new Table();
        table.setBounds(new Rect(0, 0, 200, 100));
        table.addColumn("A", 100);
        table.addColumn("B", 100);
        table.addColumn("C", 100);
        for (int index = 0; index < 10; index++) {
            table.addRow("A", "B", "C");
        }
        manager.add(table);
        manager.render();

        ScrollBar horizontal = bar(table, ScrollBar.Orientation.HORIZONTAL);
        assertTrue(horizontal.isVisible());
        assertTrue(bar(table, ScrollBar.Orientation.VERTICAL).isVisible());

        table.setScroll(10_000, 0);

        float rightEdge = 300 - table.scrollX();
        assertEquals(horizontal.bounds().right() + table.scrollBarMargin(), rightEdge, 0.001f);
        assertTrue(table.scrollX() >= 0);
        assertThrows(IllegalArgumentException.class,
                () -> table.setScroll(Float.POSITIVE_INFINITY, 0));
    }

    @Test
    void selectsRowsWithCtrlAndShift() {
        WidgetManager manager = manager();
        Table table = tableWithRows(Table.SelectionMode.MULTIPLE);
        List<Integer> listenerSizes = new ArrayList<>();
        table.setSelectionListener(rows -> listenerSizes.add(rows.size()));
        manager.add(table);
        manager.render();

        click(manager, 50, rowCenter(0));
        assertEquals(List.of("A"), selectedTexts(table));

        click(manager, 50, rowCenter(2), new Modifiers(false, true, false, false));
        assertEquals(List.of("A", "C"), selectedTexts(table));

        click(manager, 50, rowCenter(3), new Modifiers(true, false, false, false));
        assertEquals(List.of("C", "D"), selectedTexts(table));
        assertEquals(List.of(1, 2, 2), listenerSizes);

        table.setSort(0, Table.SortOrder.DESCENDING);
        assertEquals(List.of("D", "C", "B", "A"), texts(table.displayedRows()));
        assertEquals(List.of("C", "D"), selectedTexts(table));
    }

    @Test
    void dragSelectsAContiguousRange() {
        WidgetManager manager = manager();
        Table table = tableWithRows(Table.SelectionMode.MULTIPLE);
        manager.add(table);
        manager.render();

        dragBetween(manager, 50, rowCenter(0), 50, rowCenter(3));

        assertEquals(List.of("A", "B", "C", "D"), selectedTexts(table));

        table.clearSelection();
        table.setDragSelectionEnabled(false);
        dragBetween(manager, 50, rowCenter(0), 50, rowCenter(3));
        assertEquals(List.of("A"), selectedTexts(table));
    }

    @Test
    void drawsAndAppliesTheMarqueeRectangle() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.HIDPI);
        Table table = tableWithRows(Table.SelectionMode.MULTIPLE);
        manager.add(table);
        manager.render();

        manager.handle(new PointerPressed(30, rowCenter(1), MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(120, rowCenter(2)));

        assertEquals(List.of("B", "C"), selectedTexts(table));
        manager.render();
        var marqueeColor = manager.context().theme().primary().withAlpha(0.16f);
        boolean overlay = backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.color().equals(marqueeColor));
        assertTrue(overlay);

        manager.handle(new PointerReleased(120, rowCenter(2), MouseButton.LEFT, Modifiers.NONE));
        assertEquals(2, table.selectedRows().size());
    }

    @Test
    void clearsRowHoverAfterLeavingThroughTheScrollbar() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.HIDPI);
        Table table = new Table();
        table.setBounds(new Rect(0, 0, 300, 200));
        table.addColumn("Name", 150);
        for (int index = 0; index < 10; index++) {
            table.addRow("Row " + index);
        }
        manager.add(table);
        Color hover = Color.mix(manager.context().theme().panelStyle()
                .background(WidgetState.NORMAL), manager.context().theme().primary(), 0.24f);

        manager.handle(new PointerMoved(50, rowCenter(0)));
        manager.render();
        assertTrue(hasColor(backend, hover));

        manager.handle(new PointerMoved(292, rowCenter(0)));
        manager.render();
        manager.handle(new PointerMoved(350, rowCenter(0)));
        manager.render();

        assertFalse(hasColor(backend, hover));
    }

    @Test
    void focusLossCancelsAnUnfinishedMarquee() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.HIDPI);
        Table table = tableWithRows(Table.SelectionMode.MULTIPLE);
        manager.add(table);
        manager.render();

        manager.handle(new PointerPressed(30, rowCenter(1), MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(120, rowCenter(2)));
        manager.render();
        var marqueeColor = manager.context().theme().primary().withAlpha(0.16f);
        assertTrue(hasMarquee(backend, marqueeColor));

        manager.handle(new FocusChanged(false));
        manager.render();

        assertFalse(hasMarquee(backend, marqueeColor));
        assertFalse(table.isPressed());
    }

    @Test
    void singleModeKeepsOnlyOneSelectedRow() {
        WidgetManager manager = manager();
        Table table = tableWithRows(Table.SelectionMode.SINGLE);
        manager.add(table);
        manager.render();

        click(manager, 50, rowCenter(0));
        click(manager, 50, rowCenter(2), new Modifiers(false, true, false, false));

        assertEquals(List.of("C"), selectedTexts(table));
    }

    @Test
    void ignoresSelectionForDisabledModeAndNonLeftButtons() {
        WidgetManager manager = manager();
        Table table = tableWithRows(Table.SelectionMode.MULTIPLE);
        manager.add(table);
        manager.render();

        manager.handle(new PointerPressed(50, rowCenter(1), MouseButton.RIGHT, Modifiers.NONE));
        manager.handle(new PointerReleased(50, rowCenter(1), MouseButton.RIGHT, Modifiers.NONE));
        assertTrue(table.selectedRows().isEmpty());

        manager.handle(new PointerPressed(50, rowCenter(1), MouseButton.MIDDLE, Modifiers.NONE));
        manager.handle(new PointerReleased(50, rowCenter(1), MouseButton.MIDDLE, Modifiers.NONE));
        assertTrue(table.selectedRows().isEmpty());

        table.setSelectionMode(Table.SelectionMode.NONE);
        click(manager, 50, rowCenter(1));
        assertTrue(table.selectedRows().isEmpty());
    }

    @Test
    void fillsTheAvailableWidthWhenEnabled() {
        Table table = tableWithRows(Table.SelectionMode.NONE);
        assertEquals(150, table.measure(LayoutConstraints.loose(300, 300)).width(), 0.001f);

        table.setFillWidth(true);

        assertEquals(300, table.measure(LayoutConstraints.loose(300, 300)).width(), 0.001f);
        assertEquals(152, table.measure(LayoutConstraints.loose(300, 300)).height(), 0.001f);
    }

    @Test
    void exposesRowsForContextMenus() {
        WidgetManager manager = manager();
        Table table = tableWithRows(Table.SelectionMode.NONE);
        manager.add(table);
        manager.render();

        assertEquals(0, table.rowAt(rowCenter(0)));
        assertEquals(2, table.rowAt(rowCenter(2)));
        assertEquals(-1, table.rowAt(5));
        assertEquals(-1, table.rowAt(400));
    }

    @Test
    void drawsHeaderAndRowIcons() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.HIDPI);
        Table table = new Table();
        table.setBounds(new Rect(120, 80, 300, 200));
        Table.Column column = table.addColumn("Item", 150);
        column.setIcon(new TestTexture(16, 16));
        table.addRow(new Table.Row(Table.Cell.of("Sword", new TestTexture(16, 16))));
        manager.add(table);

        manager.render();

        long icons = backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .count();
        assertEquals(2, icons);
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static boolean hasMarquee(TestBackend backend, Color color) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.color().equals(color));
    }

    private static void click(WidgetManager manager, float x, float y) {
        click(manager, x, y, Modifiers.NONE);
    }

    private static void click(WidgetManager manager, float x, float y, Modifiers modifiers) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, modifiers));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, modifiers));
    }

    private static void drag(WidgetManager manager, float fromX, float toX, float y) {
        manager.handle(new PointerPressed(fromX, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(toX, y));
        manager.handle(new PointerReleased(toX, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static void dragBetween(WidgetManager manager, float fromX, float fromY,
                                    float toX, float toY) {
        manager.handle(new PointerPressed(fromX, fromY, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(toX, toY));
        manager.handle(new PointerReleased(toX, toY, MouseButton.LEFT, Modifiers.NONE));
    }

    private static Table tableWithRows(Table.SelectionMode mode) {
        Table table = new Table();
        table.setBounds(new Rect(0, 0, 300, 250));
        table.setSelectionMode(mode);
        table.addColumn("Name", 150);
        table.addRow("A");
        table.addRow("B");
        table.addRow("C");
        table.addRow("D");
        return table;
    }

    private static float rowCenter(int index) {
        return 32 + 30 * index + 15;
    }

    private static List<String> selectedTexts(Table table) {
        return table.selectedRows().stream().map(row -> row.cell(0).text()).toList();
    }

    private static ScrollBar bar(Table table, ScrollBar.Orientation orientation) {
        return table.children().stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(bar -> bar.orientation() == orientation)
                .findFirst()
                .orElseThrow();
    }

    private static List<String> texts(List<Table.Row> rows) {
        return rows.stream().map(row -> row.cell(0).text()).toList();
    }

    private static boolean hasColor(TestBackend backend, Color color) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.color().equals(color));
    }
}
