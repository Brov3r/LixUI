package com.lixui.demo;

import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;

/**
 * An empty flexible widget that always measures zero so repeated layout passes stay stable.
 */
final class ShowcaseSpacer extends Widget {
    ShowcaseSpacer() {
        setGrow(1);
    }

    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(0, 0);
    }

    @Override
    public Widget hitTest(float x, float y) {
        Widget hit = super.hitTest(x, y);
        return hit == this ? null : hit;
    }
}
