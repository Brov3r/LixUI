package com.lixui.widget.control;

import com.lixui.widget.LayoutConstraints;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.FixedWidget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpacerTest {
    @Test
    void spacerTakesTheRemainingSpaceInABox() {
        HBox row = new HBox();
        row.setGap(10);
        Spacer spacer = new Spacer();
        row.add(new FixedWidget(40, 20)).add(spacer).add(new FixedWidget(30, 20));

        row.layout(new Rect(0, 0, 200, 50));

        assertEquals(1, spacer.grow());
        assertEquals(new Rect(50, 0, 110, 50), spacer.bounds());
        assertEquals(new Rect(170, 0, 30, 50), row.children().get(2).bounds());

        VBox column = new VBox();
        column.setGap(5);
        Spacer vertical = new Spacer();
        column.add(new FixedWidget(20, 10)).add(vertical).add(new FixedWidget(20, 10));

        column.layout(new Rect(0, 0, 50, 100));

        assertEquals(new Rect(0, 15, 50, 70), vertical.bounds());
    }

    @Test
    void spacerHasNoNaturalSize() {
        HBox row = new HBox();
        row.add(new FixedWidget(40, 20)).add(new Spacer()).add(new FixedWidget(30, 20));

        Size measured = row.measure(LayoutConstraints.loose(500, 500));

        assertEquals(70, measured.width(), 0.001f);
        assertEquals(20, measured.height(), 0.001f);
    }

    @Test
    void spacerIsPointerTransparent() {
        Spacer spacer = new Spacer();
        spacer.setBounds(new Rect(0, 0, 100, 50));

        assertNull(spacer.hitTest(50, 25));
    }

    @Test
    void acceptsACustomGrowFactorAndRejectsInvalidValues() {
        Spacer spacer = new Spacer(2);
        assertEquals(2, spacer.grow(), 0.001f);
        spacer.setGrow(0);
        assertEquals(0, spacer.grow(), 0.001f);
        assertThrows(IllegalArgumentException.class, () -> spacer.setGrow(-1));
        assertThrows(IllegalArgumentException.class, () -> spacer.setGrow(Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> spacer.setGrow(Float.POSITIVE_INFINITY));
    }
}
