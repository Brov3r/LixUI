package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.widget.DragSession;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReorderableListTest {
    private static final Rect LIST_BOUNDS = new Rect(50, 40, 320, 260);
    private static final float GRIP_X = 60;
    private static final float HEADER = 32;
    private static final float ROW_HEIGHT = 30;

    @Test
    void draggingAGripMovesTheRowToTheDropSlot() {
        WidgetManager manager = manager();
        ReorderableList list = list();
        Table.Row first = list.rows().get(0);
        List<int[]> reorders = new ArrayList<>();
        list.setOnReorder((from, to) -> reorders.add(new int[]{from, to}));
        manager.add(list);
        manager.layout();

        DragSession session = startDrag(manager, 0, 4);

        assertNotNull(session);
        assertTrue(session.isActive());
        manager.handle(new PointerReleased(GRIP_X, rowCenter(4), MouseButton.LEFT,
                Modifiers.NONE));

        assertEquals(List.of(first), list.rows().subList(3, 4));
        assertEquals(List.of("B", "C", "D", "A"), names(list));
        assertEquals(1, reorders.size());
        assertEquals(0, reorders.get(0)[0]);
        assertEquals(3, reorders.get(0)[1]);
        assertTrue(session.isHandled());
        assertNull(manager.context().dragSession());
    }

    @Test
    void selectedBlockMovesAsOneBlockAndKeepsItsSelection() {
        WidgetManager manager = manager();
        ReorderableList list = list();
        List<Table.Row> rows = list.rows();
        list.setSelectedRows(List.of(rows.get(0), rows.get(1)));
        List<int[]> reorders = new ArrayList<>();
        list.setOnReorder((from, to) -> reorders.add(new int[]{from, to}));
        manager.add(list);
        manager.layout();

        startDrag(manager, 0, 4);
        manager.handle(new PointerReleased(GRIP_X, rowCenter(4), MouseButton.LEFT,
                Modifiers.NONE));

        assertEquals(List.of("C", "D", "A", "B"), names(list));
        assertEquals(1, reorders.size());
        assertEquals(0, reorders.get(0)[0]);
        assertEquals(2, reorders.get(0)[1]);
        assertEquals(List.of(rows.get(0), rows.get(1)), list.selectedRows());
    }

    @Test
    void nonContiguousSelectionBecomesOneBlockAndReportsFromAndTo() {
        WidgetManager manager = manager();
        ReorderableList list = list();
        List<Table.Row> rows = list.rows();
        list.setSelectedRows(List.of(rows.get(0), rows.get(2)));
        List<int[]> reorders = new ArrayList<>();
        list.setOnReorder((from, to) -> reorders.add(new int[]{from, to}));
        manager.add(list);
        manager.layout();

        startDrag(manager, 0, 4);
        manager.handle(new PointerReleased(GRIP_X, rowCenter(4), MouseButton.LEFT,
                Modifiers.NONE));

        assertEquals(List.of("B", "D", "A", "C"), names(list));
        assertEquals(1, reorders.size());
        assertEquals(0, reorders.get(0)[0]);
        assertEquals(2, reorders.get(0)[1]);
        assertEquals(List.of(rows.get(0), rows.get(2)), list.selectedRows());
    }

    @Test
    void draggingOutsideTheGripDoesNotStartAReorder() {
        WidgetManager manager = manager();
        ReorderableList list = list();
        List<int[]> reorders = new ArrayList<>();
        list.setOnReorder((from, to) -> reorders.add(new int[]{from, to}));
        manager.add(list);
        manager.layout();

        manager.handle(new PointerPressed(120, rowCenter(0), MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(120, rowCenter(4)));

        assertNull(manager.context().dragSession());
        manager.handle(new PointerReleased(120, rowCenter(4), MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of("A", "B", "C", "D"), names(list));
        assertTrue(reorders.isEmpty());
    }

    @Test
    void releaseOutsideOrEscapeKeepsTheOriginalOrder() {
        WidgetManager manager = manager();
        ReorderableList list = list();
        List<int[]> reorders = new ArrayList<>();
        list.setOnReorder((from, to) -> reorders.add(new int[]{from, to}));
        manager.add(list);
        manager.layout();

        startDrag(manager, 0, 4);
        manager.handle(new PointerReleased(600, 400, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of("A", "B", "C", "D"), names(list));
        assertTrue(reorders.isEmpty());

        startDrag(manager, 0, 3);
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertEquals(List.of("A", "B", "C", "D"), names(list));
        assertTrue(reorders.isEmpty());
        assertNull(manager.context().dragSession());
    }

    @Test
    void addsRowsWithAGripCellAndExposesOnlyDataColumns() {
        ReorderableList list = list();

        assertEquals(2, list.columns().size());
        assertEquals("Name", list.columns().get(0).title());
        Table.Row row = list.rows().get(0);
        assertTrue(row.cell(0).text().isEmpty());
        assertEquals("A", row.cell(1).text());
        assertEquals(4, list.rows().size());

        Table.Column role = list.columns().get(1);
        assertTrue(list.removeColumn(role));
        assertEquals(1, list.columns().size());

        list.clearRows();
        assertTrue(list.rows().isEmpty());
        assertTrue(list.selectedRows().isEmpty());
    }

    private static DragSession startDrag(WidgetManager manager, int row, int targetRow) {
        manager.handle(new PointerPressed(GRIP_X, rowCenter(row), MouseButton.LEFT,
                Modifiers.NONE));
        manager.handle(new PointerMoved(GRIP_X, rowCenter(targetRow)));
        return manager.context().dragSession();
    }

    private static float rowCenter(int row) {
        return LIST_BOUNDS.y() + HEADER + row * ROW_HEIGHT + ROW_HEIGHT / 2;
    }

    private static List<String> names(ReorderableList list) {
        List<String> names = new ArrayList<>();
        for (Table.Row row : list.rows()) {
            names.add(row.cell(1).text());
        }
        return names;
    }

    private static ReorderableList list() {
        ReorderableList list = new ReorderableList();
        list.setBounds(LIST_BOUNDS);
        list.setFillWidth(true);
        list.addColumn("Name", 120);
        list.addColumn("Role", 100);
        list.addRow("A", "Alpha");
        list.addRow("B", "Beta");
        list.addRow("C", "Gamma");
        list.addRow("D", "Delta");
        return list;
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.HIDPI);
        return manager;
    }
}
