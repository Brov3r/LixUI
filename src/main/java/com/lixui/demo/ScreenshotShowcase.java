package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.RenderBackend;
import com.lixui.render.Viewport;
import com.lixui.theme.Theme;
import com.lixui.widget.WidgetManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the widget manager and the four showcase pages for the screenshot demo.
 * The active page is the only visible page; scene selection applies the page theme, lays out the
 * pages and runs the page activation hook that opens overlays or presents toasts.
 */
final class ScreenshotShowcase implements AutoCloseable {
    static final int WIDTH = 1280;
    static final int HEIGHT = 720;
    private static final Color HOST_CLEAR = Color.hex("#0D0F14");

    private final WidgetManager manager;
    private final List<ShowcasePage> pages = new ArrayList<>();
    private final boolean[] lightModes = new boolean[4];
    private int active = -1;
    private ScreenshotOptions.ThemeMode forcedTheme;

    ScreenshotShowcase(InputState input, RenderBackend backend, UiPlatform platform,
                       ScreenshotOptions.ThemeMode forcedTheme) {
        this.forcedTheme = forcedTheme;
        manager = new WidgetManager(input, backend, platform);
        ShowcaseKit kit = new ShowcaseKit(manager, input);
        pages.add(DashboardScene.create(kit));
        pages.add(FormsScene.create(kit));
        pages.add(NavigationScene.create(kit));
        pages.add(FeedbackScene.create(kit));
        for (int index = 0; index < pages.size(); index++) {
            manager.add(pages.get(index).root());
            lightModes[index] = pages.get(index).isDefaultLight();
        }
    }

    int pageCount() {
        return pages.size();
    }

    void resize(Viewport viewport) {
        manager.resize(viewport);
        manager.layout();
    }

    void select(int scene) {
        int index = scene - 1;
        if (index < 0 || index >= pages.size() || index == active) {
            return;
        }
        if (active >= 0) {
            pages.get(active).deactivate();
        }
        active = index;
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            pages.get(pageIndex).root().setVisible(pageIndex == index);
        }
        applyActiveTheme();
        manager.layout();
        pages.get(index).activate();
    }

    void toggleTheme() {
        if (forcedTheme != null) {
            forcedTheme = forcedTheme.toggled();
        } else {
            for (int index = 0; index < lightModes.length; index++) {
                lightModes[index] = !lightModes[index];
            }
        }
        if (active >= 0) {
            applyActiveTheme();
        }
    }

    void update(float deltaSeconds) {
        manager.update(deltaSeconds);
    }

    void render() {
        manager.render();
    }

    int activeScene() {
        return active + 1;
    }

    Color clearColor() {
        return HOST_CLEAR;
    }

    private void applyActiveTheme() {
        if (active < 0) {
            return;
        }
        Theme theme = forcedTheme == null
                ? (lightModes[active] ? pages.get(active).lightTheme() : pages.get(active).darkTheme())
                : (forcedTheme == ScreenshotOptions.ThemeMode.LIGHT ? Theme.light() : Theme.dark());
        manager.setTheme(theme);
        pages.get(active).applyTheme(theme);
    }

    /**
     * Closes the widget manager and every resource created through its context.
     */
    @Override
    public void close() {
        manager.close();
    }
}
