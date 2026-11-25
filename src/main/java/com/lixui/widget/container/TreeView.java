package com.lixui.widget.container;

import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Transform;
import com.lixui.graphics.Color;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.image.ImageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A scrollable tree of collapsible nodes.
 * Visible nodes are flattened into a {@link VirtualList}, so only rows inside the viewport are drawn
 * and hit-tested. Children are indented by depth, the expansion arrow rotates, and the rows of a
 * branch grow or shrink with the expansion animation. Arrow clicks and the Left, Right, Enter and
 * Space keys toggle branches, while the remaining navigation keys and pointer selection follow the
 * virtual list conventions.
 * A node optionally draws an icon between the expansion arrow and its label. Disabled nodes and
 * all of their descendants are drawn muted and ignore pointer and keyboard interaction.
 */
public class TreeView extends Widget {
    /**
     * A node in a {@link TreeView}.
     * Nodes keep their expansion state and ordered children, and belong to exactly one tree.
     */
    public static final class Node {
        private final TreeView owner;
        private final List<Node> children = new ArrayList<>();
        private final AnimationSlot animation = new AnimationSlot();
        private Node parent;
        private String label;
        private ImageSource icon;
        private IconSet iconSet;
        private boolean disabled;
        private boolean expanded;
        private float expandedAmount;

        private Node(TreeView owner, String label) {
            this.owner = owner;
            this.label = label;
        }

        /**
         * Returns the node label.
         *
         * @return the non-null label
         */
        public String label() {
            return label;
        }

        /**
         * Replaces the node label.
         *
         * @param label non-null label
         * @throws NullPointerException if the label is null
         */
        public void setLabel(String label) {
            this.label = Objects.requireNonNull(label, "label");
        }

        /**
         * Returns the node icon source.
         *
         * @return the icon source, or null when the node has no icon
         */
        public ImageSource icon() {
            return icon;
        }

        /**
         * Assigns the node icon drawn in the slot before the label.
         * Caller-owned texture sources are never released by the tree. Bundled icons are acquired
         * through the shared context icon cache, reloaded when the display density changes, and
         * released when the tree detaches. Remote sources are not supported.
         *
         * @param icon icon source, or null to clear the icon
         * @throws IllegalArgumentException if the source is a remote image
         */
        public void setIcon(ImageSource icon) {
            if (icon instanceof ImageSource.RemoteSource) {
                throw new IllegalArgumentException("Remote node icons are not supported.");
            }
            if (Objects.equals(this.icon, icon)) {
                return;
            }
            owner.releaseNodeIcon(this);
            this.icon = icon;
            this.iconSet = icon instanceof ImageSource.IconSource iconSource
                    ? new IconSet(iconSource.name()) : null;
            owner.ensureNodeIcon(this);
        }

        /**
         * Reports whether this node or one of its ancestors is disabled.
         * Disabled nodes and their descendants draw muted, ignore pointer and keyboard
         * interaction, and cannot be selected programmatically.
         *
         * @return true when this node or an ancestor is disabled
         */
        public boolean isDisabled() {
            for (Node node = this; node != null; node = node.parent) {
                if (node.disabled) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Disables or enables this node and its descendants.
         * Disabling a branch clears a selection inside it. Expansion and collapse can still be
         * changed programmatically so the branch can be inspected.
         *
         * @param disabled true to draw the branch muted and ignore interaction
         */
        public void setDisabled(boolean disabled) {
            owner.setNodeDisabled(this, disabled);
        }

        /**
         * Returns the parent node.
         *
         * @return the parent, or null for a root node
         */
        public Node parent() {
            return parent;
        }

        /**
         * Returns the children in display order.
         *
         * @return an immutable snapshot of the children
         */
        public List<Node> children() {
            return List.copyOf(children);
        }

        /**
         * Reports whether the node has at least one child.
         *
         * @return true when children are present
         */
        public boolean hasChildren() {
            return !children.isEmpty();
        }

        /**
         * Reports whether the node is expanded.
         *
         * @return true when the children are shown
         */
        public boolean isExpanded() {
            return expanded;
        }

        /**
         * Expands or collapses the node with the tree animation.
         * Nodes without children ignore the call.
         *
         * @param expanded true to show the children
         */
        public void setExpanded(boolean expanded) {
            owner.setNodeExpanded(this, expanded, true);
        }

        /**
         * Returns the animated expansion progress.
         *
         * @return a value in the {@code [0, 1]} range
         */
        public float expansionProgress() {
            return expandedAmount;
        }

        /**
         * Appends a child node.
         *
         * @param label non-null child label
         * @return the created child
         * @throws NullPointerException if the label is null
         */
        public Node addChild(String label) {
            Node child = new Node(owner, Objects.requireNonNull(label, "label"));
            child.parent = this;
            children.add(child);
            owner.invalidateRows();
            return child;
        }
    }

    private static final float DEFAULT_ROW_HEIGHT = 28;
    private static final float DEFAULT_INDENT = 18;
    private static final float DEFAULT_EXPANSION_SECONDS = 0.2f;
    private static final float ROW_PADDING = 10;
    private static final float ARROW_SIZE = 10;
    private static final float ARROW_GAP = 8;
    private static final float NODE_ICON_SIZE = 16;
    private static final float NODE_ICON_GAP = 8;
    private static final float DISABLED_ALPHA = 0.45f;
    private static final float MIN_ANIMATED_ROW_HEIGHT = 0.01f;
    private static final float ANIMATION_SNAP = 0.001f;

    private final List<Node> roots = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final TreeList list;
    private final IconSet icons = new IconSet("arrow-right-04.svg");

    private Consumer<Node> selectionListener;
    private Node selectedNode;
    private boolean syncingSelection;
    private boolean rowsDirty = true;
    private float rowHeight = DEFAULT_ROW_HEIGHT;
    private float indent = DEFAULT_INDENT;
    private float expansionDuration = DEFAULT_EXPANSION_SECONDS;
    private Easing expansionEasing = Easing.EASE_IN_OUT;

    /**
     * Creates an empty tree with keyboard focus and scrolling enabled.
     */
    public TreeView() {
        setClipToBounds(true);
        list = new TreeList();
        add(list);
    }

    /**
     * Returns the root nodes in display order.
     *
     * @return an immutable snapshot of the roots
     */
    public List<Node> roots() {
        return List.copyOf(roots);
    }

    /**
     * Appends a collapsed root node.
     *
     * @param label non-null node label
     * @return the created root
     * @throws NullPointerException if the label is null
     */
    public Node addRoot(String label) {
        return addRoot(label, false);
    }

    /**
     * Appends a root node with an explicit initial expansion state.
     * The initial state is applied without animation.
     *
     * @param label non-null node label
     * @param expanded true to show the children immediately
     * @return the created root
     * @throws NullPointerException if the label is null
     */
    public Node addRoot(String label, boolean expanded) {
        Node node = new Node(this, Objects.requireNonNull(label, "label"));
        node.expanded = expanded;
        node.expandedAmount = expanded ? 1 : 0;
        roots.add(node);
        invalidateRows();
        return node;
    }

    /**
     * Returns the number of visible rows.
     *
     * @return the flattened visible row count
     */
    public int visibleRowCount() {
        ensureRows();
        return rows.size();
    }

    /**
     * Returns the visible node at a flattened row index.
     *
     * @param visibleIndex zero-based visible row index
     * @return the node at the supplied row
     * @throws IllegalArgumentException if the index is out of range
     */
    public Node nodeAt(int visibleIndex) {
        ensureRows();
        if (visibleIndex < 0 || visibleIndex >= rows.size()) {
            throw new IllegalArgumentException("Visible row index is out of range.");
        }
        return rows.get(visibleIndex).node;
    }

    /**
     * Returns the flattened row index of a visible node.
     *
     * @param node node to find
     * @return the zero-based visible row index, or -1 when the node is hidden
     * @throws NullPointerException if the node is null
     */
    public int indexOf(Node node) {
        Objects.requireNonNull(node, "node");
        ensureRows();
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).node == node) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Returns the selected node.
     *
     * @return the selected node, or null when the selection is empty
     */
    public Node selectedNode() {
        return selectedNode;
    }

    /**
     * Selects a visible node and scrolls it into view.
     *
     * @param node node to select
     * @throws NullPointerException if the node is null
     * @throws IllegalArgumentException if the node does not belong to this tree or is hidden
     * @throws IllegalStateException if the node or one of its ancestors is disabled
     */
    public void select(Node node) {
        Objects.requireNonNull(node, "node");
        requireOwned(node);
        if (node.isDisabled()) {
            throw new IllegalStateException("Node is disabled.");
        }
        int index = indexOf(node);
        if (index < 0) {
            throw new IllegalArgumentException("Node is not visible.");
        }
        list.setSelectedIndices(List.of(index));
        list.scrollToRow(index);
    }

    /**
     * Returns the callback invoked after the selected node changes.
     *
     * @return the selection listener, or null when none is assigned
     */
    public Consumer<Node> selectionListener() {
        return selectionListener;
    }

    /**
     * Assigns a callback invoked after the selected node changes.
     *
     * @param selectionListener listener receiving the selected node or null, or null to clear it
     */
    public void setSelectionListener(Consumer<Node> selectionListener) {
        this.selectionListener = selectionListener;
    }

    /**
     * Expands a node with the configured animation.
     * Nodes without children ignore the call. Disabled nodes may still be expanded
     * programmatically, but their descendants stay muted and inert.
     *
     * @param node node to expand
     * @throws NullPointerException if the node is null
     * @throws IllegalArgumentException if the node does not belong to this tree
     */
    public void expand(Node node) {
        Objects.requireNonNull(node, "node");
        requireOwned(node);
        setNodeExpanded(node, true, true);
    }

    /**
     * Collapses a node with the configured animation.
     * Nodes without children ignore the call.
     *
     * @param node node to collapse
     * @throws NullPointerException if the node is null
     * @throws IllegalArgumentException if the node does not belong to this tree
     */
    public void collapse(Node node) {
        Objects.requireNonNull(node, "node");
        requireOwned(node);
        setNodeExpanded(node, false, true);
    }

    /**
     * Expands a collapsed node or collapses an expanded node.
     *
     * @param node node to toggle
     * @throws NullPointerException if the node is null
     * @throws IllegalArgumentException if the node does not belong to this tree
     */
    public void toggle(Node node) {
        Objects.requireNonNull(node, "node");
        requireOwned(node);
        setNodeExpanded(node, !node.expanded, true);
    }

    /**
     * Returns the base height of one row.
     *
     * @return the row height in logical UI units
     */
    public float rowHeight() {
        return rowHeight;
    }

    /**
     * Sets the base height of one row.
     *
     * @param rowHeight positive row height in logical UI units
     * @throws IllegalArgumentException if the height is not finite or not positive
     */
    public void setRowHeight(float rowHeight) {
        if (!Float.isFinite(rowHeight) || rowHeight <= 0) {
            throw new IllegalArgumentException("Row height must be finite and positive.");
        }
        this.rowHeight = rowHeight;
        list.invalidateRowHeights();
    }

    /**
     * Returns the horizontal indent added for each depth level.
     *
     * @return the indent in logical UI units
     */
    public float indent() {
        return indent;
    }

    /**
     * Sets the horizontal indent added for each depth level.
     *
     * @param indent non-negative indent in logical UI units
     * @throws IllegalArgumentException if the indent is negative or non-finite
     */
    public void setIndent(float indent) {
        if (!Float.isFinite(indent) || indent < 0) {
            throw new IllegalArgumentException("Indent must be finite and non-negative.");
        }
        this.indent = indent;
    }

    /**
     * Returns the expansion animation duration.
     *
     * @return the duration in seconds, or zero when changes apply instantly
     */
    public float expansionDuration() {
        return expansionDuration;
    }

    /**
     * Sets the expansion animation duration.
     * Zero disables the animation and applies expansion changes instantly.
     *
     * @param expansionDuration non-negative duration in seconds
     * @throws IllegalArgumentException if the duration is negative or non-finite
     */
    public void setExpansionDuration(float expansionDuration) {
        if (!Float.isFinite(expansionDuration) || expansionDuration < 0) {
            throw new IllegalArgumentException("Expansion duration must be finite and non-negative.");
        }
        this.expansionDuration = expansionDuration;
    }

    /**
     * Returns the expansion animation timing curve.
     *
     * @return the timing curve
     */
    public Easing expansionEasing() {
        return expansionEasing;
    }

    /**
     * Sets the expansion animation timing curve.
     *
     * @param expansionEasing timing curve
     * @throws NullPointerException if the curve is null
     */
    public void setExpansionEasing(Easing expansionEasing) {
        this.expansionEasing = Objects.requireNonNull(expansionEasing, "expansionEasing");
    }

    /**
     * Returns the total height of the visible rows.
     *
     * @return the content height in logical UI units
     */
    public float contentHeight() {
        ensureRows();
        return list.contentHeight();
    }

    /**
     * Lays the internal list over the whole tree bounds.
     */
    @Override
    protected void onLayout() {
        ensureRows();
        list.layout(new Rect(0, 0, bounds().width(), bounds().height()));
    }

    /**
     * Loads the expansion arrow when the tree joins a managed tree.
     */
    @Override
    protected void onAttached() {
        ensureIcons();
    }

    /**
     * Cancels running expansion animations, snaps branches to their target state and releases the
     * expansion arrow and node icons.
     */
    @Override
    protected void onDetached() {
        for (Node node : allNodes()) {
            boolean active = node.animation.isActive();
            node.animation.cancel();
            if (active) {
                node.expandedAmount = node.expanded ? 1 : 0;
            }
        }
        onStructureChanged();
        releaseNodeIcons();
        icons.release(context());
    }

    /**
     * Drops the cached expansion arrow and node icons so they reload at the new pixel density.
     */
    @Override
    protected void onContextChanged() {
        releaseNodeIcons();
        icons.release(context());
    }

    /**
     * Makes sure the expansion arrow is loaded before the rows are drawn.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        ensureRows();
        ensureIcons();
    }

    private void setNodeDisabled(Node node, boolean disabled) {
        if (node.disabled == disabled) {
            return;
        }
        node.disabled = disabled;
        onStructureChanged();
    }

    private void setNodeExpanded(Node node, boolean expanded, boolean animate) {
        if (!node.hasChildren()) {
            return;
        }
        ensureRows();
        float target = expanded ? 1 : 0;
        if (node.expanded == expanded && Math.abs(node.expandedAmount - target) < ANIMATION_SNAP) {
            return;
        }
        node.expanded = expanded;
        if (!expanded && selectedNode != null && isDescendant(selectedNode, node)) {
            int index = indexOf(node);
            if (index >= 0) {
                selectVisible(index);
            }
        }
        float from = node.expandedAmount;
        if (!animate || expansionDuration <= 0 || context() == null
                || Math.abs(target - from) < ANIMATION_SNAP) {
            node.animation.cancel();
            node.expandedAmount = target;
            onStructureChanged(node);
            return;
        }
        onStructureChanged(node);
        node.animation.start(context().animator(), from, target, expansionDuration,
                expansionEasing,
                value -> {
                    node.expandedAmount = Math.clamp(value, 0f, 1f);
                    list.invalidateRowHeights();
                },
                () -> {
                    node.expandedAmount = target;
                    onStructureChanged(node);
                });
    }

    // Flattening is deferred so bulk insertion stays linear; the row list rebuilds once on demand.
    private void invalidateRows() {
        rowsDirty = true;
    }

    private void ensureRows() {
        if (rowsDirty) {
            onStructureChanged();
        }
    }

    private void onStructureChanged() {
        onStructureChanged(null);
    }

    private void onStructureChanged(Node fallback) {
        rowsDirty = false;
        Node before = selectedNode;
        Node previous = selectedNode;
        rows.clear();
        appendRows(roots, 0);
        syncingSelection = true;
        try {
            list.setItemCount(rows.size());
            list.invalidateRowHeights();
            int index = previous == null ? -1 : indexOf(previous);
            if (index < 0 && previous != null && fallback != null) {
                previous = fallback;
                index = indexOf(fallback);
            }
            if (index >= 0 && !previous.isDisabled()) {
                selectedNode = previous;
                list.setSelectedIndices(List.of(index));
            } else {
                selectedNode = null;
                list.setSelectedIndices(List.of());
            }
        } finally {
            syncingSelection = false;
        }
        if (selectedNode != before && selectionListener != null) {
            selectionListener.accept(selectedNode);
        }
    }

    private void appendRows(List<Node> nodes, int depth) {
        for (Node node : nodes) {
            rows.add(new Row(node, depth));
            if (node.expanded || node.expandedAmount > ANIMATION_SNAP) {
                appendRows(node.children, depth + 1);
            }
        }
    }

    private void requireOwned(Node node) {
        if (node.owner != this) {
            throw new IllegalArgumentException("Node does not belong to this tree.");
        }
    }

    private List<Node> allNodes() {
        List<Node> result = new ArrayList<>();
        collectNodes(roots, result);
        return result;
    }

    private static void collectNodes(List<Node> nodes, List<Node> result) {
        for (Node node : nodes) {
            result.add(node);
            collectNodes(node.children, result);
        }
    }

    private static boolean isDescendant(Node node, Node ancestor) {
        for (Node parent = node.parent; parent != null; parent = parent.parent) {
            if (parent == ancestor) {
                return true;
            }
        }
        return false;
    }

    private void onListSelection(List<Integer> indices) {
        if (syncingSelection) {
            return;
        }
        Node next = indices.isEmpty() ? null : rows.get(indices.get(0)).node;
        if (next == selectedNode) {
            return;
        }
        selectedNode = next;
        if (selectionListener != null) {
            selectionListener.accept(next);
        }
    }

    private float animatedRowHeight(int index) {
        Node node = rows.get(index).node;
        double factor = 1;
        for (Node parent = node.parent; parent != null; parent = parent.parent) {
            factor *= parent.expandedAmount;
        }
        return Math.max(MIN_ANIMATED_ROW_HEIGHT, (float) (rowHeight * factor));
    }

    private void renderRow(int index, Rect bounds, VirtualList.RowState state,
                           RenderManager renderer) {
        Row row = rows.get(index);
        Theme theme = context().theme();
        boolean disabled = row.node.isDisabled();
        Color contentColor = disabled ? theme.textMuted().withAlpha(DISABLED_ALPHA) : theme.text();
        float left = ROW_PADDING + row.depth * indent;
        float centerY = bounds.y() + bounds.height() / 2;
        Color arrowColor = disabled ? contentColor
                : state.hovered() || state.active() ? theme.primary() : theme.textMuted();
        if (row.node.hasChildren()) {
            drawExpansionIcon(renderer,
                    new Rect(left, centerY - ARROW_SIZE / 2, ARROW_SIZE, ARROW_SIZE),
                    row.node.expandedAmount, arrowColor);
        }
        Font font = font();
        float textX = left + ARROW_SIZE + ARROW_GAP;
        if (row.node.icon() != null) {
            Texture icon = nodeIcon(row.node);
            if (icon != null) {
                renderer.icon(icon, new Rect(textX, centerY - NODE_ICON_SIZE / 2,
                        NODE_ICON_SIZE, NODE_ICON_SIZE), ImageStyle.ICON, contentColor);
            }
            textX += NODE_ICON_SIZE + NODE_ICON_GAP;
        }
        float textWidth = Math.max(0, bounds.right() - textX - ROW_PADDING);
        if (font != null && textWidth > 0) {
            renderer.text(font, row.node.label(), textX, centerY, contentColor,
                    new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
    }

    private Texture nodeIcon(Node node) {
        ensureNodeIcon(node);
        if (node.icon() instanceof ImageSource.TextureSource textureSource) {
            return textureSource.texture();
        }
        return node.iconSet == null ? null : node.iconSet.get(0);
    }

    private void ensureNodeIcon(Node node) {
        UiContext context = context();
        if (context == null || node.iconSet == null) {
            return;
        }
        int size = Math.max(1, Math.round(NODE_ICON_SIZE * context.density()));
        if (!node.iconSet.matches(context, size)) {
            node.iconSet.reload(context, size);
        }
    }

    private void releaseNodeIcon(Node node) {
        if (node.iconSet != null) {
            node.iconSet.release(context());
        }
    }

    private void releaseNodeIcons() {
        for (Node node : allNodes()) {
            releaseNodeIcon(node);
        }
    }

    private void drawExpansionIcon(RenderManager renderer, Rect slot, float progress,
                                   Color color) {
        Texture arrow = icons.get(0);
        if (arrow == null) {
            return;
        }
        float amount = Math.clamp(progress, 0f, 1f);
        float angle = (float) (Math.PI / 2 * amount);
        float centerX = slot.x() + slot.width() / 2;
        float centerY = slot.y() + slot.height() / 2;
        Transform transform = Transform.translation(centerX, centerY)
                .multiply(Transform.rotation(angle));
        Rect local = new Rect(-slot.width() / 2, -slot.height() / 2,
                slot.width(), slot.height());
        renderer.withTransform(transform,
                () -> renderer.icon(arrow, local, ImageStyle.FILL, color));
    }

    private void ensureIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(ARROW_SIZE * context.density()));
        if (icons.matches(context, size)) {
            return;
        }
        icons.reload(context, size);
    }

    private boolean handleTreeKey(Key key, Modifiers modifiers) {
        Node node = activeNode();
        return switch (key) {
            case UP -> moveSelection(-1);
            case DOWN -> moveSelection(1);
            case HOME -> moveToEdge(true);
            case END -> moveToEdge(false);
            case PAGE_UP -> movePage(-1);
            case PAGE_DOWN -> movePage(1);
            case RIGHT -> handleRight(node);
            case LEFT -> handleLeft(node);
            case ENTER, SPACE -> {
                if (node != null && node.hasChildren()) {
                    toggle(node);
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private boolean handleRight(Node node) {
        if (node == null) {
            return false;
        }
        if (node.hasChildren() && node.expansionProgress() < 1 - ANIMATION_SNAP) {
            expand(node);
            return true;
        }
        if (!node.hasChildren()) {
            return false;
        }
        int index = indexOf(node);
        if (index >= 0 && index + 1 < rows.size()
                && rows.get(index + 1).depth > rows.get(index).depth) {
            selectVisible(index + 1);
        }
        return true;
    }

    private boolean handleLeft(Node node) {
        if (node == null) {
            return false;
        }
        if (node.hasChildren() && node.expansionProgress() > ANIMATION_SNAP) {
            collapse(node);
            return true;
        }
        int index = indexOf(node);
        if (index > 0) {
            int depth = rows.get(index).depth;
            for (int parent = index - 1; parent >= 0; parent--) {
                if (rows.get(parent).depth < depth) {
                    selectVisible(parent);
                    return true;
                }
            }
        }
        return false;
    }

    private Node activeNode() {
        int index = activeRowIndex();
        return index >= 0 && index < rows.size() ? rows.get(index).node : null;
    }

    private int activeRowIndex() {
        ensureRows();
        int index = list.activeIndex();
        if (index < 0 || index >= rows.size()) {
            index = selectedNode == null ? -1 : indexOf(selectedNode);
        }
        if (index >= 0 && !isRowSelectable(index)) {
            int fallback = findSelectable(index - 1, -1);
            if (fallback < 0) {
                fallback = findSelectable(index + 1, 1);
            }
            index = fallback;
        }
        return index;
    }

    private boolean moveSelection(int delta) {
        ensureRows();
        if (rows.isEmpty()) {
            return false;
        }
        int current = activeRowIndex();
        if (current < 0) {
            int edge = findSelectable(delta > 0 ? 0 : rows.size() - 1, delta);
            if (edge >= 0) {
                selectVisible(edge);
            }
            return true;
        }
        int next = findSelectable(current + delta, delta);
        if (next >= 0) {
            selectVisible(next);
        }
        return true;
    }

    private boolean moveToEdge(boolean first) {
        ensureRows();
        int index = findSelectable(first ? 0 : rows.size() - 1, first ? 1 : -1);
        if (index >= 0) {
            selectVisible(index);
        }
        return index >= 0;
    }

    private boolean movePage(int direction) {
        ensureRows();
        if (rows.isEmpty()) {
            return false;
        }
        int current = activeRowIndex();
        if (current < 0) {
            return moveSelection(direction);
        }
        int pageRows = Math.max(1, (int) (list.bounds().height() / Math.max(1, rowHeight)));
        int index = current;
        for (int step = 0; step < pageRows; step++) {
            int next = findSelectable(index + direction, direction);
            if (next < 0) {
                break;
            }
            index = next;
        }
        if (index != current) {
            selectVisible(index);
        }
        return true;
    }

    private int findSelectable(int from, int delta) {
        for (int index = from; index >= 0 && index < rows.size(); index += delta) {
            if (isRowSelectable(index)) {
                return index;
            }
        }
        return -1;
    }

    private boolean isRowSelectable(int index) {
        Node node = rows.get(index).node;
        if (node.isDisabled()) {
            return false;
        }
        for (Node parent = node.parent; parent != null; parent = parent.parent) {
            if (!parent.expanded) {
                return false;
            }
        }
        return true;
    }

    private void selectVisible(int index) {
        list.setSelectedIndices(List.of(index));
        list.scrollToRow(index);
    }

    private boolean arrowContains(Row row, float x) {
        float left = ROW_PADDING + row.depth * indent;
        return x >= left - 2 && x <= left + ARROW_SIZE + 2;
    }

    private final class TreeList extends VirtualList {
        private TreeList() {
            setSelectionMode(SelectionMode.SINGLE);
            setRowHeightProvider(TreeView.this::animatedRowHeight);
            setRowRenderer(TreeView.this::renderRow);
            setSelectionListener(TreeView.this::onListSelection);
        }

        /**
         * Toggles a branch from its expansion arrow or starts row selection.
         *
         * @param x horizontal pointer position relative to the list
         * @param y vertical pointer position relative to the list
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press was consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button == MouseButton.LEFT) {
                int index = rowAt(y);
                if (index >= 0) {
                    Row row = rows.get(index);
                    if (row.node.isDisabled()) {
                        return true;
                    }
                    if (row.node.hasChildren() && arrowContains(row, x)) {
                        toggle(row.node);
                        return true;
                    }
                    if (!isRowSelectable(index)) {
                        return true;
                    }
                }
            }
            return super.onPointerPress(x, y, button, modifiers);
        }

        /**
         * Adds tree-specific expansion keys before regular list navigation.
         *
         * @param key pressed key
         * @param modifiers active modifiers
         * @param repeat whether this is a key repeat
         * @return true when the key was consumed
         */
        @Override
        protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
            if (handleTreeKey(key, modifiers)) {
                return true;
            }
            return super.onKeyPressed(key, modifiers, repeat);
        }
    }

    private record Row(Node node, int depth) {
    }
}
