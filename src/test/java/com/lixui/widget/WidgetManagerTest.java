package com.lixui.widget;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.event.FocusChanged;
import com.lixui.input.event.CharTyped;
import com.lixui.input.event.InputEvent;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.event.KeyPressed;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.platform.Cursor;
import com.lixui.platform.UiPlatform;
import com.lixui.render.Antialiasing;
import com.lixui.render.RenderManager;
import com.lixui.render.Viewport;
import com.lixui.theme.Theme;
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
    private static final Viewport VIEWPORT = TestViewports.HIDPI;
    private static final Rect ROOT_BOUNDS = new Rect(0, 0, 200, 200);
    private static final Rect CHILD_BOUNDS = new Rect(50, 50, 100, 100);

    @Test
    void resizeDelegatesAndExposesTheLogicalViewport() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);

        manager.resize(VIEWPORT);

        assertSame(VIEWPORT, backend.viewport());
        assertEquals(new Rect(0, 0, 800, 600), manager.viewport());
    }

    @Test
    void renderDrawsTopLevelWidgetsInInsertionOrder() {
        TestBackend backend = new TestBackend();
        List<String> log = new ArrayList<>();
        Probe first = new Probe("first", log);
        Probe second = new Probe("second", log);
        first.setBounds(ROOT_BOUNDS);
        second.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(backend, first, second);

        manager.render();

        assertEquals(List.of("draw:first", "draw:second"), log);
        assertEquals(2, backend.lastDrawList().commands().size());
    }

    @Test
    void hitTestingConvertsViewportCoordinatesToTopLevelLocalCoordinates() {
        TestBackend backend = new TestBackend();
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(new Rect(100, 50, 200, 200));
        child.setBounds(new Rect(10, 20, 50, 50));
        root.add(child);
        WidgetManager manager = manager(backend, root);

        manager.handle(new PointerMoved(115, 75));
        assertSame(child, manager.hovered());

        manager.handle(new PointerPressed(115, 75, MouseButton.LEFT, Modifiers.NONE));
        assertTrue(child.events.contains("press:LEFT:5,5"));

        manager.handle(new PointerMoved(5, 5));
        assertNull(manager.hovered());
    }

    @Test
    void renderRunsALayoutPassBeforeDrawing() {
        TestBackend backend = new TestBackend();
        Probe root = new Probe("root", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(backend, root);

        manager.render();

        assertEquals(1, root.layoutCount);
    }

    @Test
    void attachingAndRemovingWidgetsRunsLifecycleHooks() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        List<String> log = new ArrayList<>();
        LifecycleProbe root = new LifecycleProbe("root", log);
        LifecycleProbe child = new LifecycleProbe("child", log);
        root.add(child);

        manager.add(root);

        assertEquals(List.of("attach:root", "attach:child"), log);
        assertSame(manager.context(), root.context());
        assertSame(manager.context(), child.context());
        assertSame(manager.context(), root.attachedContext);
        assertSame(manager.context(), child.attachedContext);

        log.clear();
        root.remove(child);

        assertEquals(List.of("detach:child"), log);
        assertNull(child.context());
        assertSame(manager.context(), child.detachedContext);

        log.clear();
        LifecycleProbe late = new LifecycleProbe("late", log);
        root.add(late);
        assertEquals(List.of("attach:late"), log);

        log.clear();
        manager.remove(root);

        assertEquals(List.of("detach:late", "detach:root"), log);
        assertNull(root.context());
        assertSame(manager.context(), root.detachedContext);
    }

    @Test
    void contextChangesNotifyAttachedSubtrees() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        List<String> log = new ArrayList<>();
        LifecycleProbe root = new LifecycleProbe("root", log);
        LifecycleProbe child = new LifecycleProbe("child", log);
        root.add(child);
        manager.add(root);
        log.clear();

        manager.resize(VIEWPORT);

        assertEquals(List.of("context:root", "context:child"), log);
        assertEquals(new Rect(0, 0, 800, 600), manager.context().viewport());
        assertEquals(2, manager.context().scaleX());

        log.clear();
        manager.setTheme(Theme.light());

        assertEquals(List.of("context:root", "context:child"), log);
        assertEquals(Theme.light(), manager.context().theme());

        log.clear();
        manager.setTheme(Theme.light());
        assertTrue(log.isEmpty());
    }

    @Test
    void closeDetachesWidgetsAndRejectsFurtherFrames() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        List<String> log = new ArrayList<>();
        LifecycleProbe root = new LifecycleProbe("root", log);
        LifecycleProbe child = new LifecycleProbe("child", log);
        root.add(child);
        manager.add(root);
        log.clear();

        manager.close();

        assertEquals(List.of("detach:child", "detach:root"), log);
        assertNull(root.context());
        assertThrows(IllegalStateException.class, () -> manager.update(0));
        assertThrows(IllegalStateException.class, manager::render);
        assertThrows(IllegalStateException.class, () -> manager.context().clipboardText());
        manager.close();
    }

    @Test
    void pointerEventsBubbleUntilConsumed() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        List<String> log = new ArrayList<>();
        BubbleProbe root = new BubbleProbe("root", log);
        BubbleProbe child = new BubbleProbe("child", log);
        root.setBounds(new Rect(0, 0, 100, 100));
        child.setBounds(new Rect(0, 0, 100, 100));
        root.add(child);
        manager.add(root);

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of("press:child", "press:root", "release:child", "release:root",
                "click:child", "click:root"), log);

        log.clear();
        child.consumePress = true;
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of("press:child"), log);
    }

    @Test
    void blocksHostPointerInputInsideUiAndDuringCapturedDrags() {
        WidgetManager manager = manager(new TestBackend());
        Panel panel = new Panel();
        panel.setBounds(new Rect(10, 10, 100, 100));
        manager.add(panel);
        manager.render();

        assertTrue(manager.blocksPointer(50, 50));
        assertFalse(manager.blocksPointer(500, 500));
        assertFalse(manager.blocksPointer(5, 5));
        assertThrows(IllegalArgumentException.class, () -> manager.blocksPointer(Float.NaN, 1));

        manager.handle(new PointerPressed(50, 50, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(500, 500));
        assertTrue(manager.blocksPointer(500, 500));
        manager.handle(new PointerReleased(500, 500, MouseButton.LEFT, Modifiers.NONE));
        assertFalse(manager.blocksPointer(500, 500));

        panel.setVisible(false);
        assertFalse(manager.blocksPointer(50, 50));
        manager.close();
        assertFalse(manager.blocksPointer(50, 50));
    }

    @Test
    void tabTraversalFollowsDepthFirstFocusOrder() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        Probe root = new Probe("root", new ArrayList<>());
        Probe first = new Probe("first", new ArrayList<>());
        Probe second = new Probe("second", new ArrayList<>());
        Probe third = new Probe("third", new ArrayList<>());
        root.setBounds(new Rect(0, 0, 300, 300));
        first.setBounds(new Rect(0, 0, 50, 50));
        second.setBounds(new Rect(50, 50, 50, 50));
        third.setBounds(new Rect(100, 100, 50, 50));
        first.setFocusable(true);
        second.setFocusable(true);
        third.setFocusable(true);
        root.add(first);
        root.add(second);
        root.add(third);
        manager.add(root);

        manager.focus(first);
        manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
        assertSame(second, manager.focused());

        manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
        assertSame(third, manager.focused());

        manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
        assertSame(first, manager.focused());

        manager.handle(new KeyPressed(Key.TAB, new Modifiers(true, false, false, false), false));
        assertSame(third, manager.focused());
    }

    @Test
    void tabTraversalStaysInsideTheNearestFocusScope() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        Probe root = new Probe("root", new ArrayList<>());
        Probe scope = new Probe("scope", new ArrayList<>());
        Probe first = new Probe("first", new ArrayList<>());
        Probe second = new Probe("second", new ArrayList<>());
        Probe outside = new Probe("outside", new ArrayList<>());
        root.setBounds(new Rect(0, 0, 300, 300));
        scope.setBounds(new Rect(0, 0, 100, 100));
        scope.setFocusScope(true);
        first.setBounds(new Rect(0, 0, 50, 50));
        second.setBounds(new Rect(50, 50, 50, 50));
        outside.setBounds(new Rect(150, 150, 50, 50));
        first.setFocusable(true);
        second.setFocusable(true);
        outside.setFocusable(true);
        scope.add(first);
        scope.add(second);
        root.add(scope);
        root.add(outside);
        manager.add(root);

        manager.focus(first);
        manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
        assertSame(second, manager.focused());

        manager.handle(new KeyPressed(Key.TAB, Modifiers.NONE, false));
        assertSame(first, manager.focused());
    }

    @Test
    void generatesClicksDoubleClicksDragsAndLongPresses() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        List<String> log = new ArrayList<>();
        GestureProbe root = new GestureProbe(log);
        root.setBounds(new Rect(0, 0, 100, 100));
        manager.add(root);

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(log.contains("click:LEFT:1"));
        log.clear();

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(log.contains("double:LEFT"));
        log.clear();

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(20, 10));
        manager.handle(new PointerReleased(20, 10, MouseButton.LEFT, Modifiers.NONE));

        assertEquals(List.of("press:LEFT:10,10", "move:20,10", "drag-start:LEFT:20,10",
                "drag:LEFT:20,10", "release:LEFT:20,10", "drag-end:LEFT:20,10"), log);
        assertFalse(log.contains("click:LEFT:1"));
        log.clear();

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.update(0.6f);
        manager.handle(new PointerReleased(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(log.contains("long:LEFT:10,10"));
        assertFalse(log.contains("click:LEFT:1"));
    }

    @Test
    void stateChangesClearHoverFocusAndCaptures() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        Probe root = new Probe("root", new ArrayList<>());
        root.setBounds(new Rect(0, 0, 100, 100));
        root.setFocusable(true);
        manager.add(root);
        manager.handle(new PointerMoved(10, 10));
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertSame(root, manager.hovered());
        assertSame(root, manager.focused());
        assertTrue(root.isPressed());

        root.setVisible(false);

        assertNull(manager.hovered());
        assertNull(manager.focused());
        assertFalse(root.isHovered());
        assertFalse(root.isFocused());
        assertFalse(root.isPressed());

        root.setVisible(true);
        manager.focus(root);
        root.setEnabled(false);

        assertNull(manager.focused());
        assertFalse(root.isEnabled());

        root.setEnabled(true);
        manager.focus(root);
        root.setFocusable(false);

        assertNull(manager.focused());
    }

    @Test
    void hoverFollowsBoundsChangesAndCursorUsesTheHoveredAncestor() {
        TestBackend backend = new TestBackend();
        RecordingPlatform platform = new RecordingPlatform();
        WidgetManager manager = new WidgetManager(new InputState(), backend, platform);
        CursorProbe root = new CursorProbe();
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(new Rect(0, 0, 100, 100));
        child.setBounds(new Rect(0, 0, 100, 100));
        root.add(child);
        manager.add(root);

        manager.handle(new PointerMoved(10, 10));
        assertSame(child, manager.hovered());
        assertEquals(Cursor.POINTER, platform.cursor);
        assertEquals(Cursor.POINTER, manager.context().cursor());

        root.setBounds(new Rect(200, 200, 100, 100));
        manager.layout();

        assertNull(manager.hovered());
        assertEquals(Cursor.DEFAULT, platform.cursor);
    }

    @Test
    void explicitPointerCaptureSetsAndClearsPressedState() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        Probe root = new Probe("root", new ArrayList<>());
        root.setBounds(new Rect(0, 0, 100, 100));
        manager.add(root);

        manager.capturePointer(root, MouseButton.LEFT);
        assertTrue(root.isPressed());

        manager.releasePointerCapture(MouseButton.LEFT);
        assertFalse(root.isPressed());

        manager.context().capturePointer(root, MouseButton.LEFT);
        assertTrue(root.isPressed());
        manager.context().releasePointerCapture(MouseButton.LEFT);
        assertFalse(root.isPressed());
    }

    @Test
    void removesVisibleTopLevelWidgetsThatLieCompletelyOutsideTheViewport() {
        Probe outside = new Probe("outside", new ArrayList<>());
        Probe reachable = new Probe("reachable", new ArrayList<>());
        outside.setBounds(new Rect(900, 0, 100, 100));
        reachable.setBounds(new Rect(-50, 0, 100, 100));
        WidgetManager manager = manager(new TestBackend(), outside, reachable);

        manager.layout();

        assertEquals(List.of(reachable), manager.widgets());
        assertNull(outside.context());
    }

    @Test
    void keepsHiddenWidgetsBeyondTheViewport() {
        Probe hidden = new Probe("hidden", new ArrayList<>());
        hidden.setBounds(new Rect(900, 0, 100, 100));
        hidden.setVisible(false);
        WidgetManager manager = manager(new TestBackend(), hidden);

        manager.layout();

        assertEquals(List.of(hidden), manager.widgets());
    }

    @Test
    void nestedWidgetsDrawParentFirstAndHitTestTheChildFirst() {
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
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
        assertTrue(child.events.contains("move:350,350"));

        manager.handle(new PointerReleased(400, 400, MouseButton.LEFT, Modifiers.NONE));
        assertFalse(child.isPressed());
        assertTrue(child.events.contains("release:LEFT:350,350"));
    }

    @Test
    void keyboardEventsGoToTheFocusedChild() {
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
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
    void advancesAnimationsWithTheFrameClock() {
        WidgetManager manager = manager(new TestBackend());
        List<Float> values = new ArrayList<>();
        manager.animator().animate(0, 1, 1, Easing.LINEAR, values::add);

        manager.update(0.25f);
        assertEquals(0.25f, values.get(values.size() - 1), 0.0001f);
        manager.update(0.75f);
        assertEquals(1, values.get(values.size() - 1), 0.0001f);
        assertTrue(manager.animator().isEmpty());
    }

    @Test
    void removingATopLevelWidgetClearsItsInteraction() {
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
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
    void focusLossCancelsACapturedGestureWithoutReplayingItsRelease() {
        List<String> events = new ArrayList<>();
        Widget widget = new Widget() {
            /**
             * Records a release that must not be synthesized for a cancelled capture.
             *
             * @param x local pointer x
             * @param y local pointer y
             * @param button released button
             * @param modifiers keyboard modifiers
             * @return true to consume the release
             */
            @Override
            protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
                events.add("release:" + button);
                return true;
            }

            /**
             * Records a drag end that must not be synthesized for a cancelled capture.
             *
             * @param x local pointer x
             * @param y local pointer y
             * @param button released button
             * @param modifiers keyboard modifiers
             * @return true to consume the drag end
             */
            @Override
            protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
                events.add("drag-end:" + button);
                return true;
            }

            /**
             * Records a click that must not be synthesized for a cancelled capture.
             *
             * @param x local pointer x
             * @param y local pointer y
             * @param button clicked button
             * @param modifiers keyboard modifiers
             * @return true to consume the click
             */
            @Override
            protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
                events.add("click:" + button);
                return true;
            }

            /**
             * Records the cancellation callback.
             *
             * @param button button whose capture was cancelled
             */
            @Override
            protected void onPointerCaptureCancelled(MouseButton button) {
                events.add("cancel:" + button);
            }
        };
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(40, 10));
        events.clear();

        manager.handle(new FocusChanged(false));

        assertEquals(List.of("cancel:LEFT"), events);
        assertFalse(widget.isPressed());
    }

    @Test
    void addRejectsParentedAndDuplicateWidgets() {
        TestBackend backend = new TestBackend();
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
        TestBackend backend = new TestBackend();
        Probe root = new Probe("root", new ArrayList<>());
        Probe foreign = new Probe("foreign", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        root.setFocusable(true);
        foreign.setFocusable(true);
        WidgetManager manager = manager(backend, root);

        assertThrows(IllegalArgumentException.class, () -> manager.focus(foreign));
    }

    @Test
    void attachedWidgetCannotBeSharedWithAnotherManagerOrParent() {
        Probe root = new Probe("root", new ArrayList<>());
        Probe parent = new Probe("parent", new ArrayList<>());
        WidgetManager first = manager(new TestBackend(), root);
        WidgetManager second = manager(new TestBackend(), parent);
        Widget detachedParent = new Widget() { };

        assertThrows(IllegalArgumentException.class, () -> second.add(root));
        assertThrows(IllegalArgumentException.class, () -> parent.add(root));
        assertThrows(IllegalArgumentException.class, () -> detachedParent.add(root));

        assertEquals(List.of(root), first.widgets());
        assertEquals(List.of(parent), second.widgets());
        assertSame(first.context(), root.context());
        assertNull(root.parent());
        assertTrue(parent.children().isEmpty());
        assertTrue(detachedParent.children().isEmpty());

        first.remove(root);
        parent.add(root);
        assertSame(second.context(), root.context());
        assertSame(parent, root.parent());
    }

    @Test
    void failedChildAttachmentRollsBackTreeAndFocus() {
        Widget root = new Widget() { };
        WidgetManager manager = manager(new TestBackend(), root);
        IllegalStateException failure = new IllegalStateException("Attachment failed.");
        Widget child = new Widget() {
            /**
             * Acquires focus before simulating an initialization failure.
             *
             * @throws IllegalStateException always
             */
            @Override
            protected void onAttached() {
                requestFocus();
                throw failure;
            }
        };
        child.setFocusable(true);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> root.add(child)));

        assertTrue(root.children().isEmpty());
        assertNull(child.parent());
        assertNull(child.context());
        assertNull(manager.focused());
        assertFalse(child.isFocused());
    }

    @Test
    void failedRootAttachmentPreservesWidgetsAddedByTheCallback() {
        WidgetManager manager = manager(new TestBackend());
        Widget sibling = new Widget() { };
        IllegalStateException failure = new IllegalStateException("Attachment failed.");
        Widget root = new Widget() {
            /**
             * Registers another root before simulating an initialization failure.
             *
             * @throws IllegalStateException always
             */
            @Override
            protected void onAttached() {
                manager.add(sibling);
                throw failure;
            }
        };

        assertSame(failure, assertThrows(IllegalStateException.class, () -> manager.add(root)));

        assertEquals(List.of(sibling), manager.widgets());
        assertSame(manager.context(), sibling.context());
        assertNull(root.context());
    }

    @Test
    void removingAChildImmediatelyClearsItsInteraction() {
        Probe root = new Probe("root", new ArrayList<>());
        Probe child = new Probe("child", new ArrayList<>());
        root.setBounds(ROOT_BOUNDS);
        child.setBounds(CHILD_BOUNDS);
        child.setFocusable(true);
        root.add(child);
        WidgetManager manager = manager(new TestBackend(), root);
        manager.handle(new PointerPressed(60, 60, MouseButton.LEFT, Modifiers.NONE));

        root.remove(child);

        assertNull(manager.hovered());
        assertNull(manager.focused());
        assertFalse(child.isHovered());
        assertFalse(child.isFocused());
        assertFalse(child.isPressed());
        assertNull(child.context());
        assertNull(child.parent());
    }

    @Test
    void removingAllChildrenImmediatelyClearsDescendantInteraction() {
        Widget root = new Widget() { };
        Widget branch = new Widget() { };
        Probe child = new Probe("child", new ArrayList<>());
        child.setFocusable(true);
        root.add(branch);
        branch.add(child);
        WidgetManager manager = manager(new TestBackend(), root);
        manager.focus(child);
        manager.capturePointer(child, MouseButton.LEFT);

        root.removeAll();

        assertNull(manager.focused());
        assertFalse(child.isFocused());
        assertFalse(child.isPressed());
        assertNull(child.context());
        assertNull(branch.parent());
        assertTrue(root.children().isEmpty());
    }

    @Test
    void failedChildDetachmentStillUnlinksItAndClearsInteraction() {
        Widget root = new Widget() { };
        IllegalStateException failure = new IllegalStateException("Detachment failed.");
        Widget child = new Widget() {
            /**
             * Simulates a failing cleanup callback.
             *
             * @throws IllegalStateException always
             */
            @Override
            protected void onDetached() {
                throw failure;
            }
        };
        child.setFocusable(true);
        root.add(child);
        WidgetManager manager = manager(new TestBackend(), root);
        manager.focus(child);
        manager.capturePointer(child, MouseButton.LEFT);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> root.remove(child)));

        assertTrue(root.children().isEmpty());
        assertNull(child.parent());
        assertNull(child.context());
        assertNull(manager.focused());
        assertFalse(child.isPressed());
    }

    @Test
    void focusTraversalAndCaptureReleaseUseWidgetIdentity() {
        Widget first = new EqualProbe();
        Widget second = new EqualProbe();
        Widget third = new EqualProbe();
        first.setFocusable(true);
        second.setFocusable(true);
        third.setFocusable(true);
        WidgetManager manager = manager(new TestBackend(), first, second, third);
        manager.focus(second);

        manager.focusNext(true);
        assertSame(third, manager.focused());
        manager.focusNext(false);
        assertSame(second, manager.focused());

        manager.capturePointer(first, MouseButton.LEFT);
        manager.capturePointer(second, MouseButton.RIGHT);
        manager.releasePointerCapture(MouseButton.LEFT);

        assertFalse(first.isPressed());
        assertTrue(second.isPressed());
    }

    @Test
    void delegatesAntialiasingSettings() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = manager(backend, new Probe("root", new ArrayList<>()));

        manager.setAntialiasing(Antialiasing.HIGH);

        assertEquals(Antialiasing.HIGH, manager.antialiasing());
    }

    @Test
    void rejectsNullArgumentsInvalidDeltaAndRenderBeforeResize() {
        TestBackend backend = new TestBackend();
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

    private static WidgetManager manager(TestBackend backend, Widget... widgets) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        for (Widget widget : widgets) {
            manager.add(widget);
        }
        return manager;
    }

    @Test
    void recapturingTheSameButtonPreservesClickDetection() {
        List<String> log = new ArrayList<>();
        GestureProbe widget = new GestureProbe(log);
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.capturePointer(widget, MouseButton.LEFT);
        manager.handle(new PointerReleased(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(log.contains("click:LEFT:1"));
        assertFalse(widget.isPressed());
    }

    @Test
    void distantReleaseWithoutMoveDoesNotGenerateAClick() {
        List<String> log = new ArrayList<>();
        GestureProbe widget = new GestureProbe(log);
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(100, 100, MouseButton.LEFT, Modifiers.NONE));

        assertFalse(log.contains("click:LEFT:1"));
        assertFalse(widget.isPressed());
    }

    @Test
    void draggingBetweenClicksDoesNotProduceADoubleClick() {
        List<String> log = new ArrayList<>();
        GestureProbe widget = new GestureProbe(log);
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        click(manager, 10, 10);
        log.clear();

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(30, 10));
        manager.handle(new PointerReleased(30, 10, MouseButton.LEFT, Modifiers.NONE));
        log.clear();
        click(manager, 10, 10);

        assertTrue(log.contains("click:LEFT:1"));
        assertFalse(log.contains("double:LEFT"));
    }

    @Test
    void releasingBeyondTheClickSlopBreaksTheClickSequence() {
        List<String> log = new ArrayList<>();
        GestureProbe widget = new GestureProbe(log);
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        click(manager, 10, 10);
        log.clear();

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(100, 100, MouseButton.LEFT, Modifiers.NONE));
        log.clear();
        click(manager, 10, 10);

        assertTrue(log.contains("click:LEFT:1"));
        assertFalse(log.contains("double:LEFT"));
    }

    @Test
    void clickingEmptySpaceBreaksTheClickSequence() {
        List<String> log = new ArrayList<>();
        GestureProbe widget = new GestureProbe(log);
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        click(manager, 10, 10);
        log.clear();

        click(manager, 200, 200);
        click(manager, 10, 10);

        assertTrue(log.contains("click:LEFT:1"));
        assertFalse(log.contains("double:LEFT"));
    }

    @Test
    void releasingCaptureBreaksTheClickSequence() {
        List<String> log = new ArrayList<>();
        GestureProbe widget = new GestureProbe(log);
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        click(manager, 10, 10);
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.releasePointerCapture(MouseButton.LEFT);
        log.clear();

        click(manager, 10, 10);

        assertTrue(log.contains("click:LEFT:1"));
        assertFalse(log.contains("double:LEFT"));
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    @Test
    void widgetsCannotReleaseAnotherWidgetsCapture() {
        Widget first = new Widget() { };
        Widget second = new Widget() { };
        WidgetManager manager = manager(new TestBackend(), first, second);
        manager.capturePointer(second, MouseButton.LEFT);

        first.releasePointerCapture(MouseButton.LEFT);
        assertTrue(second.isPressed());
        second.releasePointerCapture(MouseButton.LEFT);
        assertFalse(second.isPressed());
    }

    @Test
    void focusRedirectedByABlurCallbackHasOnlyOneOwner() {
        WidgetManager manager = manager(new TestBackend());
        Widget requested = new Widget() { };
        Widget redirected = new Widget() { };
        Widget first = new Widget() {
            /**
             * Redirects focus when this widget loses it.
             *
             * @param focused whether this widget gained focus
             */
            @Override
            protected void onFocusChanged(boolean focused) {
                if (!focused) {
                    manager.focus(redirected);
                }
            }
        };
        first.setFocusable(true);
        requested.setFocusable(true);
        redirected.setFocusable(true);
        manager.add(first).add(requested).add(redirected);
        manager.focus(first);

        manager.focus(requested);

        assertSame(redirected, manager.focused());
        assertTrue(redirected.isFocused());
        assertFalse(first.isFocused());
        assertFalse(requested.isFocused());
    }

    @Test
    void removalDuringPointerEnterDoesNotCaptureTheDetachedWidget() {
        WidgetManager manager = manager(new TestBackend());
        Widget widget = new Widget() {
            /**
             * Removes this widget while hover is being assigned.
             */
            @Override
            protected void onPointerEnter() {
                manager.remove(this);
            }
        };
        widget.setBounds(ROOT_BOUNDS);
        manager.add(widget);

        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertNull(manager.hovered());
        assertNull(manager.focused());
        assertFalse(widget.isHovered());
        assertFalse(widget.isPressed());
    }

    @Test
    void releaseCallbackFailureStillClearsPressedState() {
        IllegalStateException failure = new IllegalStateException("Release failed.");
        Widget widget = new Widget() {
            /**
             * Simulates an input callback failure.
             *
             * @param x local pointer x
             * @param y local pointer y
             * @param button released button
             * @param modifiers keyboard modifiers
             * @return never returns normally
             * @throws IllegalStateException always
             */
            @Override
            protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
                throw failure;
            }
        };
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> manager.handle(new PointerReleased(10, 10, MouseButton.LEFT, Modifiers.NONE))));
        assertFalse(widget.isPressed());
    }

    @Test
    void cancellingCaptureDuringDragStartStopsFurtherDragEvents() {
        int[] drags = {0};
        Widget widget = new Widget() {
            /**
             * Cancels the drag as soon as it starts.
             *
             * @param x local pointer x
             * @param y local pointer y
             * @param button dragged button
             * @param modifiers keyboard modifiers
             * @return true to consume the start event
             */
            @Override
            protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
                releasePointerCapture(button);
                return true;
            }

            /**
             * Counts drag events that should have been cancelled.
             *
             * @param x local pointer x
             * @param y local pointer y
             * @param button dragged button
             * @param modifiers keyboard modifiers
             * @return true to consume the event
             */
            @Override
            protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
                drags[0]++;
                return true;
            }
        };
        widget.setBounds(ROOT_BOUNDS);
        WidgetManager manager = manager(new TestBackend(), widget);
        manager.handle(new PointerPressed(10, 10, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(30, 10));
        manager.handle(new PointerMoved(40, 10));

        assertEquals(0, drags[0]);
        assertFalse(widget.isPressed());
    }

    @Test
    void unfocusedWindowDoesNotDispatchKeyboardOrTextEvents() {
        Probe widget = new Probe("keyboard", new ArrayList<>());
        widget.setFocusable(true);
        WidgetManager manager = manager(new TestBackend(), widget);
        manager.focus(widget);
        widget.events.clear();
        manager.handle(new FocusChanged(false));
        manager.handle(new KeyPressed(Key.A, Modifiers.NONE, true));
        manager.handle(new CharTyped(0x1F600));

        assertTrue(widget.events.isEmpty());
        assertSame(widget, manager.focused());

        manager.handle(new FocusChanged(true));
        manager.handle(new CharTyped(0x1F600));
        assertEquals(List.of("char:128512"), widget.events);
    }

    @Test
    void keyboardBubblingStopsWhenItsSubtreeIsRemoved() {
        WidgetManager manager = manager(new TestBackend());
        Probe root = new Probe("root", new ArrayList<>());
        Widget child = new Widget() {
            /**
             * Removes the receiving subtree during keyboard dispatch.
             *
             * @param key pressed key
             * @param modifiers keyboard modifiers
             * @param repeat whether this is key repeat
             * @return false to exercise bubbling after removal
             */
            @Override
            protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
                manager.remove(root);
                return false;
            }
        };
        child.setFocusable(true);
        root.add(child);
        manager.add(root);
        manager.focus(child);

        manager.handle(new KeyPressed(Key.A, Modifiers.NONE, false));

        assertTrue(root.events.isEmpty());
        assertNull(manager.focused());
        assertNull(child.context());
    }

    private static final class EqualProbe extends Widget {
        /**
         * Treats all instances of this probe as equal to exercise identity-based ownership.
         *
         * @param other object to compare
         * @return true for another equal probe
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualProbe;
        }

        /**
         * Returns the shared hash code required by equality.
         *
         * @return the same hash code for every instance
         */
        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class Probe extends Widget {
        private final String name;
        private final List<String> log;
        private final List<String> events = new ArrayList<>();
        private FrameInfo lastUpdateFrame;
        private FrameInfo lastDrawFrame;
        private int layoutCount;

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
            renderer.rect(localBounds(), Color.WHITE);
        }

        /**
         * Counts layout passes performed by the manager.
         */
        @Override
        protected void onLayout() {
            layoutCount++;
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
         * @param x horizontal pointer position relative to the widget's top-left corner
         * @param y vertical pointer position relative to the widget's top-left corner
         */
        @Override
        protected boolean onPointerMove(float x, float y) {
            events.add("move:" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records a pointer press.
         *
         * @param x horizontal pointer position relative to the widget's top-left corner
         * @param y vertical pointer position relative to the widget's top-left corner
         * @param button pressed button
         * @param modifiers active keyboard modifiers
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            events.add("press:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records a pointer release.
         *
         * @param x horizontal pointer position relative to the widget's top-left corner
         * @param y vertical pointer position relative to the widget's top-left corner
         * @param button released button
         * @param modifiers active keyboard modifiers
         */
        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
            events.add("release:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records a key press.
         *
         * @param key pressed key
         * @param modifiers active keyboard modifiers
         * @param repeat true for a repeated key press
         */
        @Override
        protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
            events.add("key:" + key + ":" + repeat);
            return false;
        }

        /**
         * Records a key release.
         *
         * @param key released key
         * @param modifiers active keyboard modifiers
         */
        @Override
        protected boolean onKeyReleased(Key key, Modifiers modifiers) {
            events.add("key-up:" + key);
            return false;
        }

        /**
         * Records a typed code point.
         *
         * @param codepoint typed code point
         */
        @Override
        protected boolean onCharTyped(int codepoint) {
            events.add("char:" + codepoint);
            return false;
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

    private static final class BubbleProbe extends Widget {
        private final String name;
        private final List<String> log;
        private boolean consumePress;
        private boolean consumeClick;

        BubbleProbe(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        /**
         * Records a press and returns the configured consumption flag.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press is consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("press:" + name);
            return consumePress;
        }

        /**
         * Records a release.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button released button
         * @param modifiers active modifiers
         * @return false to continue bubbling
         */
        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("release:" + name);
            return false;
        }

        /**
         * Records a click and returns the configured consumption flag.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the click is consumed
         */
        @Override
        protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("click:" + name);
            return consumeClick;
        }
    }

    private static final class GestureProbe extends Widget {
        private final List<String> log;

        GestureProbe(List<String> log) {
            this.log = log;
        }

        /**
         * Records a pointer press.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers active modifiers
         * @return false to continue bubbling
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("press:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records pointer movement.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @return false to continue bubbling
         */
        @Override
        protected boolean onPointerMove(float x, float y) {
            log.add("move:" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records a pointer release.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button released button
         * @param modifiers active modifiers
         * @return false to continue bubbling
         */
        @Override
        protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("release:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records a single click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return false to continue bubbling
         */
        @Override
        protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("click:" + button + ":1");
            return false;
        }

        /**
         * Records a double click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return false to continue bubbling
         */
        @Override
        protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("double:" + button);
            return false;
        }

        /**
         * Records the start of a drag.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return false to continue bubbling
         */
        @Override
        protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("drag-start:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records drag movement.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return false to continue bubbling
         */
        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("drag:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records the end of a drag.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button released button
         * @param modifiers modifiers captured at press time
         * @return false to continue bubbling
         */
        @Override
        protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("drag-end:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }

        /**
         * Records a long press.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return false to continue bubbling
         */
        @Override
        protected boolean onLongPress(float x, float y, MouseButton button, Modifiers modifiers) {
            log.add("long:" + button + ":" + Math.round(x) + "," + Math.round(y));
            return false;
        }
    }

    private static final class CursorProbe extends Widget {
        /**
         * Requests the pointing hand cursor for the subtree.
         *
         * @return the pointer cursor
         */
        @Override
        public Cursor cursor() {
            return Cursor.POINTER;
        }
    }

    private static final class RecordingPlatform implements UiPlatform {
        private Cursor cursor = Cursor.DEFAULT;

        @Override
        public String clipboardText() {
            return "";
        }

        @Override
        public void setClipboardText(String text) {
        }

        @Override
        public void setCursor(Cursor cursor) {
            this.cursor = cursor;
        }
    }

    private static final class LifecycleProbe extends Widget {
        private final String name;
        private final List<String> log;
        private UiContext attachedContext;
        private UiContext detachedContext;

        LifecycleProbe(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        /**
         * Records attachment and captures the assigned context.
         */
        @Override
        protected void onAttached() {
            attachedContext = context();
            log.add("attach:" + name);
        }

        /**
         * Records detachment and captures the still-available context.
         */
        @Override
        protected void onDetached() {
            detachedContext = context();
            log.add("detach:" + name);
        }

        /**
         * Records context updates.
         */
        @Override
        protected void onContextChanged() {
            log.add("context:" + name);
        }
    }

}
