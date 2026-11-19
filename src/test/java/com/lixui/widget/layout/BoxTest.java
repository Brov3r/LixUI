package com.lixui.widget.layout;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void vboxStacksChildrenWithGapPaddingAndStretch() {
        VBox box = new VBox();
        box.setPadding(10);
        box.setGap(5);
        box.add(new FixedWidget(100, 20)).add(new FixedWidget(50, 30)).add(new FixedWidget(80, 10));

        Size measured = box.measure(LayoutConstraints.loose(500, 500));
        assertEquals(120, measured.width(), 0.001f);
        assertEquals(90, measured.height(), 0.001f);

        box.layout(new Rect(0, 0, 200, 300));

        List<Widget> children = box.children();
        assertEquals(new Rect(10, 10, 180, 20), children.get(0).bounds());
        assertEquals(new Rect(10, 35, 180, 30), children.get(1).bounds());
        assertEquals(new Rect(10, 70, 180, 10), children.get(2).bounds());
    }

    @Test
    void hboxAlignsChildrenAcrossTheCrossAxis() {
        HBox box = new HBox();
        box.setPadding(4);
        box.setGap(6);
        box.setCrossAlignment(Box.CrossAlignment.CENTER);
        box.add(new FixedWidget(40, 20)).add(new FixedWidget(30, 10));

        box.layout(new Rect(0, 0, 200, 100));

        List<Widget> children = box.children();
        assertEquals(new Rect(4, 40, 40, 20), children.get(0).bounds());
        assertEquals(new Rect(50, 45, 30, 10), children.get(1).bounds());
    }

    @Test
    void invisibleChildrenTakeNoSpaceAndKeepTheirBounds() {
        VBox box = new VBox();
        FixedWidget hidden = new FixedWidget(100, 40);
        hidden.setVisible(false);
        FixedWidget visible = new FixedWidget(50, 10);
        box.add(hidden).add(visible);

        Size measured = box.measure(LayoutConstraints.loose(500, 500));
        assertEquals(50, measured.width(), 0.001f);
        assertEquals(10, measured.height(), 0.001f);

        box.layout(new Rect(0, 0, 200, 100));
        assertEquals(new Rect(0, 0, 100, 40), hidden.bounds());
        assertEquals(new Rect(0, 0, 200, 10), visible.bounds());
    }

    @Test
    void rejectsInvalidSpacingAndAlignment() {
        VBox box = new VBox();
        assertThrows(IllegalArgumentException.class, () -> box.setGap(-1));
        assertThrows(IllegalArgumentException.class, () -> box.setGap(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> box.setPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> box.setPadding(Float.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> box.setCrossAlignment(null));
    }

    @Test
    void autoSizedBoxWrapsItsContentAtTheAssignedPosition() {
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            manager.resize(VIEWPORT);
            VBox box = new VBox();
            box.setPadding(6);
            box.add(new FixedWidget(80, 20)).add(new FixedWidget(40, 30));
            box.setPosition(15, 25);
            box.setAutoSize(true);
            manager.add(box);

            manager.layout();

            assertEquals(new Rect(15, 25, 92, 62), box.bounds());
        }
    }

    @Test
    void fillViewportLaysTheRootOverTheWholeViewport() {
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            manager.resize(VIEWPORT);
            VBox box = new VBox();
            box.setFillViewport(true);
            manager.add(box);

            manager.layout();

            assertEquals(new Rect(0, 0, 800, 600), box.bounds());
        }
    }

    @Test
    void distributesLeftoverMainAxisSpaceBetweenGrowingChildren() {
        HBox box = new HBox();
        box.setGap(10);
        FixedWidget first = new FixedWidget(20, 10);
        FixedWidget second = new FixedWidget(20, 10);
        FixedWidget third = new FixedWidget(20, 10);
        first.setGrow(1);
        second.setGrow(3);
        box.add(first).add(second).add(third);

        box.layout(new Rect(0, 0, 240, 60));

        assertEquals(new Rect(0, 0, 60, 60), first.bounds());
        assertEquals(new Rect(70, 0, 140, 60), second.bounds());
        assertEquals(new Rect(220, 0, 20, 60), third.bounds());
    }

    @Test
    void emptyLayoutSpaceDoesNotBlockPointerInput() {
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            manager.resize(VIEWPORT);
            HBox bar = new HBox();
            bar.setFillViewport(true);
            bar.setCrossAlignment(Box.CrossAlignment.START);
            bar.add(new FixedWidget(100, 40));
            manager.add(bar);
            manager.layout();

            assertTrue(manager.blocksPointer(50, 20));
            assertFalse(manager.blocksPointer(400, 20));
            assertNull(bar.hitTest(400, 20));
        }
    }
}
