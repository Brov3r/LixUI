package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;

final class FixedWidget extends Widget {
    private final Size preferred;

    FixedWidget(float width, float height) {
        preferred = new Size(width, height);
        setBounds(new Rect(0, 0, width, height));
    }

    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return preferred;
    }
}
