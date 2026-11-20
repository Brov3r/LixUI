package com.lixui.widget.layout;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void alignsTheChildToTheNinePositions() {
        Anchor anchor = new Anchor();
        anchor.setPadding(10);
        FixedWidget child = new FixedWidget(40, 20);
        anchor.setChild(child);
        Rect bounds = new Rect(0, 0, 200, 100);

        anchor.setAlignment(Anchor.Alignment.TOP_LEFT);
        anchor.layout(bounds);
        assertEquals(new Rect(10, 10, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.TOP_CENTER);
        anchor.layout(bounds);
        assertEquals(new Rect(80, 10, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.TOP_RIGHT);
        anchor.layout(bounds);
        assertEquals(new Rect(150, 10, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.CENTER_LEFT);
        anchor.layout(bounds);
        assertEquals(new Rect(10, 40, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.CENTER);
        anchor.layout(bounds);
        assertEquals(new Rect(80, 40, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.CENTER_RIGHT);
        anchor.layout(bounds);
        assertEquals(new Rect(150, 40, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.BOTTOM_LEFT);
        anchor.layout(bounds);
        assertEquals(new Rect(10, 70, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.BOTTOM_CENTER);
        anchor.layout(bounds);
        assertEquals(new Rect(80, 70, 40, 20), child.bounds());

        anchor.setAlignment(Anchor.Alignment.BOTTOM_RIGHT);
        anchor.layout(bounds);
        assertEquals(new Rect(150, 70, 40, 20), child.bounds());
    }

    @Test
    void fillsTheChildAcrossTheEnabledAxes() {
        Anchor anchor = new Anchor(Anchor.Alignment.BOTTOM_RIGHT);
        anchor.setPadding(10);
        FixedWidget child = new FixedWidget(40, 20);
        anchor.setChild(child);

        anchor.setFillX(true);
        anchor.layout(new Rect(0, 0, 200, 100));
        assertEquals(new Rect(10, 70, 180, 20), child.bounds());

        anchor.setFillY(true);
        anchor.layout(new Rect(0, 0, 200, 100));
        assertEquals(new Rect(10, 10, 180, 80), child.bounds());
    }

    @Test
    void autoSizedAnchorWrapsTheChildAndPadding() {
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            manager.resize(VIEWPORT);
            Anchor anchor = new Anchor(Anchor.Alignment.CENTER);
            anchor.setPadding(6);
            FixedWidget child = new FixedWidget(40, 20);
            anchor.setChild(child);
            anchor.setPosition(15, 25);
            anchor.setAutoSize(true);
            manager.add(anchor);

            manager.layout();

            assertEquals(new Rect(15, 25, 52, 32), anchor.bounds());
            assertEquals(new Rect(6, 6, 40, 20), child.bounds());
        }
    }

    @Test
    void measuresTheChildPlusPadding() {
        Anchor anchor = new Anchor();
        anchor.setPadding(4);
        anchor.setChild(new FixedWidget(60, 30));

        Size measured = anchor.measure(LayoutConstraints.loose(500, 500));

        assertEquals(68, measured.width(), 0.001f);
        assertEquals(38, measured.height(), 0.001f);
    }

    @Test
    void invisibleChildTakesNoSpaceAndKeepsItsBounds() {
        Anchor anchor = new Anchor();
        anchor.setPadding(5);
        FixedWidget hidden = new FixedWidget(100, 60);
        hidden.setVisible(false);
        anchor.setChild(hidden);

        Size measured = anchor.measure(LayoutConstraints.loose(500, 500));
        anchor.layout(new Rect(0, 0, 200, 100));

        assertEquals(10, measured.width(), 0.001f);
        assertEquals(10, measured.height(), 0.001f);
        assertEquals(new Rect(0, 0, 100, 60), hidden.bounds());
    }

    @Test
    void replacesAndClearsTheChild() {
        Anchor anchor = new Anchor();
        FixedWidget first = new FixedWidget(20, 20);
        FixedWidget second = new FixedWidget(30, 30);
        anchor.setChild(first);

        assertSame(first, anchor.child());
        assertSame(anchor, first.parent());

        anchor.setChild(second);

        assertSame(second, anchor.child());
        assertSame(anchor, second.parent());
        assertNull(first.parent());

        anchor.clearChild();

        assertNull(anchor.child());
        assertNull(second.parent());
    }

    @Test
    void emptySpaceAroundTheChildDoesNotBlockPointerInput() {
        try (WidgetManager manager = new WidgetManager(new InputState(), new TestBackend())) {
            manager.resize(VIEWPORT);
            Anchor anchor = new Anchor(Anchor.Alignment.TOP_LEFT);
            anchor.setFillViewport(true);
            FixedWidget child = new FixedWidget(100, 40);
            anchor.setChild(child);
            manager.add(anchor);
            manager.layout();

            assertTrue(manager.blocksPointer(50, 20));
            assertFalse(manager.blocksPointer(400, 20));
            assertNull(anchor.hitTest(400, 20));
        }
    }

    @Test
    void rejectsInvalidArguments() {
        Anchor anchor = new Anchor();
        assertThrows(NullPointerException.class, () -> new Anchor(null));
        assertThrows(NullPointerException.class, () -> anchor.setAlignment(null));
        assertThrows(NullPointerException.class, () -> anchor.setChild(null));
        assertThrows(IllegalArgumentException.class, () -> anchor.setPadding(-1));
    }
}
