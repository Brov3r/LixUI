package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.Texture;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.image.ImageSource;
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
    void drawsTheExpansionArrowAsARotatingIcon() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TreeView tree = tree();
            tree.setExpansionDuration(0.2f);
            TreeView.Node root = tree.addRoot("Root");
            root.addChild("Child");
            manager.add(tree);
            manager.render();

            ImageCommand collapsed = arrowCommand(backend);
            assertEquals(1, collapsed.transform().m00(), 0.001f);
            assertEquals(0, collapsed.transform().m01(), 0.001f);

            click(manager, 15, 15);
            assertTrue(root.isExpanded());
            manager.update(0.1f);
            manager.render();
            ImageCommand expanding = arrowCommand(backend);
            assertTrue(expanding.transform().m00() > 0.5f);
            assertTrue(expanding.transform().m00() < 0.9f);

            manager.update(0.11f);
            manager.render();
            ImageCommand expanded = arrowCommand(backend);
            assertEquals(0, expanded.transform().m00(), 0.001f);
            assertEquals(-1, expanded.transform().m01(), 0.001f);

            click(manager, 15, 15);
            assertFalse(root.isExpanded());
            manager.update(0.1f);
            manager.render();
            ImageCommand collapsing = arrowCommand(backend);
            assertTrue(collapsing.transform().m00() > 0.5f);
            assertTrue(collapsing.transform().m00() < 0.9f);
        }
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

    @Test
    void drawsNodeIconsBeforeTheLabelAndKeepsTheDepthIndent() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TreeView tree = tree();
            TreeView.Node root = tree.addRoot("Root", true);
            TreeView.Node child = root.addChild("Child");
            root.setIcon(ImageSource.icon("folder.svg"));
            child.setIcon(ImageSource.icon("file.svg"));
            manager.add(tree);
            manager.render();

            List<ImageCommand> icons = imageCommands(backend, 32);
            assertEquals(2, icons.size());
            assertEquals(28, icons.get(0).bounds().x(), 0.001f);
            assertEquals(6, icons.get(0).bounds().y(), 0.001f);
            assertEquals(16, icons.get(0).bounds().width(), 0.001f);
            assertEquals(46, icons.get(1).bounds().x(), 0.001f);
            assertEquals(34, icons.get(1).bounds().y(), 0.001f);
        }
    }

    @Test
    void drawsDisabledNodeIconsMuted() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TreeView tree = tree();
            TreeView.Node root = tree.addRoot("Locked");
            root.setIcon(ImageSource.icon("folder.svg"));
            root.setDisabled(true);
            manager.add(tree);
            manager.render();

            ImageCommand icon = imageCommands(backend, 32).getFirst();
            assertEquals(manager.context().theme().textMuted().withAlpha(0.45f), icon.color());
        }
    }

    @Test
    void disabledNodesIgnoreClicksAndKeyboard() {
        TreeView tree = tree();
        tree.setExpansionDuration(0);
        TreeView.Node enabled = tree.addRoot("Enabled");
        TreeView.Node locked = tree.addRoot("Locked");
        TreeView.Node lockedChild = locked.addChild("Locked child");
        TreeView.Node other = tree.addRoot("Other");
        locked.setExpanded(true);
        locked.setDisabled(true);
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();
        assertEquals(4, tree.visibleRowCount());

        click(manager, 100, 43);
        assertNull(tree.selectedNode());

        click(manager, 100, 15);
        assertSame(enabled, tree.selectedNode());
        click(manager, 100, 43);
        assertSame(enabled, tree.selectedNode());
        click(manager, 100, 71);
        assertSame(enabled, tree.selectedNode());

        click(manager, 15, 43);
        assertTrue(locked.isExpanded());

        press(manager, Key.DOWN);
        assertSame(other, tree.selectedNode());
        press(manager, Key.UP);
        assertSame(enabled, tree.selectedNode());
        assertThrows(IllegalStateException.class, () -> tree.select(locked));
        assertThrows(IllegalStateException.class, () -> tree.select(lockedChild));

        locked.setDisabled(false);
        tree.select(locked);
        assertSame(locked, tree.selectedNode());
    }

    @Test
    void disablingASelectedBranchClearsTheSelection() {
        TreeView tree = tree();
        tree.setExpansionDuration(0);
        TreeView.Node root = tree.addRoot("Root", true);
        TreeView.Node child = root.addChild("Child");
        WidgetManager manager = manager();
        manager.add(tree);
        manager.render();
        tree.select(child);
        assertSame(child, tree.selectedNode());

        List<TreeView.Node> changes = new ArrayList<>();
        tree.setSelectionListener(changes::add);
        root.setDisabled(true);

        assertNull(tree.selectedNode());
        assertEquals(1, changes.size());
        assertNull(changes.getFirst());
        assertTrue(child.isDisabled());
        assertThrows(IllegalStateException.class, () -> tree.select(child));

        root.setDisabled(false);
        assertNull(tree.selectedNode());
        assertFalse(child.isDisabled());
    }

    @Test
    void rejectsRemoteNodeIcons() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");

        assertThrows(IllegalArgumentException.class,
                () -> root.setIcon(ImageSource.remote("https://example.com/icon.png")));
        assertNull(root.icon());
    }

    @Test
    void reloadsAndReleasesNodeIconsWithTheContext() {
        TestBackend backend = new TestBackend();
        TreeView tree = tree();
        TreeView.Node root = tree.addRoot("Root");
        root.setIcon(ImageSource.icon("folder.svg"));
        try (WidgetManager manager = manager(backend)) {
            manager.add(tree);
            manager.render();
            Texture hidpiIcon = texture(backend, 32, 32);
            texture(backend, 20, 20);

            manager.resize(TestViewports.LOGICAL);
            manager.render();

            assertTrue(backend.destroyed().contains(hidpiIcon));
            texture(backend, 16, 16);
            texture(backend, 10, 10);
        }
        assertTrue(backend.destroyed().containsAll(backend.created()));
    }

    private static TreeView tree() {
        TreeView tree = new TreeView();
        tree.setBounds(new Rect(0, 0, 300, 300));
        return tree;
    }

    private static WidgetManager manager() {
        return manager(new TestBackend());
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static ImageCommand arrowCommand(TestBackend backend) {
        return backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .map(ImageCommand.class::cast)
                .filter(command -> command.texture().width() == 20)
                .findFirst().orElseThrow();
    }

    private static List<ImageCommand> imageCommands(TestBackend backend, int width) {
        return backend.lastDrawList().commands().stream()
                .filter(ImageCommand.class::isInstance)
                .map(ImageCommand.class::cast)
                .filter(command -> command.texture().width() == width)
                .toList();
    }

    private static Texture texture(TestBackend backend, int width, int height) {
        return backend.created().stream()
                .filter(texture -> texture.width() == width && texture.height() == height)
                .findFirst().orElseThrow();
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
