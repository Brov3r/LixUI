package com.lixui.widget.layout;

/**
 * A box that stacks children from top to bottom and stretches them across the width by default.
 */
public final class VBox extends Box {
    /**
     * Creates an empty vertical box.
     */
    public VBox() {
    }

    /**
     * Reports that children are stacked vertically.
     *
     * @return true
     */
    @Override
    protected boolean vertical() {
        return true;
    }
}
