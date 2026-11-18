package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.RenderBackend;
import com.lixui.render.Viewport;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Panel;
import com.lixui.widget.WidgetManager;

/**
 * A minimal widget scene that demonstrates the panel widget with uniform and diagonal corners.
 */
final class DemoWidgetScene implements AutoCloseable {
    private static final float PANEL_WIDTH = 360;
    private static final float PANEL_HEIGHT = 220;
    private static final float GAP = 32;
    private static final float CORNER_RADIUS = 28;

    private final WidgetManager manager;
    private final Panel roundedPanel;
    private final Panel diagonalPanel;
    private Viewport viewport;

    DemoWidgetScene(InputState input, RenderBackend backend, UiPlatform platform) {
        manager = new WidgetManager(input, backend, platform);
        Theme theme = manager.context().theme();
        roundedPanel = new Panel();
        roundedPanel.setStyle(withRadius(theme.panelStyle(), CORNER_RADIUS));
        diagonalPanel = new Panel();
        diagonalPanel.setStyle(withRadius(theme.primaryStyle(), CORNER_RADIUS));
        diagonalPanel.setCorners(new Panel.Corners(CORNER_RADIUS, 0, CORNER_RADIUS, 0));
        manager.add(roundedPanel).add(diagonalPanel);
    }

    /**
     * Updates the viewport and centers both panels in the window.
     *
     * @param viewport target viewport
     */
    void resize(Viewport viewport) {
        if (viewport.equals(this.viewport)) {
            return;
        }
        this.viewport = viewport;
        manager.resize(viewport);
        float totalWidth = PANEL_WIDTH * 2 + GAP;
        float left = (viewport.logicalWidth() - totalWidth) / 2;
        float top = (viewport.logicalHeight() - PANEL_HEIGHT) / 2;
        roundedPanel.setBounds(new Rect(left, top, PANEL_WIDTH, PANEL_HEIGHT));
        diagonalPanel.setBounds(new Rect(left + PANEL_WIDTH + GAP, top, PANEL_WIDTH, PANEL_HEIGHT));
    }

    /**
     * Updates the widget tree and drains input events.
     *
     * @param deltaSeconds elapsed time in seconds
     */
    void update(float deltaSeconds) {
        manager.update(deltaSeconds);
    }

    /**
     * Lays out and renders the widget tree.
     */
    void render() {
        manager.render();
    }

    /**
     * Closes the manager and releases context-owned resources.
     */
    @Override
    public void close() {
        manager.close();
    }

    private static WidgetStyle withRadius(WidgetStyle style, float radius) {
        return new WidgetStyle(style.background(), style.hoverBackground(), style.pressedBackground(),
                style.disabledBackground(), style.text(), style.disabledText(), style.border(),
                style.focusBorder(), radius, style.padding(), style.borderWidth());
    }
}
