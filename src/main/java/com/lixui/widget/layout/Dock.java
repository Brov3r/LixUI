package com.lixui.widget.layout;

/**
 * A dock side used by {@link DockPanel} children.
 * Docked children consume a strip of the remaining area in insertion order; fill children receive
 * whatever is left.
 */
public enum Dock {
    /** Consumes a strip at the top of the remaining area at the child's preferred height. */
    TOP,
    /** Consumes a strip at the bottom of the remaining area at the child's preferred height. */
    BOTTOM,
    /** Consumes a strip at the left of the remaining area at the child's preferred width. */
    LEFT,
    /** Consumes a strip at the right of the remaining area at the child's preferred width. */
    RIGHT,
    /** Receives the area left after every docked child; later fill children are drawn on top. */
    FILL,
    /** Keeps the child's current bounds and takes no space from the layout. */
    NONE
}
