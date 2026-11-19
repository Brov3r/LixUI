package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
import com.lixui.render.ImageData;
import com.lixui.render.RenderBackend;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.SystemFonts;
import com.lixui.text.TextDecoration;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextShadow;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.Button;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.Label;
import com.lixui.widget.RichLabel;
import com.lixui.widget.TabPanel;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.Window;
import com.lixui.widget.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * A widget scene that demonstrates labels, rich text, themed controls and a scrollable window.
 */
final class DemoWidgetScene implements AutoCloseable {
    private static final float THEME_BUTTON_SIZE = 36;
    private static final int THEME_ICON_SIZE = 18;
    private static final float STATS_GAP = 8;
    private static final double FPS_UPDATE_SECONDS = 0.25;
    private static final float WINDOW_WIDTH = 560;
    private static final float WINDOW_HEIGHT = 420;
    private static final float CONTENT_PADDING = 10;
    private static final float CONTENT_GAP = 8;
    private static final Color BLUE = Color.hex("#7EA6F5");
    private static final Color GREEN = Color.hex("#86C46A");
    private static final Color ORANGE = Color.hex("#E39A5B");
    private static final Color PURPLE = Color.hex("#C39BF2");

    private final WidgetManager manager;
    private final RenderBackend backend;
    private final Widget background;
    private final Button themeButton;
    private final Window window;
    private final TabPanel outerTabs;
    private final TabPanel innerTabs;
    private final Label titleLabel;
    private final Label bodyLabel;
    private final Label elidedLabel;
    private final Label decoratedLabel;
    private final Label justifiedLabel;
    private final RichLabel richLabel;
    private final DemoStatsPanel statsPanel;
    private final Texture lightThemeIcon;
    private final Texture darkThemeIcon;
    private final Texture windowIcon;
    private final FontFamily fontFamily;
    private int fps;
    private int fpsFrames;
    private double fpsElapsed;
    private Viewport viewport;

    DemoWidgetScene(InputState input, RenderBackend backend, UiPlatform platform) {
        this.backend = backend;
        manager = new WidgetManager(input, backend, platform);
        fontFamily = createFontFamily();
        lightThemeIcon = manager.context().createTexture(
                ImageData.fromResource("assets/icons-128/light-mode.png",
                        THEME_ICON_SIZE, THEME_ICON_SIZE));
        darkThemeIcon = manager.context().createTexture(
                ImageData.fromResource("assets/icons-128/dark-mode.png",
                        THEME_ICON_SIZE, THEME_ICON_SIZE));
        windowIcon = manager.context().createTexture(
                ImageData.fromResource("assets/icons-128/ui.png",
                        THEME_ICON_SIZE, THEME_ICON_SIZE));
        background = new Background();
        themeButton = new Button();
        themeButton.setIconSize(THEME_ICON_SIZE);
        themeButton.setIcon(lightThemeIcon);
        themeButton.setOnAction(this::toggleTheme);
        titleLabel = new Label("Labels and rich text");
        bodyLabel = new Label("A wrapped label reflows at the window width and aligns its text "
                + "inside the assigned bounds. Resize the window and the lines reflow again, "
                + "keeping the spacing between them.");
        elidedLabel = new Label("A single-line label truncates overflowing text with an ellipsis "
                + "instead of growing past its width, even when the sentence is much longer "
                + "than the space available for it.");
        decoratedLabel = new Label("Underline, strikethrough and a drop shadow");
        justifiedLabel = new Label("Justified text spreads words so both edges stay flush, "
                + "while the last line of the paragraph remains left-aligned, exactly like a "
                + "word processor. Longer paragraphs make the effect obvious: the spacing "
                + "between words grows so every wrapped line reaches the right edge, and only "
                + "the final line keeps its natural width.");
        justifiedLabel.setAlignment(HorizontalAlignment.JUSTIFY);
        richLabel = new RichLabel();
        VBox labelsPage = new VBox();
        labelsPage.setPadding(CONTENT_PADDING);
        labelsPage.setGap(CONTENT_GAP);
        for (Widget label : List.of(titleLabel, bodyLabel, elidedLabel, decoratedLabel,
                justifiedLabel, richLabel)) {
            label.setAutoSize(false);
        }
        labelsPage.add(titleLabel).add(bodyLabel).add(elidedLabel)
                .add(decoratedLabel).add(justifiedLabel).add(richLabel);
        innerTabs = createInnerTabs();
        outerTabs = new TabPanel();
        outerTabs.addTab("Labels", labelsPage);
        outerTabs.addTab("Tabs", innerTabs);
        window = new Window();
        window.setTitle("Label, RichLabel & TabPanel");
        window.setHeaderIcon(windowIcon);
        window.setHeaderIconSize(THEME_ICON_SIZE);
        window.setContent(outerTabs);
        statsPanel = new DemoStatsPanel();
        applyStyles();
        manager.add(background).add(themeButton).add(statsPanel).add(window);
    }

    /**
     * Updates the viewport and places the controls.
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
        statsPanel.setPosition(16 + THEME_BUTTON_SIZE + STATS_GAP, 16);
        float windowX = Math.max(0, (viewport.logicalWidth() - WINDOW_WIDTH) / 2);
        float windowY = Math.max(0, (viewport.logicalHeight() - WINDOW_HEIGHT) / 2);
        window.setBounds(new Rect(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private TabPanel createInnerTabs() {
        TabPanel tabs = new TabPanel();
        tabs.setTabsClosable(true);
        for (int index = 1; index <= 12; index++) {
            Label page = new Label("Content of tab " + index);
            page.setAlignment(HorizontalAlignment.CENTER);
            page.setVerticalAlignment(VerticalAlignment.CENTER);
            page.setAutoSize(false);
            page.setFontFamily(fontFamily);
            tabs.addTab("Tab " + index, page);
        }
        List<TabPanel.Tab> tabList = tabs.tabs();
        tabList.get(0).setIcon(lightThemeIcon);
        tabList.get(1).setIcon(darkThemeIcon);
        tabList.get(2).setTitle("Locked");
        tabList.get(2).setLocked(true);
        tabList.get(3).setTitle("A very long tab title that is truncated");
        tabList.get(4).setTitle("");
        tabList.get(4).setIcon(windowIcon);
        tabs.setTabClosable(tabList.get(0), false);
        tabs.setTabMovable(tabList.get(11), false);
        return tabs;
    }

    private FontFamily createFontFamily() {
        Optional<FontData> data = SystemFonts.findDefault();
        if (data.isEmpty()) {
            return null;
        }
        Font regular = manager.context().createFont(data.get(), 16);
        manager.setDefaultFont(regular);
        return new FontFamily(regular,
                manager.context().createFont(data.get(), 16, FontStyle.BOLD),
                manager.context().createFont(data.get(), 16, FontStyle.ITALIC),
                manager.context().createFont(data.get(), 16, FontStyle.BOLD_ITALIC));
    }

    private void toggleTheme() {
        boolean useLight = manager.context().theme().equals(Theme.dark());
        manager.setTheme(useLight ? Theme.light() : Theme.dark());
        themeButton.setIcon(useLight ? darkThemeIcon : lightThemeIcon);
        applyStyles();
    }

    private void applyStyles() {
        Theme theme = manager.context().theme();
        themeButton.setStyle(withRadius(theme.controlStyle(), 8));
        themeButton.setIconTint(theme.text());
        window.setStyle(withRadius(theme.panelStyle(), 12));
        statsPanel.setStyle(withRadius(theme.panelStyle(), 8));
        outerTabs.setStyle(transparentPanelStyle(theme));
        innerTabs.setStyle(withRadius(theme.panelStyle(), 8));
        titleLabel.setFontFamily(fontFamily);
        titleLabel.setFontStyle(FontStyle.BOLD);
        titleLabel.setDecoration(TextDecoration.UNDERLINE);
        titleLabel.setTextColor(tint(theme, theme.primary()));
        bodyLabel.setFontFamily(fontFamily);
        elidedLabel.setFontFamily(fontFamily);
        elidedLabel.setWrap(true);
        elidedLabel.setMaxLines(1);
        elidedLabel.setOverflow(TextOverflow.ELLIPSIS);
        elidedLabel.setTextColor(theme.textMuted());
        decoratedLabel.setFontFamily(fontFamily);
        decoratedLabel.setDecoration(TextDecoration.UNDERLINE_AND_STRIKETHROUGH);
        decoratedLabel.setShadow(textShadow(theme));
        decoratedLabel.setTextColor(theme.text());
        justifiedLabel.setFontFamily(fontFamily);
        richLabel.setFontFamily(fontFamily);
        rebuildRichLabel(theme);
    }

    private static WidgetStyle transparentPanelStyle(Theme theme) {
        return new WidgetStyle(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                Color.TRANSPARENT, theme.text(), theme.textMuted(), theme.border(),
                theme.focusRing(), 0, 0, 0);
    }

    private static boolean isLight(Theme theme) {
        Color background = theme.background();
        float luminance = 0.2126f * background.r() + 0.7152f * background.g()
                + 0.0722f * background.b();
        return luminance > 0.5f;
    }

    private static Color tint(Theme theme, Color color) {
        if (!isLight(theme)) {
            return color;
        }
        return new Color(color.r() * 0.62f, color.g() * 0.62f, color.b() * 0.62f, color.a());
    }

    private static TextShadow textShadow(Theme theme) {
        return new TextShadow(2, 2, new Color(0, 0, 0, isLight(theme) ? 0.35f : 0.85f));
    }

    private void rebuildRichLabel(Theme theme) {
        Font regular = fontFamily == null ? null : fontFamily.regular();
        TextShadow shadow = textShadow(theme);
        richLabel.clear();
        richLabel.add("Rich text: ", theme.text());
        richLabel.add("bold", tint(theme, theme.primary()), FontStyle.BOLD, TextDecoration.NONE);
        richLabel.add(" · ", theme.textMuted());
        richLabel.add("italic", tint(theme, BLUE), FontStyle.ITALIC, TextDecoration.NONE);
        richLabel.add(" · ", theme.textMuted());
        richLabel.add("underlined", tint(theme, GREEN), regular, TextDecoration.UNDERLINE);
        richLabel.add(" · ", theme.textMuted());
        richLabel.add("struck", tint(theme, ORANGE), regular, TextDecoration.STRIKETHROUGH);
        richLabel.add(" · ", theme.textMuted());
        richLabel.add(new RichLabel.Span("shadow", tint(theme, PURPLE), null, FontStyle.BOLD,
                TextDecoration.NONE, shadow));
    }

    /**
     * Updates the widget tree and drains input events.
     *
     * @param deltaSeconds elapsed time in seconds
     */
    void update(float deltaSeconds) {
        updateFrameRate(deltaSeconds);
        manager.update(deltaSeconds);
        statsPanel.setFps(fps);
        statsPanel.setVisibleWidgets(countVisibleWidgets());
    }

    /**
     * Lays out and renders the widget tree.
     */
    void render() {
        float contentWidth = Math.max(0, window.bounds().width());
        float contentHeight = Math.max(0, window.bounds().height() - window.headerHeight());
        outerTabs.setSize(contentWidth, contentHeight);
        statsPanel.resizeToContent(manager.renderer());
        manager.render();
        statsPanel.setDrawCalls(backend.drawCallCount());
    }

    /**
     * Closes the manager and releases context-owned resources.
     */
    @Override
    public void close() {
        manager.close();
    }

    private void updateFrameRate(float deltaSeconds) {
        if (deltaSeconds <= 0) {
            return;
        }
        fpsElapsed += deltaSeconds;
        fpsFrames++;
        if (fpsElapsed >= FPS_UPDATE_SECONDS) {
            fps = (int) Math.round(fpsFrames / fpsElapsed);
            fpsElapsed = 0;
            fpsFrames = 0;
        }
    }

    private int countVisibleWidgets() {
        int count = 0;
        for (Widget widget : manager.widgets()) {
            count += countVisibleWidgets(widget);
        }
        return count;
    }

    private static int countVisibleWidgets(Widget widget) {
        if (!widget.isVisible()) {
            return 0;
        }
        int count = 1;
        for (Widget child : widget.children()) {
            count += countVisibleWidgets(child);
        }
        return count;
    }

    private static WidgetStyle withRadius(WidgetStyle style, float radius) {
        return new WidgetStyle(style.background(), style.hoverBackground(), style.pressedBackground(),
                style.disabledBackground(), style.text(), style.disabledText(), style.border(),
                style.focusBorder(), radius, style.padding(), style.borderWidth());
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
