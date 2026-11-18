package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.text.SystemFonts;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Button;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.Panel;
import com.lixui.widget.ScrollPanel;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;

/**
 * A minimal widget scene that demonstrates panels with uniform and diagonal corners and a button.
 */
final class DemoWidgetScene implements AutoCloseable {
    private static final float PANEL_WIDTH = 360;
    private static final float PANEL_HEIGHT = 220;
    private static final float GAP = 32;
    private static final float CORNER_RADIUS = 28;
    private static final float BUTTON_WIDTH = 160;
    private static final float BUTTON_HEIGHT = 44;
    private static final float BUTTON_GAP = 24;
    private static final float THEME_BUTTON_SIZE = 36;
    private static final int THEME_ICON_SIZE = 18;

    private final WidgetManager manager;
    private final Widget background;
    private final ScrollPanel scrollPanel;
    private final Panel diagonalPanel;
    private final Button button;
    private final Button themeButton;
    private final Texture lightThemeIcon;
    private final Texture darkThemeIcon;
    private int clicks;
    private Viewport viewport;

    DemoWidgetScene(InputState input, RenderBackend backend, UiPlatform platform) {
        manager = new WidgetManager(input, backend, platform);
        SystemFonts.findDefault().ifPresent(data ->
                manager.setDefaultFont(manager.context().createFont(data, 16)));
        lightThemeIcon = manager.context().createTexture(
                ImageData.fromResource("assets/icons-128/light-mode.png",
                        THEME_ICON_SIZE, THEME_ICON_SIZE));
        darkThemeIcon = manager.context().createTexture(
                ImageData.fromResource("assets/icons-128/dark-mode.png",
                        THEME_ICON_SIZE, THEME_ICON_SIZE));
        background = new Background();
        scrollPanel = new ScrollPanel();
        ScrollContent scrollContent = new ScrollContent();
        scrollContent.setBounds(new Rect(0, 0, 520, 460));
        scrollPanel.setContent(scrollContent);
        diagonalPanel = new Panel();
        diagonalPanel.setCorners(new Panel.Corners(CORNER_RADIUS, 0, CORNER_RADIUS, 0));
        button = new Button("Click me");
        button.setOnAction(() -> button.setText("Clicks: " + ++clicks));
        themeButton = new Button();
        themeButton.setIconSize(THEME_ICON_SIZE);
        themeButton.setIcon(lightThemeIcon);
        themeButton.setOnAction(this::toggleTheme);
        applyStyles();
        manager.add(background).add(scrollPanel).add(diagonalPanel).add(button).add(themeButton);
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
        background.setBounds(new Rect(0, 0, viewport.logicalWidth(), viewport.logicalHeight()));
        themeButton.setBounds(new Rect(16, 16, THEME_BUTTON_SIZE, THEME_BUTTON_SIZE));
        float totalWidth = PANEL_WIDTH * 2 + GAP;
        float left = (viewport.logicalWidth() - totalWidth) / 2;
        float contentHeight = PANEL_HEIGHT + BUTTON_GAP + BUTTON_HEIGHT;
        float top = (viewport.logicalHeight() - contentHeight) / 2;
        scrollPanel.setBounds(new Rect(left, top, PANEL_WIDTH, PANEL_HEIGHT));
        diagonalPanel.setBounds(new Rect(left + PANEL_WIDTH + GAP, top, PANEL_WIDTH, PANEL_HEIGHT));
        button.setBounds(new Rect((viewport.logicalWidth() - BUTTON_WIDTH) / 2,
                top + PANEL_HEIGHT + BUTTON_GAP, BUTTON_WIDTH, BUTTON_HEIGHT));
    }

    private void toggleTheme() {
        boolean useLight = manager.context().theme().equals(Theme.dark());
        manager.setTheme(useLight ? Theme.light() : Theme.dark());
        themeButton.setIcon(useLight ? darkThemeIcon : lightThemeIcon);
        applyStyles();
    }

    private void applyStyles() {
        Theme theme = manager.context().theme();
        scrollPanel.setStyle(withRadius(theme.panelStyle(), CORNER_RADIUS));
        diagonalPanel.setStyle(withRadius(theme.primaryStyle(), CORNER_RADIUS));
        button.setStyle(withRadius(theme.primaryStyle(), 12));
        themeButton.setStyle(withRadius(theme.controlStyle(), 8));
        themeButton.setIconTint(theme.text());
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

    private static final class ScrollContent extends Widget {
        /**
         * Draws rows that are wider and taller than the scroll panel.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Theme theme = context().theme();
            for (int index = 0; index < 9; index++) {
                renderer.roundedRect(new Rect(12, 12 + index * 48, 496, 40), 12,
                        index % 2 == 0
                                ? theme.surfaceHover()
                                : theme.primary().withAlpha(0.35f));
            }
        }
    }

    private static final class Background extends Widget {
        /**
         * Paints the current theme background.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            renderer.rect(localBounds(), context().theme().background());
        }
    }
}
