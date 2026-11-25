package com.lixui.widget.container;

import com.lixui.widget.testsupport.TestViewports;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.FrameInfo;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.input.event.PointerScrolled;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.MaskBeginCommand;
import com.lixui.render.command.MaskEndCommand;
import com.lixui.render.RenderManager;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.Viewport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollPanelTest {
    private static final Viewport VIEWPORT = TestViewports.HIDPI;
    private static final Rect PANEL_BOUNDS = new Rect(0, 0, 200, 150);

    @Test
    void clipsContentDrawingAndHitTestingToInsets() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        panel.setContentInsets(20, 10, 15, 5);
        Widget content = new Widget() {
            /**
             * Draws a recognizable content rectangle.
             *
             * @param renderer drawing manager
             * @param frame current frame timing
             */
            @Override
            protected void onDraw(RenderManager renderer, FrameInfo frame) {
                renderer.rect(localBounds(), Color.WHITE);
            }
        };
        content.setSize(400, 600);
        panel.setContent(content);
        manager.add(panel);
        panel.setScroll(50, 60);
        manager.render();

        RoundedRectCommand command = backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .filter(value -> value.color().equals(Color.WHITE))
                .findFirst().orElseThrow();
        assertEquals(new Rect(5, 20, 185, 115), command.clip());
        assertSame(panel, panel.hitTest(50, 10));
        assertSame(content, panel.hitTest(50, 50));
        assertSame(panel, panel.hitTest(2, 50));
    }

    @Test
    void bubblesWheelToOuterPanelAtTheInnerScrollLimit() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        ScrollPanel outer = new ScrollPanel();
        outer.setBounds(PANEL_BOUNDS);
        ScrollPanel inner = new ScrollPanel();
        inner.setSize(200, 300);
        Widget content = new Widget() { };
        content.setSize(200, 600);
        inner.setContent(content);
        outer.setContent(inner);
        manager.add(outer);
        manager.layout();
        inner.setScroll(0, 300);

        manager.handle(new PointerScrolled(50, 50, 0, -1));
        manager.update(0.2f);

        assertEquals(300, inner.scrollY());
        assertEquals(48, outer.scrollY());
    }

    @Test
    void rejectsInvalidOffsetsWithoutCorruptingTheCurrentPosition() {
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setSize(400, 600);
        panel.setContent(content);
        panel.setScroll(25, 30);

        assertThrows(IllegalArgumentException.class, () -> panel.setScroll(Float.NaN, 40));
        assertThrows(IllegalArgumentException.class, () -> panel.setScroll(40, Float.POSITIVE_INFINITY));
        assertEquals(25, panel.scrollX());
        assertEquals(30, panel.scrollY());
        ScrollBar bar = bar(panel, ScrollBar.Orientation.VERTICAL);
        assertThrows(IllegalArgumentException.class, () -> bar.setValue(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> bar.setVisibleFraction(Float.NaN));
    }

    @Test
    void failedReplacementPreservesTheOriginalContent() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        ScrollPanel panel = new ScrollPanel();
        Widget original = new Widget() { };
        panel.setContent(original);
        manager.add(panel);
        Widget broken = new Widget() {
            /**
             * Simulates failed content initialization.
             *
             * @throws IllegalStateException always
             */
            @Override
            protected void onAttached() {
                throw new IllegalStateException("Attachment failed.");
            }
        };

        assertThrows(IllegalStateException.class, () -> panel.setContent(broken));
        assertSame(original, panel.content());
        assertSame(panel, original.parent());
        assertSame(manager.context(), original.context());
        assertEquals(3, panel.children().size());
    }

    @Test
    void scrollingAnotherAxisDoesNotRestartTheFirstAnimation() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setSize(400, 600);
        panel.setContent(content);
        manager.add(panel);
        manager.layout();

        manager.handle(new PointerScrolled(50, 50, 0, -1));
        manager.update(0.09f);
        manager.handle(new PointerScrolled(50, 50, 1, 0));
        manager.update(0.1f);

        assertEquals(48, panel.scrollY());
        assertTrue(panel.scrollX() > 0 && panel.scrollX() < 48);
    }

    @Test
    void autoSizeMeasuresTheContentAndInsets() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setContentInsets(5, 6, 7, 8);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 120, 80));
        panel.setContent(content);
        panel.setAutoSize(true);
        manager.add(panel);

        manager.layout();

        assertEquals(134, panel.bounds().width(), 0.001f);
        assertEquals(92, panel.bounds().height(), 0.001f);
    }

    @Test
    void exposesScrollRangesAndAppliesTheContentOffset() {
        TestBackend backend = new TestBackend();
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
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
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
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
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
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);
        manager.render();

        manager.handle(new PointerScrolled(50, 50, 0, -1));
        assertEquals(0, panel.scrollY());
        manager.update(0.09f);
        assertTrue(panel.scrollY() > 0 && panel.scrollY() < 48);
        manager.update(0.2f);
        assertEquals(48, panel.scrollY(), 0.0001f);
        assertEquals(0, panel.scrollX());

        panel.setScroll(0, 0);
        manager.handle(new PointerScrolled(50, 50, 1, 0));
        manager.update(0.2f);
        assertTrue(panel.scrollX() > 0);

        panel.setScroll(0, 0);
        manager.handle(new PointerScrolled(190, 20, 0, -1));
        manager.update(0.2f);
        assertTrue(panel.scrollY() > 0);
    }

    @Test
    void clipsChildrenWithARoundedMask() {
        TestBackend backend = new TestBackend();
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
        List<DrawCommand> commands = backend.lastDrawList().commands();
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
        TestBackend backend = new TestBackend();
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
        manager.update(0.2f);
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
    void scrollBarInsetsShiftTheTracksAwayFromTheEdges() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        panel.setScrollBarInsets(3, 10, 12, 5);
        manager.add(panel);
        manager.render();

        assertEquals(new Rect(178, 7, 8, 119),
                bar(panel, ScrollBar.Orientation.VERTICAL).bounds());
        assertEquals(new Rect(9, 126, 169, 8),
                bar(panel, ScrollBar.Orientation.HORIZONTAL).bounds());
    }

    @Test
    void keepsBothScrollbarsFourPixelsFromThePanelEdges() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        ScrollPanel panel = new ScrollPanel();
        panel.setBounds(PANEL_BOUNDS);
        panel.setContentInsets(0, 12, 12, 0);
        Widget content = new Widget() { };
        content.setBounds(new Rect(0, 0, 400, 600));
        panel.setContent(content);
        manager.add(panel);
        manager.render();

        ScrollBar vertical = bar(panel, ScrollBar.Orientation.VERTICAL);
        ScrollBar horizontal = bar(panel, ScrollBar.Orientation.HORIZONTAL);
        assertEquals(4, panel.scrollBarMargin(), 0.001f);
        assertEquals(PANEL_BOUNDS.right() - 4, vertical.bounds().right(), 0.001f);
        assertEquals(PANEL_BOUNDS.bottom() - 4, horizontal.bounds().bottom(), 0.001f);
        assertTrue(hasDrawnBounds(backend, vertical.bounds()));
        assertTrue(hasDrawnBounds(backend, horizontal.bounds()));
    }

    @Test
    void removesScrollbarsWhenContentFits() {
        WidgetManager manager = new WidgetManager(new InputState(), new TestBackend());
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

    private static boolean hasDrawnBounds(TestBackend backend, Rect bounds) {
        return backend.lastDrawList().commands().stream()
                .filter(RoundedRectCommand.class::isInstance)
                .map(RoundedRectCommand.class::cast)
                .anyMatch(command -> command.bounds().equals(bounds));
    }

    private static ScrollBar bar(ScrollPanel panel, ScrollBar.Orientation orientation) {
        for (Widget child : panel.children()) {
            if (child instanceof ScrollBar bar && bar.orientation() == orientation) {
                return bar;
            }
        }
        throw new AssertionError("Missing scrollbar: " + orientation);
    }

}
