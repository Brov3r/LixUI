package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A container that assigns each child a {@link Dock} side and fills the remaining area.
 * Children are placed in insertion order, so earlier strips take space before later ones. Children
 * added with {@link #add(Widget)} or without an explicit dock default to {@link Dock#FILL}.
 */
public final class DockPanel extends LayoutContainer {
    private final Map<Widget, Dock> docks = new IdentityHashMap<>();

    /**
     * Creates an empty dock panel.
     */
    public DockPanel() {
    }

    /**
     * Adds a child with the supplied dock side.
     *
     * @param child widget to add
     * @param dock dock side for the child
     * @return this dock panel for chaining
     * @throws NullPointerException if the child or dock side is null
     * @throws IllegalArgumentException if the child already has a parent or manager, is this panel,
     *                                  or is an ancestor
     */
    public DockPanel add(Widget child, Dock dock) {
        Objects.requireNonNull(dock, "dock");
        add(child);
        docks.put(child, dock);
        return this;
    }

    /**
     * Returns the dock side used for a child.
     *
     * @param child child of this panel
     * @return the assigned dock side, or {@link Dock#FILL} when none was assigned
     * @throws NullPointerException if the child is null
     */
    public Dock dock(Widget child) {
        Objects.requireNonNull(child, "child");
        return child.parent() == this ? docks.getOrDefault(child, Dock.FILL) : Dock.FILL;
    }

    /**
     * Changes the dock side of an attached child.
     *
     * @param child child of this panel
     * @param dock new dock side
     * @throws NullPointerException if the child or dock side is null
     * @throws IllegalArgumentException if the widget is not a direct child of this panel
     */
    public void setDock(Widget child, Dock dock) {
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(dock, "dock");
        if (child.parent() != this) {
            throw new IllegalArgumentException("Widget is not a child of this dock panel.");
        }
        docks.put(child, dock);
    }

    /**
     * Measures the largest visible child that takes part in docking plus padding.
     * Children with {@link Dock#NONE} are ignored.
     *
     * @param constraints available and required dimensions
     * @return the natural dock panel size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float availableWidth = Math.max(0, constraints.maxWidth() - padding() * 2);
        float availableHeight = Math.max(0, constraints.maxHeight() - padding() * 2);
        float width = 0;
        float height = 0;
        for (Widget child : children()) {
            if (!child.isVisible() || dock(child) == Dock.NONE) {
                continue;
            }
            Size size = child.measure(LayoutConstraints.loose(availableWidth, availableHeight));
            width = Math.max(width, size.width());
            height = Math.max(height, size.height());
        }
        return new Size(width + padding() * 2, height + padding() * 2);
    }

    /**
     * Reserves strips for docked children in insertion order and gives the remainder to fill
     * children. Children with {@link Dock#NONE} keep their current bounds.
     */
    @Override
    protected void onLayout() {
        pruneDocks();
        float left = padding();
        float top = padding();
        float right = Math.max(left, bounds().width() - padding());
        float bottom = Math.max(top, bounds().height() - padding());
        List<Widget> fills = new ArrayList<>();
        for (Widget child : children()) {
            if (!child.isVisible()) {
                continue;
            }
            Dock dock = dock(child);
            if (dock == Dock.FILL) {
                fills.add(child);
                continue;
            }
            if (dock == Dock.NONE) {
                continue;
            }
            float availableWidth = Math.max(0, right - left);
            float availableHeight = Math.max(0, bottom - top);
            Size size = child.measure(LayoutConstraints.loose(availableWidth, availableHeight));
            switch (dock) {
                case TOP -> {
                    float height = Math.min(size.height(), availableHeight);
                    child.layout(new Rect(left, top, availableWidth, height));
                    top += height;
                }
                case BOTTOM -> {
                    float height = Math.min(size.height(), availableHeight);
                    child.layout(new Rect(left, bottom - height, availableWidth, height));
                    bottom -= height;
                }
                case LEFT -> {
                    float width = Math.min(size.width(), availableWidth);
                    child.layout(new Rect(left, top, width, availableHeight));
                    left += width;
                }
                case RIGHT -> {
                    float width = Math.min(size.width(), availableWidth);
                    child.layout(new Rect(right - width, top, width, availableHeight));
                    right -= width;
                }
                default -> {
                }
            }
        }
        Rect fillArea = new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        for (Widget child : fills) {
            child.layout(fillArea);
        }
    }

    private void pruneDocks() {
        docks.entrySet().removeIf(entry -> entry.getKey().parent() != this);
    }
}
