package com.lixui.widget.testsupport;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

/**
 * A widget with a constant preferred size for layout tests.
 */
public final class FixedWidget extends Widget {
    private final Size preferred;

    /**
     * Creates a widget with the supplied natural size.
     *
     * @param width preferred width
     * @param height preferred height
     */
    public FixedWidget(float width, float height) {
        preferred = new Size(width, height);
        setBounds(new Rect(0, 0, width, height));
    }

    /**
     * Returns the fixed natural size.
     *
     * @param constraints available and required dimensions
     * @return the fixed preferred size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return preferred;
    }
}
