package com.lixui.widget;

import com.lixui.widget.testsupport.TestBackend;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.render.command.DrawCommand;
import com.lixui.render.command.MeshCommand;
import com.lixui.render.MeshVertex;
import com.lixui.render.command.RoundedRectCommand;
import com.lixui.render.Viewport;
import com.lixui.theme.WidgetStyle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelTest {
    private static final Viewport VIEWPORT = new Viewport(1600, 1200, 800, 600);
    private static final Rect PANEL_BOUNDS = new Rect(10, 20, 200, 100);

    @Test
    void drawsThePanelStyleBackgroundAndBorder() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Panel panel = new Panel();
        panel.setBounds(PANEL_BOUNDS);
        manager.add(panel);

        manager.render();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(2, commands.size());
        RoundedRectCommand background = assertInstanceOf(RoundedRectCommand.class, commands.get(0));
        WidgetStyle style = manager.context().theme().panelStyle();
        assertEquals(PANEL_BOUNDS, background.bounds());
        assertEquals(style.cornerRadius(), background.radius(), 0.0001f);
        assertEquals(style.background(), background.color());
        assertInstanceOf(MeshCommand.class, commands.get(1));
    }

    @Test
    void resolvesInteractiveBackgroundStatesFromACustomStyle() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Panel panel = new Panel();
        panel.setBounds(PANEL_BOUNDS);
        WidgetStyle style = new WidgetStyle(
                Color.rgb(10, 20, 30), Color.rgb(40, 50, 60), Color.rgb(70, 80, 90),
                Color.rgb(100, 110, 120), Color.rgb(130, 140, 150), Color.rgb(160, 170, 180),
                Color.rgb(190, 200, 210), Color.rgb(220, 230, 240), 10, 12, 2);
        panel.setStyle(style);
        manager.add(panel);

        panel.setHovered(true);
        manager.render();
        RoundedRectCommand hovered = assertInstanceOf(RoundedRectCommand.class,
                backend.lastDrawList().commands().get(0));
        assertEquals(style.hoverBackground(), hovered.color());

        panel.setHovered(false);
        panel.setEnabled(false);
        manager.render();
        RoundedRectCommand disabled = assertInstanceOf(RoundedRectCommand.class,
                backend.lastDrawList().commands().get(0));
        assertEquals(style.disabledBackground(), disabled.color());
        assertEquals(2, backend.lastDrawList().commands().size());
    }

    @Test
    void focusOutlineCanBeDisabled() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Panel panel = new Panel();
        panel.setBounds(PANEL_BOUNDS);
        WidgetStyle style = new WidgetStyle(
                Color.rgb(10, 20, 30), Color.rgb(40, 50, 60), Color.rgb(70, 80, 90),
                Color.rgb(100, 110, 120), Color.rgb(130, 140, 150), Color.rgb(160, 170, 180),
                Color.rgb(190, 200, 210), Color.rgb(220, 230, 240), 10, 12, 2);
        panel.setStyle(style);
        manager.add(panel);
        panel.setFocused(true);

        manager.render();
        assertEquals(style.focusBorder(), borderColor(backend));

        panel.setFocusOutlineEnabled(false);
        manager.render();
        assertEquals(style.border(), borderColor(backend));
    }

    private static Color borderColor(TestBackend backend) {
        MeshCommand border = assertInstanceOf(MeshCommand.class,
                backend.lastDrawList().commands().get(1));
        return border.vertices().get(0).color();
    }

    @Test
    void keepsTheBorderInsideBoundsSoClippingDoesNotCutItInHalf() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Panel panel = new Panel();
        panel.setBounds(PANEL_BOUNDS);
        panel.setClipToBounds(true);
        manager.add(panel);

        manager.render();

        MeshCommand border = assertInstanceOf(MeshCommand.class,
                backend.lastDrawList().commands().get(1));
        for (MeshVertex vertex : border.vertices()) {
            assertTrue(vertex.x() >= PANEL_BOUNDS.x() && vertex.x() <= PANEL_BOUNDS.right(),
                    "x=" + vertex.x());
            assertTrue(vertex.y() >= PANEL_BOUNDS.y() && vertex.y() <= PANEL_BOUNDS.bottom(),
                    "y=" + vertex.y());
        }
        assertEquals(PANEL_BOUNDS.x(), border.bounds().x(), 0.001f);
        assertEquals(PANEL_BOUNDS.y(), border.bounds().y(), 0.001f);
        assertEquals(PANEL_BOUNDS.width(), border.bounds().width(), 0.001f);
        assertEquals(PANEL_BOUNDS.height(), border.bounds().height(), 0.001f);
    }

    @Test
    void diagonalCornersTessellateACustomOutline() {
        TestBackend backend = new TestBackend();
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        Panel panel = new Panel();
        panel.setBounds(PANEL_BOUNDS);
        panel.setCorners(new Panel.Corners(24, 0, 24, 0));
        manager.add(panel);

        manager.render();

        List<DrawCommand> commands = backend.lastDrawList().commands();
        assertEquals(2, commands.size());
        assertInstanceOf(MeshCommand.class, commands.get(0));
        assertInstanceOf(MeshCommand.class, commands.get(1));
    }

    @Test
    void rejectsInvalidCornerRadii() {
        assertThrows(IllegalArgumentException.class, () -> new Panel.Corners(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Panel.Corners(0, Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Panel.Corners(0, 0, Float.POSITIVE_INFINITY, 0));
    }

}
