package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.testsupport.FixedWidget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackTest {
    @Test
    void manualChildrenKeepTheirBoundsAndOverlap() {
        Stack stack = new Stack();
        stack.setPadding(7);
        FixedWidget back = new FixedWidget(100, 80);
        back.setBounds(new Rect(4, 6, 100, 80));
        FixedWidget front = new FixedWidget(40, 30);
        front.setBounds(new Rect(30, 20, 40, 30));
        stack.add(back).add(front);

        stack.layout(new Rect(10, 10, 200, 200));

        assertEquals(new Rect(4, 6, 100, 80), back.bounds());
        assertEquals(new Rect(30, 20, 40, 30), front.bounds());
        assertEquals(List.of(back, front), stack.children());
    }

    @Test
    void fillStretchesEveryVisibleChildOverThePaddedContentArea() {
        Stack stack = new Stack();
        assertFalse(stack.isFill());
        stack.setFill(true);
        assertTrue(stack.isFill());
        stack.setPadding(5);
        FixedWidget first = new FixedWidget(10, 10);
        FixedWidget second = new FixedWidget(20, 20);
        stack.add(first).add(second);

        stack.layout(new Rect(100, 50, 300, 200));

        assertEquals(new Rect(5, 5, 290, 190), first.bounds());
        assertEquals(new Rect(5, 5, 290, 190), second.bounds());
    }

    @Test
    void measuresTheLargestChildAndAddsPaddingOnlyInFillMode() {
        Stack stack = new Stack();
        stack.setPadding(7);
        FixedWidget offset = new FixedWidget(20, 10);
        offset.setBounds(new Rect(50, 40, 20, 10));
        stack.add(new FixedWidget(40, 30)).add(new FixedWidget(60, 20)).add(offset);

        Size manual = stack.measure(LayoutConstraints.loose(500, 500));
        assertEquals(70, manual.width(), 0.001f);
        assertEquals(50, manual.height(), 0.001f);

        stack.setFill(true);
        Size filled = stack.measure(LayoutConstraints.loose(500, 500));
        assertEquals(74, filled.width(), 0.001f);
        assertEquals(44, filled.height(), 0.001f);
    }

    @Test
    void invisibleChildrenKeepTheirBounds() {
        Stack stack = new Stack();
        stack.setFill(true);
        stack.setPadding(4);
        FixedWidget hidden = new FixedWidget(30, 40);
        hidden.setBounds(new Rect(1, 2, 30, 40));
        hidden.setVisible(false);
        FixedWidget visible = new FixedWidget(10, 10);
        stack.add(hidden).add(visible);

        stack.layout(new Rect(0, 0, 100, 60));

        assertEquals(new Rect(1, 2, 30, 40), hidden.bounds());
        assertEquals(new Rect(4, 4, 92, 52), visible.bounds());
    }
}
