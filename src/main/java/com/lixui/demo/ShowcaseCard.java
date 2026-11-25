package com.lixui.demo;

import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.VBox;

/**
 * A rounded surface with a padded vertical content area.
 * The surface uses the context panel style, so it follows theme changes without extra bindings.
 */
final class ShowcaseCard extends Panel {
    private final VBox body = new VBox();

    ShowcaseCard() {
        body.setPadding(16);
        body.setGap(10);
        add(body);
    }

    VBox body() {
        return body;
    }

    ShowcaseCard with(Widget child) {
        body.add(child);
        return this;
    }

    ShowcaseCard padding(float padding) {
        body.setPadding(padding);
        return this;
    }

    ShowcaseCard gap(float gap) {
        body.setGap(gap);
        return this;
    }

    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float width = Math.min(constraints.maxWidth(), maximumWidth());
        return body.measure(LayoutConstraints.loose(width, constraints.maxHeight()));
    }

    @Override
    protected void onLayout() {
        body.layout(localBounds());
    }
}
