package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;

/**
 * A clipped viewport that keeps a benchmark subtree at a fixed visible height.
 * The content is laid out at its own natural height, so large stress scenes stay fully built while
 * the demo page only reserves a small window for them.
 */
final class BenchmarkStage extends Panel {
    private final float preferredHeight;

    /**
     * Creates an empty stage with a fixed visible height.
     *
     * @param preferredHeight non-negative visible height in logical UI units
     * @throws IllegalArgumentException if the height is negative or non-finite
     */
    BenchmarkStage(float preferredHeight) {
        if (!Float.isFinite(preferredHeight) || preferredHeight < 0) {
            throw new IllegalArgumentException("Stage height must be finite and non-negative.");
        }
        this.preferredHeight = preferredHeight;
        setClipToBounds(true);
    }

    /**
     * Reports the fixed stage height at the available width.
     *
     * @param constraints available and required dimensions
     * @return the stage size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(constraints.maxWidth(), preferredHeight);
    }

    /**
     * Lays every child out at the stage width and at its retained stress height.
     */
    @Override
    protected void onLayout() {
        for (Widget child : children()) {
            if (child.parent() == this) {
                child.layout(new Rect(0, 0, bounds().width(),
                        Math.max(bounds().height(), child.bounds().height())));
            }
        }
    }

    /**
     * Removes every stress widget from the stage.
     */
    void clear() {
        removeAll();
    }

    /**
     * Reports whether the stage currently holds a stress subtree.
     *
     * @return true when at least one child is attached
     */
    boolean hasContent() {
        return !children().isEmpty();
    }
}
