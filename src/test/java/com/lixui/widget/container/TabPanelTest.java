package com.lixui.widget.container;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.FrameInfo;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.FocusChanged;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.input.event.PointerScrolled;
import com.lixui.render.ImageData;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.render.command.ImageCommand;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.testsupport.FixedWidget;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.tooltip.TextTooltip;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabPanelTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void selectsTabsAndShowsOnlyTheActivePage() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = panel();
            FixedWidget firstContent = new FixedWidget(100, 100);
            FixedWidget secondContent = new FixedWidget(100, 100);
            TabPanel.Tab first = panel.addTab("First", firstContent);
            TabPanel.Tab second = panel.addTab("Second", secondContent);
            List<TabPanel.Tab> changes = new ArrayList<>();
            panel.setOnChange(changes::add);
            manager.add(panel);
            manager.render();

            assertSame(first, panel.selected());
            assertTrue(firstContent.isVisible());
            assertFalse(secondContent.isVisible());
            assertEquals(new Rect(0, 30, 400, 270), firstContent.bounds());

            click(manager, centerX(panel, second), 15);
            assertSame(second, panel.selected());
            assertFalse(firstContent.isVisible());
            assertTrue(secondContent.isVisible());
            assertEquals(List.of(second), changes);

            click(manager, centerX(panel, first), 15);
            assertSame(first, panel.selected());
            assertEquals(List.of(second, first), changes);
        }
    }

    @Test
    void lockedTabCannotBeOpenedOrMoved() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = panel();
            TabPanel.Tab first = panel.addTab("First", new FixedWidget(10, 10));
            TabPanel.Tab second = panel.addTab("Second", new FixedWidget(10, 10));
            second.setLocked(true);
            manager.add(panel);
            manager.render();

            float lockedX = centerX(panel, second);
            click(manager, lockedX, 15);
            assertSame(first, panel.selected());
            assertFalse(panel.select(second));

            drag(manager, lockedX, 15, lockedX + 60, 15);
            assertEquals(List.of(first, second), panel.tabs());
        }
    }

    @Test
    void closesTabsWithTheCrossAndHonorsTheCloseRequest() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = panel();
            panel.setTabsClosable(true);
            TabPanel.Tab first = panel.addTab("First", new FixedWidget(10, 10));
            TabPanel.Tab second = panel.addTab("Second", new FixedWidget(10, 10));
            List<TabPanel.Tab> closed = new ArrayList<>();
            panel.setOnClose(closed::add);
            manager.add(panel);
            manager.render();

            Rect firstClose = panel.tabCloseBounds(first);
            click(manager, firstClose.x() + firstClose.width() / 2,
                    firstClose.y() + firstClose.height() / 2);

            assertEquals(List.of(second), panel.tabs());
            assertEquals(List.of(first), closed);
            assertSame(second, panel.selected());
            assertNull(first.content().parent());

            panel.setCloseRequest(tab -> false);
            Rect secondClose = panel.tabCloseBounds(second);
            click(manager, secondClose.x() + secondClose.width() / 2,
                    secondClose.y() + secondClose.height() / 2);
            assertEquals(List.of(second), panel.tabs());
        }
    }

    @Test
    void dragReordersMovableTabsOnly() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = panel();
            TabPanel.Tab first = panel.addTab("First", new FixedWidget(10, 10));
            TabPanel.Tab second = panel.addTab("Second", new FixedWidget(10, 10));
            TabPanel.Tab third = panel.addTab("Third", new FixedWidget(10, 10));
            manager.add(panel);
            manager.render();

            float firstX = centerX(panel, first);
            drag(manager, firstX, 15, 100, 15);
            assertEquals(List.of(second, first, third), panel.tabs());

            panel.setTabsMovable(false);
            drag(manager, firstX, 15, 150, 15);
            assertEquals(List.of(second, first, third), panel.tabs());
            assertSame(second, panel.selected());
            assertSame(second, panel.tabs().getFirst());
        }
    }

    @Test
    void focusLossStopsDragEdgeScrolling() {
        try (WidgetManager manager = manager(new TestBackend())) {
            TabPanel panel = panel();
            panel.setBounds(new Rect(0, 0, 200, 300));
            for (int index = 0; index < 10; index++) {
                panel.addTab("Tab " + index, new FixedWidget(10, 10));
            }
            manager.add(panel);
            manager.render();

            manager.handle(new PointerPressed(100, 15, MouseButton.LEFT, Modifiers.NONE));
            manager.handle(new PointerMoved(195, 15));
            manager.update(0.1f);
            assertTrue(panel.scrollOffset() > 0);
            manager.handle(new FocusChanged(false));
            float stoppedAt = panel.scrollOffset();

            manager.update(1);

            assertEquals(stoppedAt, panel.scrollOffset(), 0.001f);
        }
    }

    @Test
    void scrollsWithArrowsAndWheelAndRevealsTheSelection() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = panel();
            panel.setBounds(new Rect(0, 0, 200, 300));
            for (int index = 0; index < 10; index++) {
                panel.addTab("Tab " + index, new FixedWidget(10, 10));
            }
            manager.add(panel);
            manager.render();
            assertEquals(0, panel.scrollOffset(), 0.001f);

            click(manager, 191, 15);
            manager.update(0.2f);
            assertTrue(panel.scrollOffset() > 0);

            click(manager, 9, 15);
            manager.update(0.2f);
            assertEquals(0, panel.scrollOffset(), 0.001f);

            manager.handle(new PointerScrolled(50, 15, 0, -1));
            manager.update(0.2f);
            float wheelOffset = panel.scrollOffset();
            assertTrue(wheelOffset > 0);

            panel.select(9);
            manager.update(0.2f);
            assertTrue(panel.scrollOffset() > wheelOffset);
        }
    }

    @Test
    void drawsIconsAndSupportsTheIconSide() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = panel();
            Texture icon = manager.context().createTexture(new ImageData(2, 2, new byte[16]));
            TabPanel.Tab tab = panel.addTab("", icon, new FixedWidget(10, 10));
            panel.addTab("Plain", new FixedWidget(10, 10));
            manager.add(panel);
            manager.render();

            boolean hasImage = backend.lastDrawList().commands().stream()
                    .anyMatch(ImageCommand.class::isInstance);
            assertTrue(hasImage);

            tab.setIconSide(TabPanel.IconSide.RIGHT);
            manager.render();
            assertEquals(TabPanel.IconSide.RIGHT, tab.iconSide());
        }
    }

    @Test
    void showsTheFullTitleInATooltipOnlyWhenItIsTruncated() {
        TabPanel panel = panel();
        panel.setTabMaxWidth(60);
        TabPanel.Tab shortTab = panel.addTab("Hi", null);
        TabPanel.Tab longTab = panel.addTab("A very long tab title", null);

        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            FontData data = SystemFonts.findDefault().orElse(null);
            Assumptions.assumeTrue(data != null, "No readable system font is installed.");
            manager.setDefaultFont(manager.context().createFont(data, 16));
            manager.add(panel);
            manager.render();

            Widget strip = panel.children().getFirst();

            manager.handle(new PointerMoved(centerX(panel, longTab), 15));
            TextTooltip tooltip = (TextTooltip) strip.tooltip();
            assertNotNull(tooltip);
            assertEquals("A very long tab title", tooltip.text());

            manager.handle(new PointerMoved(centerX(panel, shortTab), 15));
            assertNull(strip.tooltip());
        }
    }

    @Test
    void clipsPageContentToTheContentArea() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = panel();
            Widget content = new Widget() {
                /**
                 * Draws a recognizable page rectangle.
                 *
                 * @param renderer drawing manager
                 * @param frame current frame timing
                 */
                @Override
                protected void onDraw(RenderManager renderer, FrameInfo frame) {
                    renderer.rect(localBounds(), Color.WHITE);
                }
            };
            panel.addTab("Page", content);
            manager.add(panel);
            manager.render();

            RoundedRectCommand page = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .filter(command -> command.color().equals(Color.WHITE))
                    .findFirst().orElseThrow();
            assertEquals(new Rect(0, 30, 400, 270), page.clip());
        }
    }

    @Test
    void supportsPerTabOverrides() {
        TabPanel panel = panel();
        TabPanel.Tab tab = panel.addTab("Tab", null);

        panel.setTabsClosable(true);
        assertTrue(panel.isTabClosable(tab));
        panel.setTabClosable(tab, false);
        assertFalse(panel.isTabClosable(tab));
        panel.clearTabClosable(tab);
        assertTrue(panel.isTabClosable(tab));

        panel.setTabsMovable(false);
        assertFalse(panel.isTabMovable(tab));
        panel.setTabMovable(tab, true);
        assertTrue(panel.isTabMovable(tab));
        tab.setLocked(true);
        assertFalse(panel.isTabMovable(tab));
        assertFalse(panel.select(tab));
    }

    @Test
    void drawsTabsBelowTheViewportOrigin() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            TabPanel panel = new TabPanel();
            panel.setBounds(new Rect(0, 120, 400, 300));
            panel.addTab("First", new FixedWidget(10, 10));
            manager.add(panel);
            manager.render();

            RoundedRectCommand activeTab = backend.lastDrawList().commands().stream()
                    .filter(RoundedRectCommand.class::isInstance)
                    .map(RoundedRectCommand.class::cast)
                    .filter(command -> command.bounds().height() == 26)
                    .findFirst().orElseThrow();
            assertEquals(120f, activeTab.clip().y(), 0.001f);
            assertEquals(30f, activeTab.clip().height(), 0.001f);
        }
    }

    @Test
    void removesSelectedTabsAndNotifiesChanges() {
        TabPanel panel = panel();
        TabPanel.Tab first = panel.addTab("First", new FixedWidget(10, 10));
        TabPanel.Tab second = panel.addTab("Second", new FixedWidget(10, 10));
        List<TabPanel.Tab> changes = new ArrayList<>();
        panel.setOnChange(changes::add);

        panel.select(second);
        assertTrue(panel.removeTab(second));
        assertSame(first, panel.selected());
        assertEquals(List.of(second, first), changes);

        panel.removeTab(first);
        assertNull(panel.selected());
        assertEquals(3, changes.size());
        assertNull(changes.get(2));
    }

    @Test
    void skipsLockedTabsWhenTheActiveTabIsRemoved() {
        TabPanel panel = panel();
        TabPanel.Tab first = panel.addTab("First", null);
        TabPanel.Tab locked = panel.addTab("Locked", null);
        locked.setLocked(true);
        TabPanel.Tab third = panel.addTab("Third", null);

        panel.select(third);
        panel.removeTab(third);

        assertSame(first, panel.selected());
    }

    @Test
    void rejectsInvalidArgumentsAndSettings() {
        TabPanel panel = panel();
        assertThrows(NullPointerException.class, () -> panel.addTab(null, null));
        assertThrows(NullPointerException.class, () -> panel.select(null));
        assertThrows(IllegalArgumentException.class, () -> panel.select(0));
        assertThrows(IllegalArgumentException.class, () -> panel.moveTab(0, 1));
        assertThrows(IllegalArgumentException.class, () -> panel.setTabHeight(0));
        assertThrows(IllegalArgumentException.class, () -> panel.setTabMinWidth(-1));
        assertThrows(IllegalArgumentException.class, () -> panel.setTabMaxWidth(10));
        assertThrows(IllegalArgumentException.class, () -> panel.setTabPadding(-1));
        assertThrows(IllegalArgumentException.class, () -> panel.setIconSize(0));
        assertThrows(IllegalArgumentException.class, () -> panel.setContentPadding(-1));

        TabPanel.Tab tab = panel.addTab("Tab", null);
        TabPanel.Tab foreign = panel().addTab("Other", null);
        assertThrows(IllegalArgumentException.class, () -> panel.setTabClosable(foreign, true));
        assertFalse(panel.closeTab(foreign));
        assertThrows(NullPointerException.class, () -> tab.setTitle(null));
        assertThrows(NullPointerException.class, () -> tab.setIconSide(null));
    }

    @Test
    void prefersAdjacentPageOverADistantUnlockedTab() {
        TabPanel panel = panel();
        TabPanel.Tab adjacent = panel.addTab("Left", null);
        TabPanel.Tab active = panel.addTab("Active", null);
        panel.addTab("Locked", null).setLocked(true);
        panel.addTab("Distant", null);
        panel.select(active);
        panel.removeTab(active);
        assertSame(adjacent, panel.selected());
    }

    @Test
    void queuedScrollInputRespectsTheLatestTargetAndViewport() {
        try (WidgetManager manager = manager(new TestBackend())) {
            TabPanel panel = panel();
            panel.setBounds(new Rect(0, 0, 200, 100));
            for (int index = 0; index < 10; index++) {
                panel.addTab("Tab", null);
            }
            manager.add(panel);
            manager.render();
            manager.handle(new PointerScrolled(50, 15, 0, -1));
            manager.handle(new PointerScrolled(50, 15, 0, -1));
            manager.update(0.2f);
            assertEquals(96, panel.scrollOffset(), 0.001f);

            panel.scrollTo(0);
            panel.scrollTo(panel.scrollOffset());
            manager.update(0.2f);
            assertEquals(96, panel.scrollOffset(), 0.001f);

            panel.scrollTo(200);
            panel.setBounds(new Rect(0, 0, 800, 100));
            manager.render();
            manager.update(0.2f);
            assertEquals(0, panel.scrollOffset(), 0.001f);
        }
    }

    private static TabPanel panel() {
        TabPanel panel = new TabPanel();
        panel.setBounds(new Rect(0, 0, 400, 300));
        return panel;
    }

    private static float centerX(TabPanel panel, TabPanel.Tab tab) {
        Rect bounds = panel.tabBounds(tab);
        return bounds.x() + bounds.width() / 2;
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
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
}
