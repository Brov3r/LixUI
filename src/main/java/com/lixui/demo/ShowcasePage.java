package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Panel;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Badge;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.Stack;
import com.lixui.widget.layout.VBox;

/**
 * One showcase scene: a full-viewport background, a shared header and a scene body.
 * Pages carry their curated light and dark themes and optional activation hooks that open the
 * navigation overlays or present the feedback toasts.
 */
final class ShowcasePage {
    private static final float PAGE_PADDING = 24;
    private static final float PAGE_GAP = 16;

    private final Widget root;
    private final Theme lightTheme;
    private final Theme darkTheme;
    private final boolean defaultLight;
    private final Badge sceneBadge;
    private final Badge themeBadge;
    private Runnable onActivate = () -> {
    };
    private Runnable onDeactivate = () -> {
    };

    ShowcasePage(ShowcaseKit kit, int index, int count, String title, String subtitle,
                 Theme lightTheme, Theme darkTheme, boolean defaultLight, Widget body) {
        this.lightTheme = lightTheme;
        this.darkTheme = darkTheme;
        this.defaultLight = defaultLight;
        sceneBadge = kit.badge(index + " / " + count, Color.hex("#4F8EF7"));
        themeBadge = kit.badge("", Color.hex("#4F8EF7"));
        Label hint = kit.muted("1-4 scenes  ·  T theme");
        HBox meta = new HBox();
        meta.setGap(8);
        meta.setCrossAlignment(HBox.CrossAlignment.CENTER);
        meta.add(hint).add(sceneBadge).add(themeBadge);

        VBox content = new VBox();
        content.setPadding(PAGE_PADDING);
        content.setGap(PAGE_GAP);
        body.setGrow(1);
        content.add(kit.header(title, subtitle, meta)).add(body);

        Stack stack = new Stack();
        stack.setFill(true);
        stack.setFillViewport(true);
        stack.add(new BackgroundPanel());
        stack.add(content);
        root = stack;
    }

    Widget root() {
        return root;
    }

    boolean isDefaultLight() {
        return defaultLight;
    }

    Theme lightTheme() {
        return lightTheme;
    }

    Theme darkTheme() {
        return darkTheme;
    }

    void onActivate(Runnable action) {
        onActivate = action;
    }

    void onDeactivate(Runnable action) {
        onDeactivate = action;
    }

    void activate() {
        onActivate.run();
    }

    void deactivate() {
        onDeactivate.run();
    }

    void applyTheme(Theme theme) {
        boolean light = theme.background().luminance() > 0.5f;
        themeBadge.setText(light ? "Light" : "Dark");
        for (Badge badge : new Badge[]{sceneBadge, themeBadge}) {
            badge.setBackgroundColor(theme.primary().withAlpha(0.16f));
            badge.setBorderColor(theme.primary().withAlpha(0.5f));
            badge.setTextColor(theme.text());
        }
    }

    /**
     * Paints the active theme background edge to edge so no host clear color shows through.
     */
    private static final class BackgroundPanel extends Panel {
        @Override
        protected WidgetStyle defaultStyle() {
            if (context() == null) {
                return null;
            }
            Theme theme = context().theme();
            Color background = theme.background();
            return new WidgetStyle(background, background, background, background,
                    theme.text(), theme.textMuted(), Color.TRANSPARENT, Color.TRANSPARENT,
                    0, 0, 0);
        }
    }
}
