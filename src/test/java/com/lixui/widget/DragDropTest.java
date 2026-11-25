package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.FocusChanged;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragDropTest {
    private static final String KIND = "card";
    private static final Rect SOURCE_BOUNDS = new Rect(10, 10, 100, 40);
    private static final Rect ZONE_BOUNDS = new Rect(200, 10, 160, 100);

    @Test
    void startsAfterTheDragThresholdAndAddsANonBlockingPreview() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone("other");
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        manager.add(source).add(zone);

        manager.handle(new PointerPressed(20, 20, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(22, 20));

        assertNull(source.session);
        assertEquals(2, manager.widgets().size());

        manager.handle(new PointerMoved(40, 20));

        assertNotNull(source.session);
        assertTrue(source.session.isActive());
        assertEquals(3, manager.widgets().size());
        Widget preview = manager.widgets().get(2);
        assertTrue(preview.isAlwaysOnTop());
        assertNull(preview.hitTest(1, 1));

        manager.handle(new PointerMoved(250, 40));
        assertNotSame(preview, manager.hovered());
        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void acceptingZoneReceivesThePayloadAndMarksTheDropHandled() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind + ":" + payload + ":" + (dragSource == source));
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 250, 40);

        assertSame(zone, source.session.target());
        assertTrue(zone.isDragTarget());
        assertTrue(zone.isDropAccepted());
        assertTrue(source.session.isAccepted());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of("card:value:true"), drops);
        assertTrue(source.session.isHandled());
        assertTrue(source.dragEndHandled);
        assertFalse(source.session.isActive());
        assertNull(manager.context().dragSession());
        assertEquals(2, manager.widgets().size());
        assertFalse(zone.isDragTarget());
        assertTrue(source.dragEnded);
    }

    @Test
    void rejectingZoneAndOutsideReleaseCancelTheDrop() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone("other");
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 250, 40);

        assertSame(zone, source.session.target());
        assertTrue(zone.isDragTarget());
        assertFalse(zone.isDropAccepted());
        assertFalse(source.session.isAccepted());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertFalse(source.session.isHandled());
        assertFalse(source.dragEndHandled);
        assertNull(manager.context().dragSession());
        assertEquals(2, manager.widgets().size());

        startDrag(manager, 700, 500);

        assertNull(source.session.target());
        manager.handle(new PointerReleased(700, 500, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertFalse(source.session.isHandled());
        assertNull(manager.context().dragSession());
    }

    @Test
    void dropCallbackResultControlsTheHandledFlag() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return false;
        });
        manager.add(source).add(zone);

        startDrag(manager, 250, 40);
        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of(KIND), drops);
        assertFalse(source.session.isHandled());
        assertNull(manager.context().dragSession());
    }

    @Test
    void escapeCancelsTheDragWithoutADropOrAClick() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 250, 40);
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertFalse(source.session.isActive());
        assertNull(manager.context().dragSession());
        assertTrue(source.dragEnded);
        assertEquals(2, manager.widgets().size());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertEquals(0, source.clicks);
    }

    @Test
    void secondButtonDoesNotEndAnotherButtonsDragAndDropRunsOnce() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        CaptureProbe other = new CaptureProbe();
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        other.setBounds(new Rect(400, 200, 80, 40));
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone).add(other);

        startDrag(manager, 250, 40);
        DragSession session = source.session;

        manager.handle(new PointerPressed(420, 220, MouseButton.RIGHT, Modifiers.NONE));
        manager.handle(new PointerReleased(420, 220, MouseButton.RIGHT, Modifiers.NONE));

        assertSame(session, manager.context().dragSession());
        assertTrue(session.isActive());
        assertTrue(drops.isEmpty());
        assertEquals(4, manager.widgets().size());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(1, drops.size());
        assertNull(manager.context().dragSession());
        assertEquals(3, manager.widgets().size());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(1, drops.size());
    }

    @Test
    void escapeCancelsOnlyTheDragGestureAndKeepsOtherCaptures() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        CaptureProbe other = new CaptureProbe();
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        other.setBounds(new Rect(400, 200, 80, 40));
        manager.add(source).add(zone).add(other);

        startDrag(manager, 250, 40);
        manager.handle(new PointerPressed(420, 220, MouseButton.RIGHT, Modifiers.NONE));
        manager.handle(new KeyPressed(Key.ESCAPE, Modifiers.NONE, false));

        assertNull(manager.context().dragSession());
        assertFalse(source.session.isActive());
        assertTrue(source.dragEnded);
        assertTrue(other.isPressed());
        assertEquals(3, manager.widgets().size());

        manager.handle(new PointerReleased(420, 220, MouseButton.RIGHT, Modifiers.NONE));

        assertEquals(1, other.releases);
        assertFalse(other.isPressed());
    }

    @Test
    void previewMeasuresItsContentInsteadOfTheViewport() {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        WidgetManager manager = manager();
        Font font = manager.context().createFont(data.get(), 16);
        manager.setDefaultFont(font);
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        manager.add(source);

        startDrag(manager, 300, 100);

        Widget preview = manager.widgets().get(1);
        Size text = font.measure("Dragging value");
        assertTrue(preview.bounds().width() >= text.width());
        assertTrue(preview.bounds().width() <= text.width() + 40);
        assertTrue(preview.bounds().height() <= text.height() + 40);
        assertTrue(preview.bounds().width() < manager.viewport().width());
        assertTrue(preview.bounds().height() < manager.viewport().height());
    }

    @Test
    void losingWindowFocusCancelsTheDrag() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        manager.add(source);

        startDrag(manager, 300, 100);
        manager.handle(new FocusChanged(false));

        assertFalse(source.session.isActive());
        assertNull(manager.context().dragSession());
        assertEquals(1, manager.widgets().size());
    }

    @Test
    void altTabFocusLossCancelsTheDragBeforeReleaseAndClearsGhost() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 250, 40);
        DragSession session = source.session;
        manager.handle(new FocusChanged(false));

        assertFalse(session.isActive());
        assertNull(manager.context().dragSession());
        assertEquals(1, source.captureCancellations);
        assertEquals(2, manager.widgets().size());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertEquals(0, source.clicks);
        assertEquals(2, manager.widgets().size());
    }

    @Test
    void sameButtonRecaptureCancelsTheDragWithoutDropOrGhost() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        CaptureProbe other = new CaptureProbe();
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        other.setBounds(new Rect(400, 200, 80, 40));
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone).add(other);

        startDrag(manager, 250, 40);
        DragSession session = source.session;
        manager.handle(new PointerPressed(420, 220, MouseButton.LEFT, Modifiers.NONE));

        assertFalse(session.isActive());
        assertNull(manager.context().dragSession());
        assertEquals(1, source.captureCancellations);
        assertTrue(other.isPressed());
        assertEquals(3, manager.widgets().size());

        manager.handle(new PointerReleased(420, 220, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertEquals(1, other.releases);
        assertFalse(other.isPressed());
        assertEquals(0, source.clicks);
    }

    @Test
    void releasingCaptureCancelsTheDragAndNotifiesTheSource() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        List<String> drops = new ArrayList<>();
        zone.setOnDrop((dragSource, kind, payload) -> {
            drops.add(kind);
            return true;
        });
        manager.add(source).add(zone);

        startDrag(manager, 250, 40);
        DragSession session = source.session;
        manager.releasePointerCapture(MouseButton.LEFT);

        assertFalse(session.isActive());
        assertNull(manager.context().dragSession());
        assertEquals(1, source.captureCancellations);
        assertEquals(2, manager.widgets().size());

        manager.handle(new PointerReleased(250, 40, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(drops.isEmpty());
        assertEquals(0, source.clicks);
    }

    @Test
    void detachingTheSourceCancelsTheDrag() {
        WidgetManager manager = manager();
        DragSource source = new DragSource(KIND, "value");
        DropZone zone = new DropZone(KIND);
        source.setBounds(SOURCE_BOUNDS);
        zone.setBounds(ZONE_BOUNDS);
        manager.add(source).add(zone);

        startDrag(manager, 250, 40);
        manager.remove(source);

        assertFalse(source.session.isActive());
        assertNull(manager.context().dragSession());
        assertEquals(1, manager.widgets().size());
    }

    @Test
    void rejectsUnattachedSourceAndConcurrentSessions() {
        WidgetManager manager = manager();
        DragSource detached = new DragSource(KIND, "value");

        assertThrows(IllegalArgumentException.class,
                () -> manager.context().startDrag(detached, MouseButton.LEFT, KIND, null, "Drag"));

        DragSource source = new DragSource(KIND, "value");
        source.setBounds(SOURCE_BOUNDS);
        manager.add(source);
        startDrag(manager, 300, 100);

        assertThrows(IllegalStateException.class,
                () -> manager.context().startDrag(source, MouseButton.LEFT, KIND, null, "Drag"));

        manager.context().cancelDrag();
        manager.handle(new PointerReleased(300, 100, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void dropZoneKindFilterCanAcceptEveryPayload() {
        DropZone zone = new DropZone();

        assertTrue(zone.accepts("anything"));
        zone.setAcceptedKind("quest");
        assertFalse(zone.accepts("loot"));
        assertTrue(zone.accepts("quest"));
        zone.setAcceptedKind(null);
        assertTrue(zone.accepts("loot"));
    }

    private static void startDrag(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(20, 20, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(x, y));
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(TestViewports.HIDPI);
        return manager;
    }

    private static final class CaptureProbe extends Widget {
        private int releases;

        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
            releases++;
            return true;
        }
    }

    private static final class DragSource extends Widget {
        private final String kind;
        private final Object payload;
        private DragSession session;
        private boolean dragEnded;
        private boolean dragEndHandled;
        private int captureCancellations;
        private int clicks;

        private DragSource(String kind, Object payload) {
            this.kind = kind;
            this.payload = payload;
        }

        @Override
        protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
            session = context().startDrag(this, button, kind, payload, "Dragging " + payload);
            return true;
        }

        @Override
        protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
            dragEnded = true;
            dragEndHandled = session != null && session.isHandled();
            return true;
        }

        @Override
        protected void onPointerCaptureCancelled(MouseButton button) {
            captureCancellations++;
        }

        @Override
        protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
            clicks++;
            return true;
        }
    }
}
