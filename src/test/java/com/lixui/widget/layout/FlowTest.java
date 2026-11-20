package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.testsupport.FixedWidget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowTest {
    @Test
    void wrapsChildrenWithoutStretchingThem() {
        Flow flow = new Flow();
        flow.setPadding(5);
        flow.setGap(4);
        flow.add(new FixedWidget(50, 20)).add(new FixedWidget(60, 30))
                .add(new FixedWidget(40, 10));

        flow.layout(new Rect(0, 0, 130, 100));

        List<Widget> children = flow.children();
        assertEquals(new Rect(5, 5, 50, 20), children.get(0).bounds());
        assertEquals(new Rect(59, 5, 60, 30), children.get(1).bounds());
        assertEquals(new Rect(5, 39, 40, 10), children.get(2).bounds());
    }

    @Test
    void measuresTheWidestLineAndTheTotalLineHeight() {
        Flow flow = new Flow();
        flow.setPadding(5);
        flow.setGap(4);
        flow.add(new FixedWidget(50, 20)).add(new FixedWidget(60, 30))
                .add(new FixedWidget(40, 10));

        Size measured = flow.measure(LayoutConstraints.loose(150, 500));

        assertEquals(124, measured.width(), 0.001f);
        assertEquals(54, measured.height(), 0.001f);
    }

    @Test
    void invisibleChildrenTakeNoSpaceAndKeepTheirBounds() {
        Flow flow = new Flow();
        FixedWidget hidden = new FixedWidget(100, 100);
        hidden.setBounds(new Rect(7, 8, 100, 100));
        hidden.setVisible(false);
        flow.add(hidden).add(new FixedWidget(20, 10)).add(new FixedWidget(10, 10));

        Size measured = flow.measure(LayoutConstraints.loose(500, 500));
        assertEquals(30, measured.width(), 0.001f);
        assertEquals(10, measured.height(), 0.001f);

        flow.layout(new Rect(0, 0, 100, 50));

        assertEquals(new Rect(7, 8, 100, 100), hidden.bounds());
        assertEquals(new Rect(0, 0, 20, 10), flow.children().get(1).bounds());
        assertEquals(new Rect(20, 0, 10, 10), flow.children().get(2).bounds());
    }

    @Test
    void rejectsInvalidGap() {
        Flow flow = new Flow();
        assertThrows(IllegalArgumentException.class, () -> flow.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> flow.setGap(Float.NaN));
    }
}
