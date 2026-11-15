package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.FocusChanged;
import com.lixui.input.InputEvent;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.PointerMoved;
import com.lixui.input.PointerPressed;
import com.lixui.input.PointerReleased;
import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.Antialiasing;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetManagerTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);
    private static final Rect ROOT_BOUNDS = new Rect(0, 0, 200, 200);
    private static final Rect CHILD_BOUNDS = new Rect(50, 50, 100, 100);

    @Test
    void resizeDelegatesAndExposesTheLogicalViewport() {
        RecordingBackend backend = new RecordingBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);

        manager.resize(VIEWPORT);

        assertSame(VIEWPORT, backend.viewport);
        assertEquals(new Rect(0, 0, 800, 600), manager.viewport());
    }

    @Test
    void renderDrawsTopLevelWidgetsInInsertionOrder() {
        RecordingBackend backend = new RecordingBackend();
        List<String> log = new ArrayList<>();
        Probe first = new Probe("first", log);
        Probe second = new Probe("second", log);
        first.setBounds(ROOT_BOUNDS);
        second.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(backend, first, second);

        manager.render();

        assertEquals(List.of("draw:first", "draw:second"), log);
        assertEquals(2, backend.lastDrawList.commands().size());
    }

    @Test
    void nestedWidgetsDrawParentFirstAndHitTestTheChildFirst() {
        RecordingBackend backend = new RecordingBackend();
        List<String> log = new ArrayList<>();
        Probe root = new Probe("root", log);
        Probe child = new Probe("child", log);
        root.setBounds(ROOT_BOUNDS);
        child.setBounds(CHILD_BOUNDS);
        root.add(child);
        WidgetManager manager = manager(backend, root);

        manager.render();
        assertEquals(List.of("draw:root", "draw:child"), log);

        manager.handle(new PointerMoved(100, 100));
        assertSame(child, manager.hovered());
        assertTrue(child.isHovered());

        manager.handle(new PointerMoved(10, 10));
        assertSame(root, manager.hovered());
        assertFalse(child.isHovered());
        assertTrue(root.isHovered());

        manager.handle(new PointerMoved(400, 400));
        assertNull(manager.hovered());
        assertFalse(root.isHovered());
    }

    @Test
    void updateDrainsInputAndUpdatesEveryTopLevelWidget() {
        RecordingBackend backend = new RecordingBackend();
        InputState input = new InputState();
        List<String> log = new ArrayList<>();
        Probe root = new Probe("root", log);
        root.setBounds(ROOT_BOUNDS);
        WidgetManager manager = new WidgetManager(input, backend);
        manager.resize(VIEWPORT);
        manager.add(root);
        input.pointerMoved(10, 10);

        manager.update(0.25f);

        assertTrue(root.isHovered());
        assertEquals(List.of("update:root"), log);
        assertFalse(input.hasEvents());
    }

    @Test
    void pressCapturesANestedWidgetUntilRelease() {
        RecordingBackend backend = new RecordingBackend();
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        child.setBounds(CHILD_BOUNDS);
        child.setFocusable(true);
        root.add(child);
        WidgetManager manager = manager(backend, root);

        manager.handle(new PointerMoved(100, 100));
        manager.handle(new PointerPressed(100, 100, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(child.isPressed());
        assertTrue(child.isFocused());

        manager.handle(new PointerMoved(400, 400));
        assertFalse(child.isHovered());
        assertTrue(child.events.contains("move:400,400"));

        manager.handle(new PointerReleased(400, 400, MouseButton.LEFT, Modifiers.NONE));
        assertFalse(child.isPressed());
        assertTrue(child.events.contains("release:LEFT:400,400"));
    }

    @Test
    void keyboardEventsGoToTheFocusedChild() {
        RecordingBackend backend = new RecordingBackend();
        InputState input = new InputState();
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        child.setBounds(CHILD_BOUNDS);
        child.setFocusable(true);
        root.add(child);
        WidgetManager manager = new WidgetManager(input, backend);
        manager.resize(VIEWPORT);
        manager.add(root);
        manager.handle(new PointerPressed(100, 100, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(100, 100, MouseButton.LEFT, Modifiers.NONE));

        input.keyPressed(Key.A, Modifiers.NONE, false);
        input.charTyped(0x436);
        input.keyReleased(Key.A, Modifiers.NONE);
        manager.update(0);

        assertTrue(child.events.contains("key:A:false"));
        assertTrue(child.events.contains("char:1078"));
        assertTrue(child.events.contains("key-up:A"));
    }

    @Test
    void updateAdvancesFrameInfoAndPassesItToUpdateAndDraw() {
        RecordingBackend backend = new RecordingBackend();
        List<String> log = new ArrayList<>();
        Probe root = new Probe("root", log);
        root.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(backend, root);

        manager.update(0.25f);
        manager.update(0.5f);
        manager.render();

        assertEquals(0.5f, manager.frame().deltaSeconds());
        assertEquals(0.75, manager.frame().elapsedSeconds(), 0.0001);
        assertEquals(2, manager.frame().frameIndex());
        assertSame(manager.frame(), root.lastUpdateFrame);
        assertSame(manager.frame(), root.lastDrawFrame);
    }

    @Test
    void removingATopLevelWidgetClearsItsInteraction() {
        RecordingBackend backend = new RecordingBackend();
        Probe root = new Probe("root", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        root.setFocusable(true);
        WidgetManager manager = manager(backend, root);
        manager.handle(new PointerMoved(10, 10));
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(manager.remove(root));

        assertNull(manager.hovered());
        assertNull(manager.focused());
        assertFalse(root.isHovered());
        assertFalse(root.isFocused());
        assertFalse(root.isPressed());
    }

    @Test
    void focusLossClearsHoverAndCapturesButKeepsWidgetFocus() {
        RecordingBackend backend = new RecordingBackend();
        Probe root = new Probe("root", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        root.setFocusable(true);
        WidgetManager manager = manager(backend, root);
        manager.handle(new PointerMoved(10, 10));
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));

        manager.handle(new FocusChanged(false));

        assertFalse(root.isPressed());
        assertFalse(root.isHovered());
        assertNull(manager.hovered());
        assertTrue(root.isFocused());
    }

    @Test
    void addRejectsParentedAndDuplicateWidgets() {
        RecordingBackend backend = new RecordingBackend();
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        WidgetManager manager = manager(backend, root);
        root.add(child);

        assertThrows(IllegalArgumentException.class, () -> manager.add(child));
        assertThrows(IllegalArgumentException.class, () -> manager.add(root));
        assertThrows(NullPointerException.class, () -> manager.add(null));
    }

    @Test
    void focusRejectsForeignWidgets() {
        RecordingBackend backend = new RecordingBackend();
        Probe root = new Probe("root", new ArrayList<>());
        Probe foreign = new Probe("foreign", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        root.setFocusable(true);
        foreign.setFocusable(true);
        WidgetManager manager = manager(backend, root);

        assertThrows(IllegalArgumentException.class, () -> manager.focus(foreign));
    }

    @Test
    void delegatesAntialiasingSettings() {
        RecordingBackend backend = new RecordingBackend();
        WidgetManager manager = manager(backend, new Probe("root", new ArrayList<>()));

        manager.setAntialiasing(Antialiasing.HIGH);

        assertEquals(Antialiasing.HIGH, manager.antialiasing());
    }

    @Test
    void rejectsNullArgumentsInvalidDeltaAndRenderBeforeResize() {
        RecordingBackend backend = new RecordingBackend();
        InputState input = new InputState();
        WidgetManager manager = new WidgetManager(input, backend);

        assertThrows(NullPointerException.class, () -> new WidgetManager(null, backend));
        assertThrows(NullPointerException.class, () -> new WidgetManager(input, null));
        assertThrows(NullPointerException.class, () -> manager.handle((InputEvent) null));
        assertThrows(NullPointerException.class, () -> manager.remove(null));
        assertThrows(IllegalArgumentException.class, () -> manager.update(-1));
        assertThrows(IllegalArgumentException.class, () -> manager.update(Float.NaN));
        assertThrows(IllegalStateException.class, manager::render);
    }

    private static WidgetManager manager(RecordingBackend backend, Widget... widgets) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        for (Widget widget : widgets) {
            manager.add(widget);
        }
        return manager;
    }

    private static final class Probe extends Widget {
        private final String name;
        private final List<String> log;
        private final List<String> events = new ArrayList<>();
        private FrameInfo lastUpdateFrame;
        private FrameInfo lastDrawFrame;

        Probe(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        /**
         * Records the update call and frame timing.
         *
         * @param frame timing information for the current frame
         */
        @Override
        protected void onUpdate(FrameInfo frame) {
            lastUpdateFrame = frame;
            log.add("update:" + name);
        }

        /**
         * Records the draw call and paints a white rectangle.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            lastDrawFrame = frame;
            log.add("draw:" + name);
            renderer.rect(bounds(), Color.WHITE);
        }

        /**
         * Records a pointer enter.
         */
        @Override
        protected void onPointerEnter() {
            events.add("enter");
        }

        /**
         * Records a pointer exit.
         */
        @Override
        protected void onPointerExit() {
            events.add("exit");
        }

        /**
         * Records pointer movement.
         *
         * @param x horizontal pointer position in logical UI units
         * @param y vertical pointer position in logical UI units
         */
        @Override
        protected void onPointerMove(float x, float y) {
            events.add("move:" + Math.round(x) + "," + Math.round(y));
        }

        /**
         * Records a pointer press.
         *
         * @param x horizontal pointer position in logical UI units
         * @param y vertical pointer position in logical UI units
         * @param button pressed button
         * @param modifiers active keyboard modifiers
         */
        @Override
        protected void onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            events.add("press:" + button + ":" + Math.round(x) + "," + Math.round(y));
        }

        /**
         * Records a pointer release.
         *
         * @param x horizontal pointer position in logical UI units
         * @param y vertical pointer position in logical UI units
         * @param button released button
         * @param modifiers active keyboard modifiers
         */
        @Override
        protected void onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
            events.add("release:" + button + ":" + Math.round(x) + "," + Math.round(y));
        }

        /**
         * Records a key press.
         *
         * @param key pressed key
         * @param modifiers active keyboard modifiers
         * @param repeat true for a repeated key press
         */
        @Override
        protected void onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
            events.add("key:" + key + ":" + repeat);
        }

        /**
         * Records a key release.
         *
         * @param key released key
         * @param modifiers active keyboard modifiers
         */
        @Override
        protected void onKeyReleased(Key key, Modifiers modifiers) {
            events.add("key-up:" + key);
        }

        /**
         * Records a typed code point.
         *
         * @param codepoint typed code point
         */
        @Override
        protected void onCharTyped(int codepoint) {
            events.add("char:" + codepoint);
        }

        /**
         * Records a focus change.
         *
         * @param focused true when the widget gained focus
         */
        @Override
        protected void onFocusChanged(boolean focused) {
            events.add("focus:" + focused);
        }
    }

    private static final class RecordingBackend implements RenderBackend {
        private Viewport viewport;
        private DrawList lastDrawList;

        @Override
        public void resize(Viewport viewport) {
            this.viewport = viewport;
        }

        @Override
        public void draw(DrawList drawList) {
            lastDrawList = drawList;
        }

        @Override
        public Texture createTexture(ImageData image) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroyTexture(Texture texture) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
