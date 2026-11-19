package com.lixui.widget;

import com.lixui.widget.control.Button;
import com.lixui.widget.container.Window;
import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.input.event.PointerMoved;
import com.lixui.input.event.PointerPressed;
import com.lixui.input.event.PointerReleased;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.DrawList;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.widget.testsupport.FixedWidget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetOrderTest {
    private static final Viewport VIEWPORT = new Viewport(800, 600, 800, 600);

    @Test
    void alwaysOnTopIsDrawnAndHitTestedAboveLaterRegularWidgets() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel bottom = panel(new Rect(0, 0, 100, 100));
            Panel top = panel(new Rect(20, 20, 100, 100));
            Panel later = panel(new Rect(30, 30, 100, 100));
            top.setAlwaysOnTop(true);
            manager.add(bottom).add(top).add(later);

            manager.render();

            assertEquals(List.of(bottom, later, top),
                    paintedPanels(backend.lastDrawList, bottom, top, later));
            manager.handle(new PointerMoved(50, 50));
            assertEquals(top, manager.hovered());
        }
    }

    @Test
    void bringToFrontMovesTheTopLevelAncestorToTheTop() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel first = panel(new Rect(0, 0, 100, 100));
            Panel second = panel(new Rect(50, 50, 100, 100));
            FixedWidget child = new FixedWidget(10, 10);
            first.add(child);
            manager.add(first).add(second);

            manager.bringToFront(child);
            manager.render();

            assertEquals(List.of(second, first), paintedPanels(backend.lastDrawList, first, second));
            assertThrows(IllegalArgumentException.class, () -> manager.bringToFront(new Panel()));
            assertThrows(NullPointerException.class, () -> manager.bringToFront(null));
        }
    }

    @Test
    void plainWidgetsKeepTheirOrderOnClickUnlessOptedIn() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel first = panel(new Rect(0, 0, 100, 100));
            Panel second = panel(new Rect(50, 50, 100, 100));
            manager.add(first).add(second);

            click(manager, 10, 10);

            manager.render();
            assertEquals(List.of(first, second), paintedPanels(backend.lastDrawList, first, second));
        }
    }

    @Test
    void optedInWidgetsAndWindowsRaiseOnPointerPress() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel first = panel(new Rect(0, 0, 100, 100));
            first.setRaiseOnFocus(true);
            Panel second = panel(new Rect(50, 50, 100, 100));
            manager.add(first).add(second);

            click(manager, 10, 10);

            manager.render();
            assertEquals(List.of(second, first), paintedPanels(backend.lastDrawList, first, second));
        }
    }

    @Test
    void windowsRaiseByDefaultAndStayBelowAlwaysOnTopWidgets() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Window lower = new Window();
            lower.setBounds(new Rect(0, 0, 300, 240));
            Window upper = new Window();
            upper.setBounds(new Rect(100, 0, 300, 240));
            Panel pinned = panel(new Rect(0, 300, 100, 100));
            pinned.setAlwaysOnTop(true);
            manager.add(lower).add(upper).add(pinned);

            click(manager, 20, 20);

            manager.render();
            List<Widget> painted = paintedPanels(backend.lastDrawList, lower, upper, pinned);
            assertTrue(painted.indexOf(lower) > painted.indexOf(upper));
            assertEquals(pinned, painted.getLast());
            assertTrue(lower.isRaiseOnFocus());
        }
    }

    @Test
    void focusRaisesTheTopLevelAncestorWhenEnabled() {
        RecordingBackend backend = new RecordingBackend();
        try (WidgetManager manager = new WidgetManager(new InputState(), backend)) {
            manager.resize(VIEWPORT);
            Panel first = panel(new Rect(0, 0, 200, 100));
            first.setRaiseOnFocus(true);
            Button button = new Button("Go");
            button.setBounds(new Rect(10, 10, 80, 30));
            first.add(button);
            Panel second = panel(new Rect(100, 0, 200, 100));
            manager.add(first).add(second);

            manager.focus(button);

            manager.render();
            assertEquals(List.of(second, first), paintedPanels(backend.lastDrawList, first, second));
        }
    }

    private static Panel panel(Rect bounds) {
        Panel panel = new Panel();
        panel.setBounds(bounds);
        return panel;
    }

    private static void click(WidgetManager manager, float x, float y) {
        manager.handle(new PointerPressed(x, y, MouseButton.LEFT, Modifiers.NONE));
        manager.handle(new PointerReleased(x, y, MouseButton.LEFT, Modifiers.NONE));
    }

    private static List<Widget> paintedPanels(DrawList drawList, Widget... widgets) {
        List<Widget> painted = new ArrayList<>();
        for (DrawCommand command : drawList.commands()) {
            if (command instanceof RoundedRectCommand rectangle) {
                for (Widget widget : widgets) {
                    if (widget.bounds().equals(rectangle.bounds())) {
                        painted.add(widget);
                    }
                }
            }
        }
        return painted;
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
            return new TestTexture(image.width(), image.height());
        }

        @Override
        public void destroyTexture(Texture texture) {
        }

        @Override
        public void close() {
        }
    }

    private record TestTexture(int width, int height) implements Texture {
    }
}
