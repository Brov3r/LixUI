package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerExited;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.input.event.PointerScrolled;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualListTest {
    @Test
    void rejectsInvalidConfiguration() {
        VirtualList list = new VirtualList();

        assertThrows(IllegalArgumentException.class, () -> list.setItemCount(-1));
        assertThrows(IllegalArgumentException.class, () -> list.setRowHeight(0));
        assertThrows(IllegalArgumentException.class, () -> list.setRowHeight(Float.NaN));
        assertThrows(NullPointerException.class, () -> list.setRowHeightProvider(null));
        assertThrows(NullPointerException.class, () -> list.setSelectionMode(null));
        assertThrows(NullPointerException.class, () -> list.setSelectedIndices(null));
        assertThrows(IllegalArgumentException.class, () -> list.setScrollY(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> list.scrollToRow(0));

        list.setItemCount(3);
        assertThrows(IllegalArgumentException.class, () -> list.scrollToRow(-1));
        assertThrows(IllegalArgumentException.class, () -> list.scrollToRow(3));
        assertThrows(NullPointerException.class, () -> list.setSelectedIndices(List.of(0, null)));

        list.setRowHeightProvider(index -> index == 1 ? -1 : 20);
        assertThrows(IllegalArgumentException.class, list::contentHeight);

        list.setRowHeightProvider(index -> index == 1 ? Float.NaN : 20);
        assertThrows(IllegalArgumentException.class, list::contentHeight);

        list.setRowHeight(12);
        assertEquals(36, list.contentHeight(), 0.001f);
    }

    @Test
    void mapsFixedHeightPositionsToVisibleRows() {
        VirtualList list = list(10, 20, 200, 80);
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        assertEquals(0, list.rowAt(0));
        assertEquals(0, list.rowAt(19.9f));
        assertEquals(1, list.rowAt(20));
        assertEquals(3, list.rowAt(79.9f));
        assertEquals(-1, list.rowAt(80));
        assertEquals(-1, list.rowAt(-1));
        assertEquals(200, list.contentHeight(), 0.001f);
        assertEquals(120, list.maxScrollY(), 0.001f);

        ScrollBar bar = bar(list);
        assertTrue(bar.isVisible());
        assertEquals(0.4f, bar.visibleFraction(), 0.001f);
        assertEquals(0, bar.value(), 0.001f);

        list.setScrollY(30);
        assertEquals(1, list.rowAt(0));
        assertEquals(1, list.rowAt(9.9f));
        assertEquals(2, list.rowAt(10));

        list.setScrollY(10_000);
        assertEquals(120, list.scrollY(), 0.001f);
        assertEquals(1, bar.value(), 0.001f);
        assertEquals(6, list.rowAt(0));
        assertEquals(9, list.rowAt(79.9f));
        assertEquals(-1, list.rowAt(80));
    }

    @Test
    void locatesRowsWithVariableHeightsUsingPrefixSums() {
        VirtualList list = new VirtualList();
        list.setBounds(new Rect(0, 0, 200, 50));
        float[] heights = {10, 20, 30, 40};
        list.setItemCount(heights.length);
        list.setRowHeightProvider(index -> heights[index]);

        assertEquals(100, list.contentHeight(), 0.001f);
        assertEquals(50, list.maxScrollY(), 0.001f);
        assertEquals(0, list.rowAt(0));
        assertEquals(0, list.rowAt(9.9f));
        assertEquals(1, list.rowAt(10));
        assertEquals(1, list.rowAt(29.9f));
        assertEquals(2, list.rowAt(30));
        assertEquals(2, list.rowAt(49.9f));

        list.setScrollY(50);
        assertEquals(2, list.rowAt(0));
        assertEquals(3, list.rowAt(10));
        assertEquals(3, list.rowAt(49.9f));
        assertEquals(-1, list.rowAt(50));

        list.setScrollY(15);
        assertEquals(1, list.rowAt(0));
        assertEquals(1, list.rowAt(14.9f));
        assertEquals(2, list.rowAt(15));
    }

    @Test
    void recomputesOnlyAfterExplicitInvalidationOrCountChanges() {
        VirtualList list = new VirtualList();
        list.setBounds(new Rect(0, 0, 200, 100));
        float[] heights = {10, 20, 30};
        list.setItemCount(3);
        list.setRowHeightProvider(index -> heights[index]);
        assertEquals(60, list.contentHeight(), 0.001f);

        heights[1] = 40;
        assertEquals(60, list.contentHeight(), 0.001f);

        list.invalidateRowHeights();
        assertEquals(80, list.contentHeight(), 0.001f);
        assertEquals(1, list.rowAt(15));

        list.setItemCount(2);
        assertEquals(50, list.contentHeight(), 0.001f);

        list.setRowHeight(25);
        assertEquals(50, list.contentHeight(), 0.001f);
    }

    @Test
    void itemCountAssignmentRefreshesCachedHeightsEvenWhenTheCountIsUnchanged() {
        VirtualList list = new VirtualList();
        list.setBounds(new Rect(0, 0, 200, 100));
        float[] heights = {10, 20, 30};
        list.setItemCount(heights.length);
        list.setRowHeightProvider(index -> heights[index]);
        assertEquals(60, list.contentHeight(), 0.001f);

        heights[1] = 40;
        list.setItemCount(heights.length);

        assertEquals(80, list.contentHeight(), 0.001f);
    }

    @Test
    void fixedHeightsUseConstantMemoryForVeryLargeCounts() {
        VirtualList list = new VirtualList();
        list.setBounds(new Rect(0, 0, 200, 80));
        list.setRowHeight(20);
        list.setItemCount(Integer.MAX_VALUE);

        assertTrue(list.contentHeight() > 0);
        assertTrue(list.maxScrollY() > 0);
    }

    @Test
    void variableHeightsAccumulateInDoublePrecision() {
        VirtualList list = new VirtualList();
        list.setBounds(new Rect(0, 0, 200, 100));
        int count = 100_000;
        list.setItemCount(count);
        list.setRowHeightProvider(index -> 0.1f);

        double expected = count * (double) 0.1f;
        assertEquals(expected, list.contentHeight(), 0.01);
    }

    @Test
    void rejectsCountsThatWouldOverflowTheVariablePrefixTable() {
        VirtualList fixed = new VirtualList();
        fixed.setItemCount(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> fixed.setRowHeightProvider(index -> 10));

        VirtualList variable = new VirtualList();
        variable.setRowHeightProvider(index -> 10);
        assertThrows(IllegalArgumentException.class,
                () -> variable.setItemCount(Integer.MAX_VALUE));
    }

    @Test
    void rendersOnlyVisibleRows() {
        VirtualList list = list(10, 20, 200, 80);
        List<Integer> rendered = new ArrayList<>();
        list.setRowRenderer((index, bounds, state, renderer) -> rendered.add(index));
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        assertEquals(List.of(0, 1, 2, 3), rendered);

        rendered.clear();
        list.setScrollY(40);
        manager.render();

        assertEquals(List.of(2, 3, 4, 5), rendered);
    }

    @Test
    void reportsHoverForTheRowUnderThePointer() {
        VirtualList list = list(10, 20, 200, 80);
        Map<Integer, VirtualList.RowState> states = new HashMap<>();
        list.setRowRenderer((index, bounds, state, renderer) -> states.put(index, state));
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        manager.handle(new PointerMoved(50, 30));
        manager.render();

        assertTrue(states.get(1).hovered());
        assertFalse(states.get(0).hovered());

        manager.handle(new PointerScrolled(50, 30, 0, -1));
        assertEquals(48, list.scrollY(), 0.001f);
        states.clear();
        manager.render();
        assertTrue(states.get(3).hovered());
        assertFalse(states.containsKey(1));

        manager.handle(new PointerExited(50, 30));
        states.clear();
        manager.render();
        assertTrue(states.values().stream().noneMatch(VirtualList.RowState::hovered));
    }

    @Test
    void supportsSingleAndMultipleSelection() {
        VirtualList list = list(10, 20, 300, 200);
        list.setSelectionMode(VirtualList.SelectionMode.MULTIPLE);
        List<Integer> listenerSizes = new ArrayList<>();
        list.setSelectionListener(selection -> listenerSizes.add(selection.size()));
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        click(manager, 50, 10);
        assertEquals(List.of(0), list.selectedIndices());

        click(manager, 50, 50, new Modifiers(false, true, false, false));
        assertEquals(List.of(0, 2), list.selectedIndices());
        assertEquals(2, list.selectedIndices().size());

        click(manager, 50, 70, new Modifiers(true, false, false, false));
        assertEquals(List.of(2, 3), list.selectedIndices());

        click(manager, 50, 30);
        assertEquals(List.of(1), list.selectedIndices());
        assertEquals(List.of(1, 2, 2, 1), listenerSizes);

        list.setSelectionMode(VirtualList.SelectionMode.SINGLE);
        click(manager, 50, 50, new Modifiers(false, true, false, false));
        assertEquals(List.of(2), list.selectedIndices());

        list.setSelectionMode(VirtualList.SelectionMode.NONE);
        click(manager, 50, 90);
        assertTrue(list.selectedIndices().isEmpty());
    }

    @Test
    void clearsSelectionWhenThePressMissesEveryRow() {
        VirtualList list = list(2, 20, 200, 80);
        list.setSelectionMode(VirtualList.SelectionMode.SINGLE);
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        click(manager, 50, 10);
        assertEquals(List.of(0), list.selectedIndices());

        click(manager, 50, 70);
        assertTrue(list.selectedIndices().isEmpty());
        assertEquals(-1, list.rowAt(70));
    }

    @Test
    void prunesSelectionAndIgnoresOutOfRangeIndices() {
        VirtualList list = list(5, 20, 200, 200);
        list.setSelectionMode(VirtualList.SelectionMode.MULTIPLE);
        list.setSelectedIndices(List.of(1, 4, 9, -2));
        assertEquals(List.of(1, 4), list.selectedIndices());

        list.setItemCount(3);
        assertEquals(List.of(1), list.selectedIndices());

        list.setItemCount(1);
        assertTrue(list.selectedIndices().isEmpty());
        assertFalse(list.isSelected(0));
    }

    @Test
    void keyboardMovesSelectionAndKeepsTheActiveRowVisible() {
        VirtualList list = list(10, 20, 200, 80);
        list.setSelectionMode(VirtualList.SelectionMode.SINGLE);
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();
        click(manager, 50, 30);

        press(manager, Key.DOWN, Modifiers.NONE);
        assertEquals(List.of(2), list.selectedIndices());
        assertEquals(0, list.scrollY(), 0.001f);

        press(manager, Key.UP, Modifiers.NONE);
        assertEquals(List.of(1), list.selectedIndices());

        press(manager, Key.END, Modifiers.NONE);
        assertEquals(List.of(9), list.selectedIndices());
        assertEquals(120, list.scrollY(), 0.001f);

        press(manager, Key.HOME, Modifiers.NONE);
        assertEquals(List.of(0), list.selectedIndices());
        assertEquals(0, list.scrollY(), 0.001f);

        press(manager, Key.PAGE_DOWN, Modifiers.NONE);
        assertEquals(List.of(4), list.selectedIndices());
        assertEquals(20, list.scrollY(), 0.001f);

        press(manager, Key.PAGE_UP, Modifiers.NONE);
        assertEquals(List.of(0), list.selectedIndices());
        assertEquals(0, list.scrollY(), 0.001f);

        press(manager, Key.DOWN, Modifiers.NONE);
        assertEquals(1, list.activeIndex());
    }

    @Test
    void keyboardExtendsAndTogglesMultipleSelection() {
        VirtualList list = list(10, 20, 200, 80);
        list.setSelectionMode(VirtualList.SelectionMode.MULTIPLE);
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();
        click(manager, 50, 50);

        press(manager, Key.DOWN, new Modifiers(true, false, false, false));
        assertEquals(List.of(2, 3), list.selectedIndices());

        press(manager, Key.SPACE, Modifiers.NONE);
        assertEquals(List.of(2), list.selectedIndices());

        press(manager, Key.SPACE, Modifiers.NONE);
        assertEquals(List.of(2, 3), list.selectedIndices());
    }

    @Test
    void wheelScrollsAndClampsAtTheEdges() {
        VirtualList list = list(10, 20, 200, 80);
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        manager.handle(new PointerScrolled(50, 10, 0, -1));
        assertEquals(48, list.scrollY(), 0.001f);

        manager.handle(new PointerScrolled(50, 10, 0, -100));
        assertEquals(120, list.scrollY(), 0.001f);

        manager.handle(new PointerScrolled(50, 10, 0, 1));
        assertEquals(72, list.scrollY(), 0.001f);
    }

    @Test
    void scrollbarDragReachesTheEndOfTheContent() {
        VirtualList list = list(10, 20, 200, 80);
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        ScrollBar bar = bar(list);
        float x = bar.bounds().x() + bar.bounds().width() / 2;
        float start = bar.bounds().y() + bar.bounds().height() / 2;
        float end = bar.bounds().bottom();

        manager.handle(new PointerPressed(x, start, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(x, end));
        manager.handle(new PointerReleased(x, end, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(120, list.scrollY(), 0.001f);
        assertEquals(1, bar.value(), 0.001f);
    }

    @Test
    void keepsZeroSizedListsEmpty() {
        VirtualList list = new VirtualList();
        WidgetManager manager = manager();
        manager.add(list);
        manager.render();

        assertEquals(0, list.contentHeight(), 0.001f);
        assertEquals(0, list.maxScrollY(), 0.001f);
        assertEquals(-1, list.rowAt(0));
        assertFalse(bar(list).isVisible());
    }

    private static VirtualList list(int count, float rowHeight, float width, float height) {
        VirtualList list = new VirtualList();
        list.setBounds(new Rect(0, 0, width, height));
        list.setRowHeight(rowHeight);
        list.setItemCount(count);
        return list;
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        click(manager, x, y, Modifiers.NONE);
    }

    private static void click(WidgetManager manager, float x, float y, Modifiers modifiers) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, modifiers));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, modifiers));
    }

    private static void press(WidgetManager manager, Key key, Modifiers modifiers) {
        manager.handle(new KeyPressed(key, modifiers, false));
    }

    private static ScrollBar bar(VirtualList list) {
        return list.children().stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
