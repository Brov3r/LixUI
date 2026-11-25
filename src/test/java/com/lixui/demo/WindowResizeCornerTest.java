package com.lixui.demo;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.render.Viewport;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.container.ScrollBar;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.container.Window;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowResizeCornerTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;
    private static final float WINDOW_WIDTH = 620;
    private static final float WINDOW_HEIGHT = 560;
    private static final float RESIZE_SIZE = 18;

    @Test
    void nestedPageScrollbarsStayClearOfTheResizeHandle() {
        try (WidgetManager manager = manager()) {
            Window window = createWindow(manager);
            TabPanel tabs = (TabPanel) window.content();
            DemoScrollPage page = (DemoScrollPage) tabs.selected().content();

            fill(manager, window, tabs);
            assertEquals(RESIZE_SIZE, window.contentInsetRight(), 0.001f);
            assertEquals(RESIZE_SIZE, window.contentInsetBottom(), 0.001f);

            ScrollBar vertical = bar(page, ScrollBar.Orientation.VERTICAL);
            assertTrue(vertical.isVisible());
            Rect bounds = absoluteBounds(vertical);
            assertClearOfResizeCorner(window, bounds);

            Point center = bounds.center();
            assertSame(vertical, window.hitTest(center.x() - window.bounds().x(),
                    center.y() - window.bounds().y()));
        }
    }

    @Test
    void windowScrollbarStaysClearOfTheResizeHandle() {
        try (WidgetManager manager = manager()) {
            Window window = new Window();
            window.setBounds(new Rect(16, 16, 300, 300));
            Widget content = new Widget() { };
            content.setBounds(new Rect(0, 0, 400, 600));
            window.setContent(content);
            manager.add(window);
            manager.render();

            ScrollBar vertical = bar(window, ScrollBar.Orientation.VERTICAL);
            assertTrue(vertical.isVisible());
            assertClearOfResizeCorner(window, absoluteBounds(vertical));
        }
    }

    @Test
    void maximizeRemovesTheReservedCornerAndRestoreReturnsIt() {
        try (WidgetManager manager = manager()) {
            Window window = createWindow(manager);
            TabPanel tabs = (TabPanel) window.content();
            DemoScrollPage page = (DemoScrollPage) tabs.selected().content();
            fill(manager, window, tabs);

            window.setMaximized(true);
            manager.update(0.3f);
            fill(manager, window, tabs);
            assertEquals(0, window.contentInsetRight(), 0.001f);
            assertEquals(0, window.contentInsetBottom(), 0.001f);
            assertFalse(resizeHandle(window).isVisible());
            assertInsideWindow(window, absoluteBounds(bar(page, ScrollBar.Orientation.VERTICAL)));

            window.setMaximized(false);
            manager.update(0.3f);
            fill(manager, window, tabs);
            assertEquals(RESIZE_SIZE, window.contentInsetRight(), 0.001f);
            assertEquals(RESIZE_SIZE, window.contentInsetBottom(), 0.001f);
            assertTrue(resizeHandle(window).isVisible());
            assertClearOfResizeCorner(window, absoluteBounds(bar(page, ScrollBar.Orientation.VERTICAL)));
        }
    }

    @Test
    void reservesTheCornerOnlyWhileTheHandleIsShown() {
        try (WidgetManager manager = manager()) {
            Window window = createWindow(manager);

            window.setResizable(false);
            fill(manager, window, (TabPanel) window.content());
            assertEquals(0, window.contentInsetRight(), 0.001f);
            assertEquals(0, window.contentInsetBottom(), 0.001f);
            assertFalse(resizeHandle(window).isVisible());

            window.setResizable(true);
            window.setCollapsed(true);
            manager.update(0.3f);
            fill(manager, window, (TabPanel) window.content());
            assertEquals(0, window.contentInsetRight(), 0.001f);
            assertEquals(0, window.contentInsetBottom(), 0.001f);
            assertFalse(resizeHandle(window).isVisible());

            window.setCollapsed(false);
            manager.update(0.3f);
            fill(manager, window, (TabPanel) window.content());
            assertEquals(RESIZE_SIZE, window.contentInsetRight(), 0.001f);
            assertEquals(RESIZE_SIZE, window.contentInsetBottom(), 0.001f);
            assertTrue(resizeHandle(window).isVisible());
        }
    }

    private static Window createWindow(WidgetManager manager) {
        Window window = new Window();
        window.setBounds(new Rect(16, 16, WINDOW_WIDTH, WINDOW_HEIGHT));
        TabPanel tabs = new TabPanel();
        VBox content = new VBox();
        content.setPadding(10);
        content.setGap(10);
        content.add(new FixedWidget(100, 300));
        content.add(new FixedWidget(100, 300));
        tabs.addTab("Page", new DemoScrollPage(content));
        window.setContent(tabs);
        manager.add(window);
        return window;
    }

    private static void fill(WidgetManager manager, Window window, TabPanel tabs) {
        manager.render();
        tabs.setSize(
                Math.max(0, window.bounds().width() - window.contentInsetRight()),
                Math.max(0, window.bounds().height()
                        - window.contentInsetTop() - window.contentInsetBottom()));
        manager.render();
    }

    private static void assertClearOfResizeCorner(Window window, Rect rect) {
        Rect handle = absoluteBounds(resizeHandle(window));
        assertTrue(rect.intersect(handle).isEmpty(), "Scrollbar overlaps the resize handle.");
        assertTrue(rect.right() <= window.bounds().right() - RESIZE_SIZE + 0.001f,
                "Scrollbar crosses the reserved right corner.");
        assertTrue(rect.bottom() <= window.bounds().bottom() - RESIZE_SIZE + 0.001f,
                "Scrollbar crosses the reserved bottom corner.");
        assertInsideWindow(window, rect);
    }

    private static void assertInsideWindow(Window window, Rect rect) {
        assertTrue(rect.x() >= window.bounds().x(), "Scrollbar leaves the window on the left.");
        assertTrue(rect.y() >= window.bounds().y(), "Scrollbar leaves the window at the top.");
        assertTrue(rect.right() <= window.bounds().right() + 0.001f,
                "Scrollbar leaves the window on the right.");
        assertTrue(rect.bottom() <= window.bounds().bottom() + 0.001f,
                "Scrollbar leaves the window at the bottom.");
    }

    private static Rect absoluteBounds(Widget widget) {
        Point position = widget.absolutePosition();
        return new Rect(position.x(), position.y(), widget.bounds().width(), widget.bounds().height());
    }

    private static Widget resizeHandle(Window window) {
        return window.children().get(window.children().size() - 1);
    }

    private static ScrollBar bar(Widget root, ScrollBar.Orientation orientation) {
        for (Widget child : root.children()) {
            if (child instanceof ScrollBar bar && bar.orientation() == orientation) {
                return bar;
            }
        }
        throw new AssertionError("Missing scrollbar: " + orientation);
    }

    private static WidgetManager manager() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        return manager;
    }
}
