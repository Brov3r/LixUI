package com.lixui.widget.container;

import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.control.Button;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.input.event.PointerScrolled;
import com.lixui.platform.Cursor;
import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
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

class WindowTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);
    private static final Rect WINDOW_BOUNDS = new Rect(10, 10, 300, 240);

    @Test
    void sharesIconsAtDisplayResolutionAndReleasesTheLastReference() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Window first = createWindow(manager);
            Window second = createWindow(manager);
            List<Button> firstButtons = new ArrayList<>();
            List<Button> secondButtons = new ArrayList<>();
            collectButtons(first, firstButtons);
            collectButtons(second, secondButtons);

            assertEquals(6, backend.created.size());
            assertEquals(28, firstButtons.getFirst().icon().width());
            assertSame(firstButtons.getFirst().icon(), secondButtons.getFirst().icon());

            manager.remove(first);
            assertTrue(backend.destroyed.isEmpty());
            assertNull(firstButtons.getFirst().icon());

            manager.resize(new Viewport(800, 600, 800, 600));
            assertEquals(14, secondButtons.getFirst().icon().width());
            assertEquals(12, backend.created.size());
            assertEquals(6, backend.destroyed.size());

            manager.remove(second);
            assertEquals(12, backend.destroyed.size());
        }
        assertEquals(12, backend.destroyed.size());
    }

    @Test
    void rollsBackPartiallyLoadedIconsWhenUploadFails() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            backend.failAfter = 2;
            Window window = new Window();
            assertThrows(IllegalStateException.class, () -> manager.add(window));
            assertNull(window.context());
            assertTrue(manager.widgets().isEmpty());
            assertEquals(2, backend.destroyed.size());

            backend.failAfter = Integer.MAX_VALUE;
            manager.add(window);
            assertEquals(8, backend.created.size());
        }
        assertEquals(8, backend.destroyed.size());
    }

    @Test
    void preservesExpandedHeightAcrossInterruptedTransitions() {
        try (WidgetManager manager = manager()) {
            Window window = createWindow(manager);
            window.setCollapsed(true);
            manager.update(0.3f);
            window.setCollapsed(false);
            manager.update(0.1f);
            window.setCollapsed(true);
            manager.update(0.3f);
            window.setCollapsed(false);
            manager.update(0.3f);

            assertEquals(240, window.bounds().height(), 0.001f);
            window.setCollapsed(true);
            manager.update(0.1f);
            window.setCollapsed(true);
            manager.update(0.11f);
            assertEquals(window.headerHeight(), window.bounds().height());
        }
    }

    @Test
    void detachingCancelsBothHeightAndScrollAnimations() {
        try (WidgetManager manager = manager()) {
            Window window = createWindow(manager);
            manager.handle(new PointerScrolled(100, 100, 0, -1));
            window.setCollapsed(true);
            assertEquals(2, manager.animator().size());

            manager.remove(window);

            assertTrue(manager.animator().isEmpty());
            assertEquals(window.headerHeight(), window.bounds().height());
        }
    }

    @Test
    void changingHeaderHeightDuringCollapseKeepsTheNewHeight() {
        try (WidgetManager manager = manager()) {
            Window window = createWindow(manager);
            window.setCollapsed(true);
            manager.update(0.1f);
            window.setHeaderHeight(50);
            manager.update(0.3f);

            assertEquals(50, window.bounds().height());
        }
    }

    @Test
    void hiddenActionsDoNotLeaveGapsInTheHeader() {
        try (WidgetManager manager = manager()) {
            Window window = createWindow(manager);
            window.setClosable(false);
            window.setCollapsible(false);
            manager.layout();
            List<Button> buttons = new ArrayList<>();
            collectButtons(window, buttons);
            Button pin = buttons.stream().filter(Button::isVisible).findFirst().orElseThrow();

            assertEquals(264, pin.bounds().x());
            click(manager, 287, 25);
            assertTrue(window.isPinned());
        }
    }

    @Test
    void autoSizeWrapsTheBodyAndReportsTheHeaderWhenCollapsed() {
        try (WidgetManager manager = manager()) {
            Window window = new Window();
            Widget content = new Widget() { };
            content.setBounds(new Rect(0, 0, 200, 100));
            window.setContent(content);
            window.setAutoSize(true);
            manager.add(window);

            manager.layout();

            assertEquals(200, window.bounds().width(), 0.001f);
            assertEquals(window.headerHeight() + 100, window.bounds().height(), 0.001f);

            window.setCollapsed(true);
            manager.layout();
            assertEquals(window.headerHeight(), window.bounds().height(), 0.001f);
        }
    }

    @Test
    void movesTheWindowFromTheHeader() {
        WidgetManager manager = manager();
        Window window = createWindow(manager);

        drag(manager, 20, 20, 80, 60);

        assertEquals(70, window.bounds().x(), 0.001f);
        assertEquals(50, window.bounds().y(), 0.001f);
    }

    @Test
    void pinsCollapsesAndRestoresTheWindow() {
        WidgetManager manager = manager();
        Window window = createWindow(manager);

        click(manager, 255, 25);
        manager.update(0.3f);
        assertTrue(window.isCollapsed());
        assertEquals(34, window.bounds().height(), 0.001f);

        click(manager, 255, 25);
        manager.update(0.3f);
        assertFalse(window.isCollapsed());
        assertEquals(240, window.bounds().height(), 0.001f);

        click(manager, 235, 25);
        manager.update(0.3f);
        assertTrue(window.isPinned());
        assertFalse(window.isCollapsed());
        assertEquals(240, window.bounds().height(), 0.001f);

        manager.handle(new PointerMoved(700, 500));
        manager.update(0.01f);
        manager.update(0.3f);
        assertTrue(window.isCollapsed());
        assertEquals(34, window.bounds().height(), 0.001f);

        manager.handle(new PointerMoved(235, 25));
        manager.update(0.01f);
        manager.update(0.3f);
        assertFalse(window.isCollapsed());
        assertEquals(240, window.bounds().height(), 0.001f);

        click(manager, 235, 25);
        manager.update(0.3f);
        assertFalse(window.isPinned());
        assertFalse(window.isCollapsed());
        assertEquals(240, window.bounds().height(), 0.001f);
    }

    @Test
    void resizesFromTheBottomRightHandle() {
        WidgetManager manager = manager();
        Window window = createWindow(manager);

        drag(manager, 300, 240, 330, 270);

        assertEquals(330, window.bounds().width(), 0.001f);
        assertEquals(270, window.bounds().height(), 0.001f);
    }

    @Test
    void honorsMovementResizeAndActionSettings() {
        WidgetManager manager = manager();
        Window window = createWindow(manager);

        window.setMovable(false);
        drag(manager, 20, 20, 80, 60);
        assertEquals(10, window.bounds().x(), 0.001f);
        assertEquals(10, window.bounds().y(), 0.001f);

        window.setResizable(false);
        manager.render();
        assertFalse(resizeHandle(window).isVisible());

        window.setCollapsible(false);
        window.setPinnable(false);
        window.setClosable(false);
        manager.render();
        assertEquals(0, visibleButtons(window));
    }

    @Test
    void resizesOnlyEnabledAxesWithinLimitsAndUsesAxisCursor() {
        WidgetManager manager = manager();
        Window window = createWindow(manager);
        assertEquals(Cursor.RESIZE_DIAGONAL, resizeHandle(window).cursor());

        window.setMaximumSize(320, 260);
        drag(manager, 300, 240, 360, 300);
        assertEquals(320, window.bounds().width(), 0.001f);
        assertEquals(260, window.bounds().height(), 0.001f);

        window.setMaximumSize(400, 400);
        manager.render();
        window.setHorizontalResizable(false);
        manager.render();
        assertEquals(Cursor.RESIZE_VERTICAL, resizeHandle(window).cursor());
        drag(manager, 320, 260, 360, 300);
        assertEquals(320, window.bounds().width(), 0.001f);
        assertEquals(300, window.bounds().height(), 0.001f);

        window.setHorizontalResizable(true);
        window.setVerticalResizable(false);
        manager.render();
        assertEquals(Cursor.RESIZE_HORIZONTAL, resizeHandle(window).cursor());
        drag(manager, 320, 300, 360, 340);
        assertEquals(360, window.bounds().width(), 0.001f);
        assertEquals(300, window.bounds().height(), 0.001f);
    }

    @Test
    void appliesContentPaddingAndClosesThroughTheCallback() {
        WidgetManager manager = manager();
        Window window = createWindow(manager);
        boolean[] closed = {false};
        window.setOnClose(() -> closed[0] = true);

        window.setContentPadding(7);
        manager.render();
        assertEquals(41, window.contentInsetTop(), 0.001f);
        assertEquals(7, window.contentInsetLeft(), 0.001f);

        click(manager, 287, 25);
        assertFalse(window.isVisible());
        assertTrue(closed[0]);
    }

    private static Window createWindow(WidgetManager manager) {
        Window window = new Window();
        window.setBounds(WINDOW_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 400));
        window.setContent(content);
        manager.add(window);
        manager.render();
        return window;
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        manager.resize(VIEWPORT);
        return manager;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static void drag(WidgetManager manager, float x, float y, float endX, float endY) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(endX, endY));
        manager.handle(new PointerReleased(endX, endY, MouseButton.LEFT, Modifiers.NONE));
    }

    private static Widget resizeHandle(Window window) {
        return window.children().get(window.children().size() - 1);
    }

    private static int visibleButtons(Widget root) {
        List<Button> buttons = new ArrayList<>();
        collectButtons(root, buttons);
        int visible = 0;
        for (Button button : buttons) {
            if (button.isVisible()) {
                visible++;
            }
        }
        return visible;
    }

    private static void collectButtons(Widget root, List<Button> buttons) {
        for (Widget child : root.children()) {
            if (child instanceof Button button) {
                buttons.add(button);
            }
            collectButtons(child, buttons);
        }
    }

    private static final class RecordingBackend implements RenderBackend {
        private DrawList lastDrawList;
        private final List<Texture> created = new ArrayList<>();
        private final List<Texture> destroyed = new ArrayList<>();
        private int failAfter = Integer.MAX_VALUE;

        @Override
        public void resize(Viewport viewport) {
        }

        @Override
        public void draw(DrawList drawList) {
            lastDrawList = drawList;
        }

        @Override
        public Texture createTexture(ImageData image) {
            if (created.size() >= failAfter) {
                throw new IllegalStateException("Texture upload failed.");
            }
            Texture texture = new TestTexture(image.width(), image.height());
            created.add(texture);
            return texture;
        }

        @Override
        public void destroyTexture(Texture texture) {
            destroyed.add(texture);
        }

        @Override
        public void close() {
        }
    }

    private record TestTexture(int width, int height) implements Texture {
        /**
         * Creates a lightweight texture handle for widget tests.
         *
         * @param width texture width
         * @param height texture height
         */
        private TestTexture {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Texture dimensions must be positive.");
            }
        }
    }
}
