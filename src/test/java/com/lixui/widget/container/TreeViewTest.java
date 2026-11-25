package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeViewTest {
    @Test
    void rejectsInvalidConfigurationAndForeignNodes() {
        TreeView tree = new TreeView();
        TreeView other = new TreeView();
        TreeView.Node foreign = other.addRoot("Foreign");

        assertThrows(NullPointerException.class, () -> tree.addRoot(null));
        assertThrows(IllegalArgumentException.class, () -> tree.setRowHeight(0));
        assertThrows(IllegalArgumentException.class, () -> tree.setRowHeight(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> tree.setIndent(-1));
        assertThrows(IllegalArgumentException.class, () -> tree.setIndent(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> tree.setExpansionDuration(-1));
        assertThrows(IllegalArgumentException.class, () -> tree.setExpansionDuration(Float.NaN));
        assertThrows(NullPointerException.class, () -> tree.setExpansionEasing(null));
        assertThrows(IllegalArgumentException.class, () -> tree.nodeAt(0));
        assertThrows(NullPointerException.class, () -> tree.expand(null));
        assertThrows(IllegalArgumentException.class, () -> tree.expand(foreign));

        TreeView.Node root = tree.addRoot("Root");
        TreeView.Node child = root.addChild("Child");
        assertThrows(NullPointerException.class, () -> root.addChild(null));
        assertThrows(IllegalArgumentException.class, () -> tree.select(child));
        assertThrows(NullPointerException.class, () -> tree.select(null));
    }

    @Test
    void flattensExpandedNodesInDepthFirstOrder() {
        TreeView tree = new TreeView();
        TreeView.Node first = tree.addRoot("First");
        TreeView.Node second = tree.addRoot("Second");
        TreeView.Node child = first.addChild("Child");
        TreeView.Node grandchild = child.addChild("Grandchild");

        assertEquals(2, tree.visibleRowCount());
        assertSame(first, tree.nodeAt(0));
        assertSame(second, tree.nodeAt(1));

        tree.expand(first);
        assertEquals(3, tree.visibleRowCount());
        assertSame(child, tree.nodeAt(1));
        assertSame(second, tree.nodeAt(2));

        tree.expand(child);
        assertEquals(4, tree.visibleRowCount());
        assertSame(grandchild, tree.nodeAt(2));
        assertSame(second, tree.nodeAt(3));
        assertSame(child, tree.nodeAt(1));
        assertEquals(1, tree.indexOf(child));
        assertEquals(3, tree.indexOf(second));

        tree.collapse(first);
        assertEquals(2, tree.visibleRowCount());
        assertSame(first, tree.nodeAt(0));
        assertSame(second, tree.nodeAt(1));
    }

    @Test
    void preservesSelectionIdentityWhenRowsShift() {
        TreeView tree = tree();
        TreeView.Node first = tree.addRoot("First");
        TreeView.Node second = tree.addRoot("Second");
        first.addChild("Child");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();

        tree.select(second);
        assertSame(second, tree.selectedNode());

        tree.expand(first);
        assertSame(second, tree.selectedNode());
        assertEquals(2, tree.indexOf(second));
        assertEquals(List.of(2), selectedIndices(tree));
    }

    @Test
    void clickingTheArrowTogglesTheBranchAndTheLabelSelectsTheRow() {
        TreeView tree = tree();
        tree.setExpansionDuration(0);
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("Child");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();

        click(manager, 15, 15);
        assertTrue(root.isExpanded());
        assertEquals(2, tree.visibleRowCount());
        assertNull(tree.selectedNode());

        click(manager, 100, 15);
        assertSame(root, tree.selectedNode());

        click(manager, 15, 15);
        assertFalse(root.isExpanded());
        assertEquals(1, tree.visibleRowCount());
        assertSame(root, tree.selectedNode());
    }

    @Test
    void keyboardExpandsMovesToChildrenAndCollapsesToParent() {
        TreeView tree = tree();
        tree.setExpansionDuration(0);
        TreeView.Node root = tree.addRoot("Root");
        TreeView.Node first = root.addChild("First");
        TreeView.Node second = root.addChild("Second");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();
        click(manager, 100, 15);
        assertSame(root, tree.selectedNode());

        press(manager, Key.RIGHT);
        assertTrue(root.isExpanded());
        assertEquals(3, tree.visibleRowCount());
        assertSame(root, tree.selectedNode());

        press(manager, Key.RIGHT);
        assertSame(first, tree.selectedNode());

        press(manager, Key.DOWN);
        assertSame(second, tree.selectedNode());

        press(manager, Key.LEFT);
        assertSame(root, tree.selectedNode());

        press(manager, Key.LEFT);
        assertFalse(root.isExpanded());
        assertEquals(1, tree.visibleRowCount());

        press(manager, Key.ENTER);
        assertTrue(root.isExpanded());

        press(manager, Key.SPACE);
        assertFalse(root.isExpanded());
    }

    @Test
    void collapsingABranchMovesSelectionToTheCollapsedNode() {
        TreeView tree = tree();
        tree.setExpansionDuration(0);
        TreeView.Node root = tree.addRoot("Root", true);
        TreeView.Node child = root.addChild("Child");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();

        tree.select(child);
        assertSame(child, tree.selectedNode());

        tree.collapse(root);
        assertSame(root, tree.selectedNode());
        assertEquals(1, tree.visibleRowCount());
        assertEquals(List.of(0), selectedIndices(tree));
    }

    @Test
    void animatesBranchHeightAndArrowProgress() {
        TreeView tree = tree();
        tree.setExpansionDuration(0.2f);
        tree.setRowHeight(30);
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("First");
        root.addChild("Second");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();
        assertEquals(30, tree.contentHeight(), 0.001f);

        root.setExpanded(true);
        assertEquals(3, tree.visibleRowCount());
        manager.update(0.1f);
        float halfway = tree.contentHeight();
        assertTrue(halfway > 45 && halfway < 90);

        manager.update(0.11f);
        assertEquals(90, tree.contentHeight(), 0.001f);
        assertEquals(1, root.expansionProgress(), 0.001f);

        root.setExpanded(false);
        manager.update(0.1f);
        float collapsing = tree.contentHeight();
        assertTrue(collapsing > 30 && collapsing < 90);

        manager.update(0.11f);
        assertEquals(30, tree.contentHeight(), 0.001f);
        assertEquals(1, tree.visibleRowCount());
        assertEquals(0, root.expansionProgress(), 0.001f);
    }

    @Test
    void notifiesNodeSelectionChanges() {
        TreeView tree = tree();
        TreeView.Node root = tree.addRoot("Root");
        List<TreeView.Node> events = new ArrayList<>();
        tree.setSelectionListener(events::add);
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();

        click(manager, 100, 15);
        assertSame(root, tree.selectedNode());
        assertEquals(List.of(root), events);
    }

    @Test
    void keyboardSkipsDescendantsOfACollapsingBranch() {
        TreeView tree = tree();
        tree.setExpansionDuration(0.4f);
        TreeView.Node first = tree.addRoot("First", true);
        first.addChild("Child");
        TreeView.Node second = tree.addRoot("Second");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();
        click(manager, 100, 15);
        assertSame(first, tree.selectedNode());

        tree.collapse(first);
        manager.update(0.1f);
        assertEquals(3, tree.visibleRowCount());

        press(manager, Key.DOWN);
        assertSame(second, tree.selectedNode());

        manager.update(0.4f);
        assertEquals(2, tree.visibleRowCount());
        assertSame(second, tree.selectedNode());
    }

    @Test
    void collapsingWithSelectedDescendantSelectsTheBranch() {
        TreeView tree = tree();
        tree.setExpansionDuration(0.4f);
        TreeView.Node root = tree.addRoot("Root", true);
        TreeView.Node child = root.addChild("Child");
        child.addChild("Grandchild");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();

        tree.select(child);
        assertSame(child, tree.selectedNode());

        tree.collapse(root);
        assertSame(root, tree.selectedNode());

        manager.update(0.1f);
        assertSame(root, tree.selectedNode());

        manager.update(0.4f);
        assertSame(root, tree.selectedNode());
        assertEquals(1, tree.visibleRowCount());
    }

    private static TreeView tree() {
        TreeView tree = new TreeView();
        tree.setBounds(new Rect(0, 0, 300, 300));
        return tree;
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static void press(WidgetManager manager, Key key) {
        manager.handle(new KeyPressed(key, Modifiers.NONE, false));
    }

    private static List<Integer> selectedIndices(TreeView tree) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < tree.visibleRowCount(); index++) {
            if (tree.nodeAt(index) == tree.selectedNode()) {
                indices.add(index);
            }
        }
        return indices;
    }
}
