package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.platform.UiPlatform;
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
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.control.KnobSlider;
import com.lixui.widget.control.Label;
import com.lixui.widget.Panel;
import com.lixui.widget.control.RadioBox;
import com.lixui.widget.control.RangeSlider;
import com.lixui.widget.control.RichLabel;
import com.lixui.widget.control.SegmentedButton;
import com.lixui.widget.control.Separator;
import com.lixui.widget.control.Slider;
import com.lixui.widget.container.SplitPanel;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.control.ToggleGroup;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.container.Window;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A widget scene that demonstrates labels, rich text, themed controls and a scrollable window.
 */
final class DemoWidgetScene implements AutoCloseable {
    private static final float THEME_BUTTON_SIZE = 36;
    private static final int THEME_ICON_SIZE = 18;
    private static final float STATS_GAP = 8;
    private static final double FPS_UPDATE_SECONDS = 0.25;
    private static final float WINDOW_WIDTH = 560;
    private static final float WINDOW_HEIGHT = 500;
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
    private final List<IconSlot> iconSlots = new ArrayList<>();
    private Texture lightThemeIcon;
    private Texture darkThemeIcon;
    private final FontFamily fontFamily;
    private int fps;
    private int fpsFrames;
    private double fpsElapsed;
    private Viewport viewport;

    DemoWidgetScene(InputState input, RenderBackend backend, UiPlatform platform) {
        this.backend = backend;
        manager = new WidgetManager(input, backend, platform);
        fontFamily = createFontFamily();
        background = new Background();
        themeButton = new Button();
        themeButton.setIconSize(THEME_ICON_SIZE);
        bindIcon("light-mode.svg", THEME_ICON_SIZE, icon -> lightThemeIcon = icon);
        bindIcon("dark-mode.svg", THEME_ICON_SIZE, icon -> darkThemeIcon = icon);
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
        bindIcon("text.svg", outerTabs.iconSize(), outerTabs.addTab("Labels", labelsPage)::setIcon);
        bindIcon("ui.svg", outerTabs.iconSize(), outerTabs.addTab("Tabs", innerTabs)::setIcon);
        bindIcon("adjustment.svg", outerTabs.iconSize(), outerTabs.addTab("Controls", createControlsPage())::setIcon);
        bindIcon("split.svg", outerTabs.iconSize(), outerTabs.addTab("Split", createSplitPage())::setIcon);
        bindIcon("slider.svg", outerTabs.iconSize(), outerTabs.addTab("Sliders", createSlidersPage())::setIcon);
        window = new Window();
        window.setTitle("LixUI - Demo Window");
        bindIcon("ui.svg", THEME_ICON_SIZE, window::setHeaderIcon);
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
        for (IconSlot slot : iconSlots) {
            slot.resize(manager.context().density());
        }
        themeButton.setIcon(manager.context().theme().equals(Theme.light()) ? darkThemeIcon : lightThemeIcon);
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
        bindIcon("light-mode.svg", tabs.iconSize(), tabList.get(0)::setIcon);
        bindIcon("dark-mode.svg", tabs.iconSize(), tabList.get(1)::setIcon);
        tabList.get(2).setTitle("Locked");
        tabList.get(2).setLocked(true);
        tabList.get(3).setTitle("A very long tab title that is truncated");
        tabList.get(4).setTitle("");
        bindIcon("ui.svg", tabs.iconSize(), tabList.get(4)::setIcon);
        tabs.setTabClosable(tabList.get(0), false);
        tabs.setTabMovable(tabList.get(11), false);
        return tabs;
    }

    private VBox createControlsPage() {
        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.setCrossAlignment(VBox.CrossAlignment.START);

        CheckBox sound = new CheckBox("Enable sound");
        sound.setIndicator(CheckBox.Indicator.SWITCH);
        sound.setTooltipText("Toggles interface sounds.");

        CheckBox grid = new CheckBox("Show grid");
        grid.setChecked(true);
        grid.setTooltipText("Draws the layout grid over the scene.");

        ToggleGroup styleGroup = new ToggleGroup();
        styleGroup.setMaximumSelected(2);
        CheckBox bold = new CheckBox("Bold");
        bold.setChecked(true);
        bold.setTooltipText("At most two styles can be active.");
        CheckBox italic = new CheckBox("Italic");
        CheckBox underline = new CheckBox("Underline");
        bold.setGroup(styleGroup);
        italic.setGroup(styleGroup);
        underline.setGroup(styleGroup);

        ToggleGroup sizeGroup = new ToggleGroup();
        sizeGroup.setExclusive(true);
        RadioBox small = new RadioBox("Small");
        RadioBox medium = new RadioBox("Medium");
        medium.setChecked(true);
        RadioBox large = new RadioBox("Large");
        small.setGroup(sizeGroup);
        medium.setGroup(sizeGroup);
        large.setGroup(sizeGroup);
        sizeGroup.setMinimumSelected(1);

        Label status = new Label("Segmented button: no action yet");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);

        SegmentedButton actions = new SegmentedButton();
        actions.setTooltipText("Each segment runs its own action.");
        addIconSegment(actions, "Cut", "scissors.svg").setOnAction(() -> status.setText("Cut segment clicked"));
        addIconSegment(actions, "Copy", "copy.svg").setOnAction(() -> status.setText("Copy segment clicked"));
        addIconSegment(actions, "Paste", "paste.svg").setOnAction(() -> status.setText("Paste segment clicked"));
        addIconSegment(actions, "Delete", "trash.svg").setEnabled(false);

        SegmentedButton alignment = new SegmentedButton();
        alignment.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
        alignment.setTooltipText("Radio-like selection; icons can be hidden above.");
        addIconSegment(alignment, "Left", "align-left-02.svg");
        addIconSegment(alignment, "Center", "align-horizontal-centers-02.svg");
        addIconSegment(alignment, "Right", "align-right-02.svg");
        alignment.setSelectedIndex(1);
        alignment.setOnSelectionChange(index ->
                status.setText("Alignment segment " + index + " selected"));

        CheckBox icons = new CheckBox("Show segment icons");
        icons.setChecked(true);
        icons.setTooltipText("Hides or shows icons inside both segmented buttons.");
        icons.setOnChange(checked -> {
            actions.setShowIcons(checked);
            alignment.setShowIcons(checked);
        });

        CheckBox multiple = new CheckBox("Multiple segment selection");
        multiple.setTooltipText("Switches the alignment control between radio and check box modes.");
        multiple.setOnChange(checked -> alignment.setSelectionMode(checked
                ? SegmentedButton.SelectionMode.MULTIPLE
                : SegmentedButton.SelectionMode.SINGLE));

        Separator togglesSeparator = new Separator();
        togglesSeparator.setTooltipText("Separates option groups.");
        Separator segmentsSeparator = new Separator();

        page.add(sound).add(grid).add(togglesSeparator)
                .add(bold).add(italic).add(underline)
                .add(small).add(medium).add(large).add(segmentsSeparator)
                .add(icons).add(actions).add(alignment).add(multiple).add(status);
        return page;
    }

    private SplitPanel createSplitPage() {
        SplitPanel outer = new SplitPanel();
        outer.setDividerThickness(8);
        outer.setDividerPosition(0.45f);
        outer.setMinimumPaneSize(90);

        SplitPanel inner = new SplitPanel(SplitPanel.Orientation.VERTICAL);
        inner.setDividerThickness(8);
        inner.setDividerPosition(0.5f);
        inner.setMinimumPaneSize(60);
        inner.setFirst(new SplitDemoPane("Vertical split\nDrag the horizontal divider below "
                + "to resize the top and bottom panes."));
        inner.setSecond(new SplitDemoPane("Vertical split\nThis pane is nested inside the "
                + "horizontal split on the left."));

        outer.setFirst(inner);
        outer.setSecond(new SplitDemoPane("Horizontal split\nDrag the vertical divider on the "
                + "left to resize the right pane."));
        return outer;
    }

    private VBox createSlidersPage() {
        Slider slider = new Slider();
        slider.setRange(0, 100);
        slider.setStep(1);
        slider.setValue(40);
        slider.setTickCount(10);
        slider.setTooltipText("Drag the thumb, use the wheel or press the arrow keys.");

        RangeSlider range = new RangeSlider();
        range.setRange(0, 100);
        range.setStep(1);
        range.setValues(20, 80);
        range.setTickCount(5);
        range.setTooltipText("Drag either thumb to adjust the selected range.");

        Slider vertical = new Slider(Slider.Orientation.VERTICAL);
        vertical.setRange(0, 1000);
        vertical.setStep(10);
        vertical.setValue(250);
        vertical.setTickCount(10);
        vertical.setTooltipText("A vertical slider with compact range labels.");

        RangeSlider verticalRange = new RangeSlider(RangeSlider.Orientation.VERTICAL);
        verticalRange.setRange(0, 100_000);
        verticalRange.setStep(1_000);
        verticalRange.setValues(20_000, 80_000);
        verticalRange.setTickCount(5);
        verticalRange.setTooltipText("Large ranges are shortened to 100k, 1m and so on.");

        KnobSlider knob = new KnobSlider();
        knob.setRange(0, 100);
        knob.setStep(1);
        knob.setValue(60);
        knob.setKnobSize(96);
        knob.setTooltipText("Drag vertically to change the value.");

        Label status = new Label();
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);
        Runnable updateStatus = () -> status.setText("Slider: " + Math.round(slider.value())
                + "    Range: " + Math.round(range.lowValue())
                + " - " + Math.round(range.highValue())
                + "\nVertical: " + Math.round(vertical.value())
                + "    Vertical range: " + Math.round(verticalRange.lowValue())
                + " - " + Math.round(verticalRange.highValue())
                + "    Knob: " + Math.round(knob.value()));
        slider.setOnChange(value -> updateStatus.run());
        range.setOnChange(value -> updateStatus.run());
        vertical.setOnChange(value -> updateStatus.run());
        verticalRange.setOnChange(value -> updateStatus.run());
        knob.setOnChange(value -> updateStatus.run());
        updateStatus.run();

        CheckBox enabled = new CheckBox("Enable sliders");
        enabled.setChecked(true);
        enabled.setOnChange(checked -> {
            slider.setEnabled(checked);
            range.setEnabled(checked);
            vertical.setEnabled(checked);
            verticalRange.setEnabled(checked);
            knob.setEnabled(checked);
        });

        HBox verticalRow = new HBox();
        verticalRow.setGap(24);
        verticalRow.setCrossAlignment(HBox.CrossAlignment.START);
        verticalRow.add(vertical).add(verticalRange).add(knob);
        HBox enabledRow = new HBox();
        enabledRow.add(enabled);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(sliderCaption("Slider")).add(slider)
                .add(sliderCaption("Range slider")).add(range)
                .add(sliderCaption("Vertical sliders and knob")).add(verticalRow)
                .add(enabledRow).add(status);
        return page;
    }

    private Label sliderCaption(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setFontStyle(FontStyle.BOLD);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
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

    private SegmentedButton.Segment addIconSegment(SegmentedButton button, String text, String name) {
        SegmentedButton.Segment segment = button.addSegment(text);
        bindIcon(name, button.iconSize(), segment::setIcon);
        return segment;
    }

    private void bindIcon(String name, float size, Consumer<Texture> assign) {
        iconSlots.add(new IconSlot(name, size, assign));
    }

    private final class IconSlot {
        private final String name;
        private final float size;
        private final Consumer<Texture> assign;
        private Texture texture;

        private IconSlot(String name, float size, Consumer<Texture> assign) {
            this.name = name;
            this.size = size;
            this.assign = assign;
        }

        private void resize(float density) {
            int pixels = Math.max(1, Math.round(size * density));
            if (texture != null && texture.width() == pixels) {
                return;
            }
            Texture previous = texture;
            texture = manager.context().createBundledIcon(name, pixels);
            assign.accept(texture);
            if (previous != null) {
                manager.context().destroyTexture(previous);
            }
        }
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
        return theme.background().luminance() > 0.5f;
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
        statsPanel.resizeToContent(manager.renderer(), THEME_BUTTON_SIZE);
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

    private final class SplitDemoPane extends Panel {
        private static final float INSET = 14;
        private final Label label;

        private SplitDemoPane(String text) {
            label = new Label(text);
            label.setFontFamily(fontFamily);
            label.setAutoSize(false);
            label.setWrap(true);
            label.setVerticalAlignment(VerticalAlignment.CENTER);
            add(label);
        }

        /**
         * Places the pane description inside the pane inset.
         */
        @Override
        protected void onLayout() {
            label.layout(new Rect(INSET, INSET,
                    Math.max(0, bounds().width() - INSET * 2),
                    Math.max(0, bounds().height() - INSET * 2)));
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
