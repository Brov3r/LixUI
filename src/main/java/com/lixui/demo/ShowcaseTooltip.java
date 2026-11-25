package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Label;

/**
 * A pointer-transparent tooltip card placed by the navigation scene.
 * Because an open context menu occupies the viewport hit area, the showcase presents the tooltip
 * as a static top-level card instead of relying on the hover overlay.
 */
final class ShowcaseTooltip extends Panel {
    private static final float PADDING_X = 10;
    private static final float PADDING_Y = 6;

    private final Label label;

    ShowcaseTooltip(ShowcaseKit kit, String text) {
        label = kit.text(text);
        add(label);
        setAlwaysOnTop(true);
    }

    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Size text = label.measure(LayoutConstraints.loose(
                Math.max(0, constraints.maxWidth() - PADDING_X * 2),
                Math.max(0, constraints.maxHeight() - PADDING_Y * 2)));
        return new Size(text.width() + PADDING_X * 2, text.height() + PADDING_Y * 2);
    }

    @Override
    protected void onLayout() {
        label.layout(new Rect(PADDING_X, PADDING_Y,
                Math.max(0, bounds().width() - PADDING_X * 2),
                Math.max(0, bounds().height() - PADDING_Y * 2)));
    }

    @Override
    public Widget hitTest(float x, float y) {
        return null;
    }
}
