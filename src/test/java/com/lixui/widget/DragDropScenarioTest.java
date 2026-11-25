package com.lixui.widget;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerEntered;
import com.lixui.input.event.PointerExited;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.widget.container.ScrollPanel;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragDropScenarioTest {
    private static final String KIND = "card";
    private static final Rect SOURCE_BOUNDS = new Rect(10, 10, 100, 40);

    @Test
    void ghostFollowsThePointerInViewportCoordinates() {
        WidgetManager manager = manager();
        Panel parent = new Panel();
        parent.setBounds(new Rect(100, 100, 300, 200));
        DragSource nested = new DragSource(KIND, "nested");
        nested.setBounds(new Rect(20, 20, 80, 30));
        parent.add(nested);
        manager.add(parent);

        manager.handle(new PointerPressed(130, 130, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(150, 170));

        Widget ghost = ghost(manager);
        assertEquals(150 + 14, ghost.bounds().x(), 0.001f);
        assertEquals(170 + 14, ghost.bounds().y(), 0.001f);

        manager.handle(new PointerMoved(500, 400));

        assertEquals(500 + 14, ghost.bounds().x(), 0.001f);
        assertEquals(400 + 14, ghost.bounds().y(), 0.001f);

        manager.handle(new PointerMoved(795, 595));

        assertTrue(ghost.bounds().right() <= manager.viewport().right());
        assertTrue(ghost.bounds().bottom() <= manager.viewport().bottom());
        assertTrue(ghost.bounds().x() > 500);
        assertTrue(ghost.bounds().y() > 400);

        manager.handle(new PointerReleased(795, 595, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void scrolledContentUsesClippedHitTestingForDropZones() {
        WidgetManager manager = manager();
        ScrollPanel scroll = new ScrollPanel();
        scroll.setBounds(new Rect(0, 0, 200, 150));
        Panel content = new Panel();
        content.setSize(200, 400);
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(10, 300, 160, 50));
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((source, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        content.add(zone);
        scroll.setContent(content);
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(new Rect(300, 20, 80, 30));
        manager.add(scroll).add(source);
        manager.layout();

        startDrag(manager, 320, 30, 350, 40);
        manager.handle(new PointerMoved(50, 70));

        Point zoneOrigin = zone.absolutePosition();
        assertEquals(10, zoneOrigin.x(), 0.001f);
        assertEquals(300, zoneOrigin.y(), 0.001f);
        assertNull(manager.context().dragSession().target());

        scroll.setScroll(0, 250);
        manager.layout();

        Point scrolledOrigin = zone.absolutePosition();
        assertEquals(10, scrolledOrigin.x(), 0.001f);
        assertEquals(50, scrolledOrigin.y(), 0.001f);

        manager.handle(new PointerMoved(50, 70));

        assertSame(zone, manager.context().dragSession().target());
        manager.handle(new PointerReleased(50, 70, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of(KIND), drops);
    }

    @Test
    void dragFromScrolledContentReachesAZoneOutsideTheViewport() {
        WidgetManager manager = manager();
        ScrollPanel scroll = new ScrollPanel();
        scroll.setBounds(new Rect(0, 0, 220, 140));
        Panel content = new Panel();
        content.setSize(220, 500);
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(new Rect(10, 380, 100, 30));
        content.add(source);
        scroll.setContent(content);
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(300, 200, 160, 80));
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(scroll).add(zone);
        manager.layout();
        scroll.setScroll(0, 300);
        manager.layout();

        Point sourceOrigin = source.absolutePosition();
        assertEquals(10, sourceOrigin.x(), 0.001f);
        assertEquals(80, sourceOrigin.y(), 0.001f);

        manager.handle(new PointerPressed(40, 90, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(100, 100));

        assertTrue(manager.context().isDragging());
        Point ghostOrigin = ghost(manager).bounds().center();
        assertEquals(100 + 14 + ghost(manager).bounds().width() / 2, ghostOrigin.x(), 0.001f);
        assertEquals(100 + 14 + ghost(manager).bounds().height() / 2, ghostOrigin.y(), 0.001f);

        manager.handle(new PointerMoved(350, 230));
        manager.handle(new PointerReleased(350, 230, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of(KIND), drops);
        assertTrue(source.dragEnded);
        assertFalse(manager.context().isDragging());
    }

    @Test
    void disabledOrHiddenZonesNeverReceiveADrop() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone disabled = new DropZone(KIND);
        disabled.setBounds(new Rect(200, 10, 120, 60));
        List<String> drops = new ArrayList<>();
        disabled.setOnDrop((dragSource, kind, payload) -> {
            drops.add("disabled");
            return true;
        });
        disabled.setEnabled(false);
        DropZone hidden = new DropZone(KIND);
        hidden.setBounds(new Rect(340, 10, 120, 60));
        hidden.setOnDrop((dragSource, kind, payload) -> {
            drops.add("hidden");
            return true;
        });
        hidden.setVisible(false);
        manager.add(source).add(disabled).add(hidden);

        startDrag(manager, 20, 20, 250, 30);
        assertNull(manager.context().dragSession().target());
        manager.handle(new PointerReleased(250, 30, MouseButton.LEFT, Modifiers.NONE));

        startDrag(manager, 20, 20, 400, 30);
        assertNull(manager.context().dragSession().target());
        manager.handle(new PointerReleased(400, 30, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
    }

    @Test
    void innermostDropZoneReceivesTheDrop() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone outer = new DropZone(KIND);
        outer.setBounds(new Rect(150, 0, 320, 200));
        DropZone inner = new DropZone(KIND);
        inner.setBounds(new Rect(50, 50, 100, 80));
        outer.add(inner);
        List<String> drops = new ArrayList<>();
        outer.setOnDrop((dragSource, kind, payload) -> {
            drops.add("outer");
            return true;
        });
        inner.setOnDrop((dragSource, kind, payload) -> {
            drops.add("inner");
            return true;
        });
        manager.add(source).add(outer);

        startDrag(manager, 20, 20, 240, 100);
        assertSame(inner, manager.context().dragSession().target());
        assertTrue(inner.isDragTarget());
        assertFalse(outer.isDragTarget());

        manager.handle(new PointerReleased(240, 100, MouseButton.LEFT, Modifiers.NONE));
        assertEquals(List.of("inner"), drops);

        startDrag(manager, 20, 20, 440, 170);
        assertSame(outer, manager.context().dragSession().target());
        manager.handle(new PointerReleased(440, 170, MouseButton.LEFT, Modifiers.NONE));
        assertEquals(List.of("inner", "outer"), drops);
    }

    @Test
    void releasingOutsideTheWindowAfterLeavingAZoneDoesNotDrop() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(200, 10, 160, 100));
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 20, 20, 260, 50);
        assertSame(zone, manager.context().dragSession().target());

        manager.handle(new PointerExited(900, 700));
        manager.handle(new PointerReleased(900, 700, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertNull(manager.context().dragSession());
    }

    @Test
    void reenteringTheWindowThenReleasingUsesTheFinalPointerPosition() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone first = new DropZone(KIND);
        first.setBounds(new Rect(200, 10, 120, 80));
        DropZone second = new DropZone(KIND);
        second.setBounds(new Rect(350, 200, 120, 80));
        List<String> drops = new ArrayList<>();
        first.setOnDrop((dragSource, kind, payload) -> {
            drops.add("first");
            return true;
        });
        second.setOnDrop((dragSource, kind, payload) -> {
            drops.add("second");
            return true;
        });
        manager.add(source).add(first).add(second);

        startDrag(manager, 20, 20, 260, 50);
        assertSame(first, manager.context().dragSession().target());

        manager.handle(new PointerExited(900, 700));
        manager.handle(new PointerEntered(400, 240));
        manager.handle(new PointerReleased(400, 240, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of("second"), drops);
    }

    @Test
    void rejectingInnerZoneDoesNotLeakTheDropToAnAcceptingAncestor() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone outer = new DropZone(KIND);
        outer.setBounds(new Rect(150, 0, 320, 200));
        DropZone inner = new DropZone("other");
        inner.setBounds(new Rect(50, 50, 100, 80));
        outer.add(inner);
        List<String> drops = new ArrayList<>();
        outer.setOnDrop((dragSource, kind, payload) -> {
            drops.add("outer");
            return true;
        });
        inner.setOnDrop((dragSource, kind, payload) -> {
            drops.add("inner");
            return true;
        });
        manager.add(source).add(outer);

        startDrag(manager, 20, 20, 240, 100);

        assertSame(inner, manager.context().dragSession().target());
        assertFalse(inner.isDropAccepted());
        assertFalse(outer.isDragTarget());

        manager.handle(new PointerReleased(240, 100, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
    }

    @Test
    void reenteringTheWindowWhileDraggingMovesTheGhostAndRetargetsZones() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone first = new DropZone(KIND);
        first.setBounds(new Rect(200, 10, 120, 80));
        DropZone second = new DropZone(KIND);
        second.setBounds(new Rect(350, 200, 120, 80));
        manager.add(source).add(first).add(second);

        startDrag(manager, 20, 20, 260, 50);
        assertSame(first, manager.context().dragSession().target());

        manager.handle(new PointerExited(900, 700));
        manager.handle(new PointerEntered(400, 240));

        Widget ghost = ghost(manager);
        assertEquals(400 + 14, ghost.bounds().x(), 0.001f);
        assertEquals(240 + 14, ghost.bounds().y(), 0.001f);
        assertSame(second, manager.context().dragSession().target());
        assertTrue(second.isDragTarget());
        assertFalse(first.isDragTarget());

        manager.handle(new PointerReleased(400, 240, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void aZoneDisabledDuringTheDragStopsReportingAcceptance() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(200, 10, 160, 100));
        manager.add(source).add(zone);

        startDrag(manager, 20, 20, 250, 40);
        assertSame(zone, manager.context().dragSession().target());
        assertTrue(zone.isDragTarget());
        assertTrue(zone.isDropAccepted());
        assertTrue(manager.context().dragSession().isAccepted());

        zone.setEnabled(false);

        assertFalse(zone.isDropAccepted());
        assertFalse(manager.context().dragSession().isAccepted());
        assertFalse(zone.isDragTarget());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void aZoneHiddenDuringTheDragStopsReportingAcceptance() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(200, 10, 160, 100));
        manager.add(source).add(zone);

        startDrag(manager, 20, 20, 250, 40);
        assertTrue(zone.isDropAccepted());

        zone.setVisible(false);

        assertFalse(zone.isDropAccepted());
        assertFalse(manager.context().dragSession().isAccepted());
        assertFalse(zone.isDragTarget());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void movingOutOfTheZoneBeforeReleaseDoesNotDrop() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(200, 10, 160, 100));
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 20, 20, 250, 40);
        assertSame(zone, manager.context().dragSession().target());

        manager.handle(new PointerMoved(600, 400));
        assertNull(manager.context().dragSession().target());
        assertFalse(zone.isDragTarget());
        manager.handle(new PointerReleased(600, 400, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertNull(manager.context().dragSession());
    }

    @Test
    void aZoneInsideADisabledAncestorStopsAcceptingMidDrag() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        Panel parent = new Panel();
        parent.setBounds(new Rect(180, 0, 220, 160));
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(20, 10, 160, 100));
        parent.add(zone);
        manager.add(source).add(parent);

        startDrag(manager, 20, 20, 250, 40);
        assertTrue(zone.isDropAccepted());

        parent.setEnabled(false);

        assertFalse(zone.isDropAccepted());
        assertFalse(zone.isDragTarget());
        assertFalse(manager.context().dragSession().isAccepted());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void removingTheTargetZoneMidDragStopsAcceptanceAndDrop() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        DropZone zone = new DropZone(KIND);
        zone.setBounds(new Rect(200, 10, 160, 100));
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 20, 20, 250, 40);
        assertTrue(manager.context().dragSession().isAccepted());

        manager.remove(zone);

        assertFalse(manager.context().dragSession().isAccepted());
        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertNull(manager.context().dragSession());
    }

    @Test
    void scrollingDuringADragRetargetsTheZoneUnderThePointer() {
        WidgetManager manager = manager();
        ScrollPanel scroll = new ScrollPanel();
        scroll.setBounds(new Rect(0, 0, 200, 150));
        Panel content = new Panel();
        content.setSize(200, 600);
        DropZone upper = new DropZone(KIND);
        upper.setBounds(new Rect(10, 100, 180, 40));
        DropZone lower = new DropZone(KIND);
        lower.setBounds(new Rect(10, 300, 180, 40));
        List<String> drops = new ArrayList<>();
        upper.setOnDrop((dragSource, kind, payload) -> {
            drops.add("upper");
            return true;
        });
        lower.setOnDrop((dragSource, kind, payload) -> {
            drops.add("lower");
            return true;
        });
        content.add(upper).add(lower);
        scroll.setContent(content);
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(new Rect(300, 20, 80, 30));
        manager.add(scroll).add(source);
        manager.layout();

        startDrag(manager, 320, 30, 50, 120);
        assertSame(upper, manager.context().dragSession().target());

        scroll.setScroll(0, 200);
        manager.update(0.016f);

        assertSame(lower, manager.context().dragSession().target());
        assertTrue(lower.isDragTarget());
        assertFalse(upper.isDragTarget());

        manager.handle(new PointerReleased(50, 120, MouseButton.LEFT, Modifiers.NONE));
        assertEquals(List.of("lower"), drops);
    }

    private static Widget ghost(WidgetManager manager) {
        Widget ghost = null;
        for (Widget widget : manager.widgets()) {
            if (widget != null && widget.isAlwaysOnTop() && widget.hitTest(1, 1) == null) {
                ghost = widget;
            }
        }
        assertTrue(ghost != null, "Expected a drag preview.");
        return ghost;
    }

    private static void startDrag(WidgetManager manager, float pressX, float pressY,
                                  float moveX, float moveY) {
        manager.handle(new PointerPressed(pressX, pressY, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(moveX, moveY));
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static final class DragSource extends Widget {
        private final String kind;
        private final Object payload;
        private boolean dragEnded;

        private DragSource(String kind, Object payload) {
            this.kind = kind;
            this.payload = payload;
        }

        @Override
        protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
            context().startDrag(this, button, kind, payload, "Dragging " + payload);
            return true;
        }

        @Override
        protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
            dragEnded = true;
            return true;
        }
    }
}
