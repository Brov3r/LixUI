package com.lixui.widget.layout;

/**
 * A box that places children from left to right and stretches them across the height by default.
 */
public final class HBox extends Box {
    /**
     * Creates an empty horizontal box.
     */
    public HBox() {
    }

    /**
     * Reports that children are placed horizontally.
     *
     * @return false
     */
    @Override
    protected boolean vertical() {
        return false;
    }
}
