package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.PointerMoved;
import com.lixui.input.PointerPressed;
import com.lixui.input.PointerReleased;
import com.lixui.input.PointerScrolled;
import com.lixui.render.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.MaskBeginCommand;
import com.lixui.render.MaskEndCommand;
import com.lixui.render.RenderBackend;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollPanelTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);
    private static final Rect PANEL_BOUNDS = new Rect(0, 0, 200, 150);

    @Test
    void exposesScrollRangesAndAppliesTheContentOffset() {
        RecordingBackend backend = new RecordingBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);
        manager.render();

        panel.scrollBy(50, 60);

        assertEquals(50, panel.scrollX());
        assertEquals(60, panel.scrollY());
        assertEquals(-50, content.position().x());
        assertEquals(-60, content.position().y());

        panel.scrollBy(1000, 1000);
        assertEquals(200, panel.scrollX());
        assertEquals(450, panel.scrollY());
    }

    @Test
    void dragsTheVerticalAndHorizontalBars() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);
        manager.render();
        ScrollBar vertical = bar(panel, ScrollBar.Orientation.VERTICAL);
        ScrollBar horizontal = bar(panel, ScrollBar.Orientation.HORIZONTAL);
        assertTrue(vertical.isVisible());
        assertTrue(horizontal.isVisible());

        manager.handle(new PointerPressed(190, 20, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(190, 80));
        manager.handle(new PointerReleased(190, 80, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(panel.scrollY() > 0);
        assertEquals(-panel.scrollY(), content.position().y());

        manager.handle(new PointerPressed(50, 140, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerMoved(120, 140));
        manager.handle(new PointerReleased(120, 140, MouseButton.LEFT, Modifiers.NONE));

        assertTrue(panel.scrollX() > 0);
        assertEquals(-panel.scrollX(), content.position().x());
    }

    @Test
    void ignoresNonPrimaryButtonsWhenDraggingBars() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);
        manager.render();

        manager.handle(new PointerPressed(190, 20, MouseButton.RIGHT, Modifiers.NONE));
        manager.handle(new PointerMoved(190, 80));
        manager.handle(new PointerReleased(190, 80, MouseButton.RIGHT, Modifiers.NONE));

        assertEquals(0, panel.scrollY());
        assertEquals(0, content.position().y());
    }

    @Test
    void wheelScrollsTheContentAndBarsHandleTheirOwnWheelInput() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);
        manager.render();

        manager.handle(new PointerScrolled(50, 50, 0, -1));
        assertEquals(0, panel.scrollX());
        assertTrue(panel.scrollY() > 0);

        panel.setScroll(0, 0);
        manager.handle(new PointerScrolled(50, 50, 1, 0));
        assertTrue(panel.scrollX() > 0);

        float before = panel.scrollY();
        manager.handle(new PointerScrolled(190, 20, 0, -1));
        assertTrue(panel.scrollY() > before);
    }

    @Test
    void clipsChildrenWithARoundedMask() {
        RecordingBackend backend = new RecordingBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);
        manager.render();

        int maskBegin = -1;
        int maskEnd = -1;
        List<DrawCommand> commands = backend.lastDrawList.commands();
        for (int index = 0; index < commands.size(); index++) {
            if (commands.get(index) instanceof MaskBeginCommand) {
                maskBegin = index;
            } else if (commands.get(index) instanceof MaskEndCommand) {
                maskEnd = index;
            }
        }
        assertTrue(maskBegin >= 0);
        assertTrue(maskEnd > maskBegin);
    }

    @Test
    void configuresScrollbarsAndScrolling() {
        RecordingBackend backend = new RecordingBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);

        panel.setVerticalScrollBarVisible(false);
        manager.render();
        assertFalse(bar(panel, ScrollBar.Orientation.VERTICAL).isVisible());
        assertTrue(bar(panel, ScrollBar.Orientation.HORIZONTAL).isVisible());
        manager.handle(new PointerScrolled(50, 50, 0, -1));
        assertTrue(panel.scrollY() > 0);

        panel.setVerticalScrollBarVisible(true);
        panel.setHorizontalScrollBarVisible(false);
        manager.render();
        assertTrue(bar(panel, ScrollBar.Orientation.VERTICAL).isVisible());
        assertFalse(bar(panel, ScrollBar.Orientation.HORIZONTAL).isVisible());

        panel.setScrollEnabled(false);
        manager.render();
        assertFalse(bar(panel, ScrollBar.Orientation.VERTICAL).isVisible());
        assertFalse(bar(panel, ScrollBar.Orientation.HORIZONTAL).isVisible());
        panel.setScroll(0, 0);
        manager.handle(new PointerScrolled(50, 50, 0, -1));
        assertEquals(0, panel.scrollY());

        panel.setScrollEnabled(true);
        panel.setScrollBarMargin(12);
        manager.render();
        assertEquals(180, bar(panel, ScrollBar.Orientation.VERTICAL).bounds().x());
        assertEquals(130, bar(panel, ScrollBar.Orientation.HORIZONTAL).bounds().y());

        assertThrows(IllegalArgumentException.class, () -> panel.setScrollBarMargin(-1));
        assertThrows(IllegalArgumentException.class,
                () -> panel.setScrollBarMargin(Float.POSITIVE_INFINITY));
    }

    @Test
    void removesScrollbarsWhenContentFits() {
        WidgetManager manager = new WidgetManager(new InputState(), new RecordingBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 120, 80));
        panel.setContent(content);
        manager.add(panel);
        manager.render();

        assertFalse(bar(panel, ScrollBar.Orientation.VERTICAL).isVisible());
        assertFalse(bar(panel, ScrollBar.Orientation.HORIZONTAL).isVisible());
    }

    private static ScrollBar bar(ScrollPanel panel, ScrollBar.Orientation orientation) {
        for (Widget child : panel.children()) {
            if (child instanceof ScrollBar bar && bar.orientation() == orientation) {
                return bar;
            }
        }
        throw new AssertionError("Missing scrollbar: " + orientation);
    }

    private static final class RecordingBackend implements RenderBackend {
        private DrawList lastDrawList;

        @Override
        public void resize(Viewport viewport) {
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
