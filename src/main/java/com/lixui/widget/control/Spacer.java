package com.lixui.widget.control;

import com.lixui.widget.Widget;
/**
 * An empty widget that takes leftover space in a layout container.
 * A spacer has a grow factor of one by default, so boxes expand it along their main axis. It draws
 * nothing and is pointer-transparent. Set the grow factor to zero to keep it at its measured size.
 */
public class Spacer extends Widget {
    /**
     * Creates a flexible spacer with a grow factor of one.
     */
    public Spacer() {
        setGrow(1);
    }

    /**
     * Creates a flexible spacer with the supplied grow factor.
     *
     * @param grow non-negative flex factor
     * @throws IllegalArgumentException if the factor is negative or non-finite
     */
    public Spacer(float grow) {
        setGrow(grow);
    }

    /**
     * Finds a child under the point while keeping the empty spacer itself pointer-transparent.
     *
     * @param x horizontal coordinate relative to the widget's top-left corner
     * @param y vertical coordinate relative to the widget's top-left corner
     * @return the hit child, or null when the point only hits the spacer
     */
    @Override
    public Widget hitTest(float x, float y) {
        Widget hit = super.hitTest(x, y);
        return hit == this ? null : hit;
    }
}
