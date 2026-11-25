package com.lixui.demo;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.InputState;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
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
import com.lixui.widget.control.Alert;
import com.lixui.widget.control.Avatar;
import com.lixui.widget.control.Badge;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.ColorPicker;
import com.lixui.widget.control.DatePicker;
import com.lixui.widget.control.Dropdown;
import com.lixui.widget.control.DurationInput;
import com.lixui.widget.control.EmptyState;
import com.lixui.widget.control.Hyperlink;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.control.Image;
import com.lixui.widget.control.Input;
import com.lixui.widget.control.InputArea;
import com.lixui.widget.control.InputCode;
import com.lixui.widget.control.Keycap;
import com.lixui.widget.control.Keybind;
import com.lixui.widget.control.KnobSlider;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.MultiSelect;
import com.lixui.widget.control.NumberStepper;
import com.lixui.widget.Panel;
import com.lixui.widget.control.Pagination;
import com.lixui.widget.control.PasswordInput;
import com.lixui.widget.control.ProgressBar;
import com.lixui.widget.control.RadioBox;
import com.lixui.widget.control.RadioGroup;
import com.lixui.widget.control.RangeSlider;
import com.lixui.widget.control.RichLabel;
import com.lixui.widget.control.SearchInput;
import com.lixui.widget.control.SegmentedButton;
import com.lixui.widget.control.SelectOption;
import com.lixui.widget.control.Separator;
import com.lixui.widget.control.Slider;
import com.lixui.widget.control.Spacer;
import com.lixui.widget.control.Spinner;
import com.lixui.widget.control.SplitButton;
import com.lixui.widget.control.TimePicker;
import com.lixui.widget.control.UnitInput;
import com.lixui.widget.container.Accordion;
import com.lixui.widget.container.GroupBox;
import com.lixui.widget.container.ReorderableList;
import com.lixui.widget.container.SplitPanel;
import com.lixui.widget.container.Table;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.control.ToggleGroup;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.container.Window;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.Anchor;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.FormField;
import com.lixui.widget.layout.Grid;
import com.lixui.widget.layout.GridTrack;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.Stack;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.overlay.ContextMenu;
import com.lixui.widget.overlay.ToastManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * A widget scene that demonstrates labels, rich text, themed controls and a scrollable window.
 */
final class DemoWidgetScene implements AutoCloseable {
    private static final float THEME_BUTTON_SIZE = 36;
    private static final int THEME_ICON_SIZE = 18;
    private static final float STATS_GAP = 8;
    private static final double FPS_UPDATE_SECONDS = 0.25;
    private static final float WINDOW_WIDTH = 620;
    private static final float WINDOW_HEIGHT = 560;
    private static final float CONTENT_PADDING = 10;
    private static final float CONTENT_GAP = 8;
    private static final float THEME_SELECT_WIDTH = 210;
    private static final Color BLUE = Color.hex("#7EA6F5");
    private static final Color GREEN = Color.hex("#86C46A");
    private static final Color ORANGE = Color.hex("#E39A5B");
    private static final Color PURPLE = Color.hex("#C39BF2");
    private static final Theme EMBER_THEME = Theme.dracula()
            .withPrimary(Color.hex("#FF9E64"))
            .withOnPrimary(Color.hex("#282A36"))
            .withFocusRing(Color.hex("#E0AF68"));
    private static final List<ThemeChoice> THEME_CHOICES = List.of(
            new ThemeChoice("Dark", Theme.dark()),
            new ThemeChoice("Light", Theme.light()),
            new ThemeChoice("Dracula", Theme.dracula()),
            new ThemeChoice("Catppuccin Latte", Theme.catppuccinLatte()),
            new ThemeChoice("Catppuccin Frappé", Theme.catppuccinFrappe()),
            new ThemeChoice("Catppuccin Macchiato", Theme.catppuccinMacchiato()),
            new ThemeChoice("Catppuccin Mocha", Theme.catppuccinMocha()),
            new ThemeChoice("Nord", Theme.nord()),
            new ThemeChoice("Gruvbox Dark", Theme.gruvboxDark()),
            new ThemeChoice("Ember (custom)", EMBER_THEME));

    private final WidgetManager manager;
    private final RenderBackend backend;
    private final ToastManager toasts;
    private final Widget background;
    private final Button themeButton;
    private final Dropdown themeSelect;
    private final Window window;
    private final TabPanel outerTabs;
    private final TabPanel innerTabs;
    private final List<TabPanel> groupTabs = new ArrayList<>();
    private final Label titleLabel;
    private final Label bodyLabel;
    private final Label elidedLabel;
    private final Label decoratedLabel;
    private final Hyperlink hyperlink;
    private final Label justifiedLabel;
    private final RichLabel richLabel;
    private final DemoStatsPanel statsPanel;
    private final BenchmarkDemoPage benchmarkPage;
    private Button randomizeProgressButton;
    private final List<IconSlot> iconSlots = new ArrayList<>();
    private final List<GroupBox> groupBoxes = new ArrayList<>();
    private Texture lightThemeIcon;
    private Texture darkThemeIcon;
    private final FontFamily fontFamily;
    private boolean syncingThemeSelection;
    private int fps;
    private int fpsFrames;
    private double fpsElapsed;
    private float lastDeltaSeconds;
    private Viewport viewport;

    DemoWidgetScene(InputState input, RenderBackend backend, UiPlatform platform) {
        this.backend = backend;
        manager = new WidgetManager(input, backend, platform);
        toasts = new ToastManager(manager.context());
        fontFamily = createFontFamily();
        background = new Background();
        themeButton = new Button();
        themeButton.setIconSize(THEME_ICON_SIZE);
        bindIcon("light-mode.svg", THEME_ICON_SIZE, icon -> lightThemeIcon = icon);
        bindIcon("dark-mode.svg", THEME_ICON_SIZE, icon -> darkThemeIcon = icon);
        themeButton.setOnAction(this::toggleTheme);
        themeSelect = new Dropdown();
        themeSelect.setPlaceholder("Theme");
        themeSelect.setMinimumSize(THEME_SELECT_WIDTH, THEME_BUTTON_SIZE);
        themeSelect.setMaximumSize(THEME_SELECT_WIDTH, THEME_BUTTON_SIZE);
        titleLabel = new Label("Labels and rich text");
        bodyLabel = new Label("A wrapped label reflows at the window width and aligns its text "
                + "inside the assigned bounds. Resize the window and the lines reflow again, "
                + "keeping the spacing between them.");
        elidedLabel = new Label("A single-line label truncates overflowing text with an ellipsis "
                + "instead of growing past its width, even when the sentence is much longer "
                + "than the space available for it.");
        decoratedLabel = new Label("Underline, strikethrough and a drop shadow");
        hyperlink = new Hyperlink("Open the LixUI repository", "https://github.com/Brov3r/LixUI");
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

        TabPanel basics = createGroupTabs();
        basics.addTab("Components", new DemoScrollPage(createComponentsPage()));
        basics.addTab("Elements", new DemoScrollPage(createElementsPage()));
        basics.addTab("Interactive", createInteractivePage());
        basics.addTab("Labels", new DemoScrollPage(labelsPage));

        TabPanel inputs = createGroupTabs();
        inputs.addTab("Input", createInputPage());
        inputs.addTab("Text area", createInputAreaPage());
        inputs.addTab("Code", createCodePage());
        inputs.addTab("Keybind", new DemoScrollPage(createKeybindPage()));

        TabPanel forms = createGroupTabs();
        forms.addTab("Form", createFormPage());
        forms.addTab("Controls", createControlsPage());
        forms.addTab("Sliders", new DemoScrollPage(createSlidersPage()));
        forms.addTab("Pickers", createPickersPage());
        forms.addTab("Select", new DemoSelectPage(fontFamily, this::bindIcon).create());
        forms.addTab("Queries", createQueriesPage());

        TabPanel data = createGroupTabs();
        data.addTab("Tables", createTablePage());
        data.addTab("Virtual list", VirtualListDemo.create(fontFamily));
        data.addTab("Tree", TreeViewDemo.create(fontFamily));
        data.addTab("Reorder", createReorderPage());
        data.addTab("Gallery", ImageGalleryDemo.create(fontFamily));
        data.addTab("Timeline", TimelineDemo.create(fontFamily));

        TabPanel feedback = createGroupTabs();
        feedback.addTab("Progress", new DemoScrollPage(createProgressPage()));
        feedback.addTab("Steps", new StepperDemoPage(fontFamily, this::bindIcon).build());
        feedback.addTab("Donut", new DemoScrollPage(
                new DonutChartDemoPage(fontFamily).build()));
        feedback.addTab("Chart", new ChartDemoPage(fontFamily).build());
        feedback.addTab("Toasts", new DemoScrollPage(createToastPage()));

        TabPanel navigation = createGroupTabs();
        navigation.addTab("Menu bar", new DemoMenuPage(fontFamily, this::bindIcon).create());
        navigation.addTab("Breadcrumbs", new DemoBreadcrumbsPage(fontFamily).create());
        navigation.addTab("Radial", new DemoScrollPage(
                new RadialMenuDemoPage(fontFamily, this::bindIcon).build()));

        TabPanel overlays = createGroupTabs();
        overlays.addTab("Dropdowns", createDropdownsPage());
        overlays.addTab("Context menu", createContextMenuPage());
        overlays.addTab("Modals", new DemoScrollPage(new DemoModalPage(fontFamily).page()));

        TabPanel structure = createGroupTabs();
        structure.addTab("Layouts", new DemoScrollPage(createLayoutsPage()));
        structure.addTab("Containers", new DemoScrollPage(createContainersPage()));
        structure.addTab("Split", createSplitPage());
        structure.addTab("Tabs", innerTabs);
        structure.addTab("Accordion", new DemoScrollPage(createAccordionPage()));

        outerTabs = new TabPanel();
        bindIcon("puzzle-03.svg", outerTabs.iconSize(),
                outerTabs.addTab("Basics", basics)::setIcon);
        benchmarkPage = new BenchmarkDemoPage(message -> toasts.show(message));
        bindIcon("lightning.svg", outerTabs.iconSize(), outerTabs.addTab("Benchmark",
                new DemoScrollPage(benchmarkPage))::setIcon);
        bindIcon("keyboard.svg", outerTabs.iconSize(),
                outerTabs.addTab("Inputs", inputs)::setIcon);
        bindIcon("adjustment.svg", outerTabs.iconSize(),
                outerTabs.addTab("Forms", forms)::setIcon);
        bindIcon("table.svg", outerTabs.iconSize(),
                outerTabs.addTab("Data", data)::setIcon);
        bindIcon("progress.svg", outerTabs.iconSize(),
                outerTabs.addTab("Feedback", feedback)::setIcon);
        bindIcon("menu.svg", outerTabs.iconSize(),
                outerTabs.addTab("Navigation", navigation)::setIcon);
        bindIcon("menu-open.svg", outerTabs.iconSize(),
                outerTabs.addTab("Overlays", overlays)::setIcon);
        bindIcon("ui.svg", outerTabs.iconSize(),
                outerTabs.addTab("Structure", structure)::setIcon);
        window = new Window();
        window.setTitle("LixUI - Demo Window");
        bindIcon("ui.svg", THEME_ICON_SIZE, window::setHeaderIcon);
        window.setHeaderIconSize(THEME_ICON_SIZE);
        window.setContent(outerTabs);
        statsPanel = new DemoStatsPanel();
        for (ThemeChoice choice : THEME_CHOICES) {
            themeSelect.addOption(new SelectOption(choice.label(), choice.theme()));
        }
        themeSelect.setSelectedIndex(0);
        themeSelect.setOnChange(option -> {
            if (!syncingThemeSelection && option != null && option.value() instanceof Theme theme) {
                applyTheme(theme);
            }
        });
        // Keep the toolbar above the window when a small viewport shifts the window upward.
        themeButton.setAlwaysOnTop(true);
        themeSelect.setAlwaysOnTop(true);
        statsPanel.setAlwaysOnTop(true);
        applyStyles();
        manager.add(background).add(themeButton).add(themeSelect)
                .add(statsPanel).add(window);
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
        updateThemeButtonIcon();
        background.setBounds(new Rect(0, 0, viewport.logicalWidth(), viewport.logicalHeight()));
        themeButton.setBounds(new Rect(16, 16, THEME_BUTTON_SIZE, THEME_BUTTON_SIZE));
        float controlsX = 16 + THEME_BUTTON_SIZE + STATS_GAP;
        themeSelect.setBounds(new Rect(controlsX, 16, THEME_SELECT_WIDTH, THEME_BUTTON_SIZE));
        statsPanel.setPosition(controlsX + THEME_SELECT_WIDTH + STATS_GAP, 16);
        float windowX = Math.max(0, (viewport.logicalWidth() - WINDOW_WIDTH) / 2);
        float windowY = Math.max(0, (viewport.logicalHeight() - WINDOW_HEIGHT) / 2);
        window.setBounds(new Rect(windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT));
    }

    private TabPanel createGroupTabs() {
        TabPanel tabs = new TabPanel();
        groupTabs.add(tabs);
        return tabs;
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

    private Widget createComponentsPage() {
        Label status = new Label("Inline alerts, avatars, numeric steppers and unit inputs.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Alert info = new Alert(Alert.Tone.INFO);
        info.setTitle("Patch 1.4 is available");
        info.setMessage("Restart the client to apply the update.");

        Alert warning = new Alert(Alert.Tone.WARNING);
        warning.setTitle("Low disk space");
        warning.setMessage("Only 120 MB remain in the data directory.");
        warning.setClosable(true);
        warning.setOnClose(() -> status.setText("Warning alert dismissed."));

        Avatar avatar = new Avatar();
        avatar.setName("Aria Snow");
        avatar.setDiameter(56);
        avatar.addBadge("3");
        Badge online = avatar.addBadge(Avatar.Corner.TOP_RIGHT);
        online.setDotSize(12);
        online.setBackgroundColor(GREEN);
        Avatar iconAvatar = new Avatar();
        iconAvatar.setName("Bran");
        iconAvatar.setDiameter(56);
        iconAvatar.setIcon("user.svg");
        HBox avatarRow = new HBox();
        avatarRow.setGap(16);
        avatarRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        avatarRow.add(avatar).add(iconAvatar);

        NumberStepper stepper = new NumberStepper();
        stepper.setRange(0, 100);
        stepper.setValue(12);
        stepper.setOnValueChange(value -> status.setText("Quantity: " + formatAmount(value)));
        NumberStepper precise = new NumberStepper();
        precise.setRange(-10, 10);
        precise.setStep(0.5);
        precise.setDecimals(1);
        precise.setValue(2.5);
        precise.setOnValueChange(value -> status.setText("Offset: " + formatAmount(value)));
        HBox stepperRow = new HBox();
        stepperRow.setGap(12);
        stepperRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        stepperRow.add(stepper).add(precise);

        DurationInput duration = new DurationInput();
        duration.setValue(90);
        duration.setOnChange(seconds -> status.setText("Duration: " + formatAmount(seconds)
                + " s"));
        UnitInput length = new UnitInput();
        length.setUnits(List.of(new UnitInput.UnitOption("mm", 1),
                new UnitInput.UnitOption("cm", 10), new UnitInput.UnitOption("m", 1000)));
        length.setUnit(new UnitInput.UnitOption("cm", 10));
        length.setValue(250);
        length.setOnChange(millimetres -> status.setText("Length: " + formatAmount(millimetres)
                + " mm"));
        HBox unitRow = new HBox();
        unitRow.setGap(12);
        unitRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        unitRow.add(duration).add(length);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(sliderCaption("Alert: tone colors, wrapped text and an optional close button"))
                .add(info)
                .add(warning)
                .add(sliderCaption("Avatar: initials, image source and a corner badge"))
                .add(avatarRow)
                .add(sliderCaption("Number stepper: click, hold to repeat and type a value"))
                .add(stepperRow)
                .add(sliderCaption("Duration and unit inputs: switch units without losing the value"))
                .add(unitRow)
                .add(status);
        return page;
    }

    private static String formatAmount(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private Widget createInteractivePage() {
        Label status = new Label("Pagination, password and search fields, and a loading spinner.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Pagination pagination = new Pagination();
        pagination.setPageCount(12);
        pagination.setMaxButtons(7);
        pagination.setOnChange(page -> status.setText("Page " + (page + 1)
                + " of " + pagination.pageCount() + "."));

        PasswordInput password = new PasswordInput("hunter2");
        password.setPlaceholder("Password");
        password.setOnChange(value -> status.setText("Password length: "
                + password.rawText().codePointCount(0, password.rawText().length())));

        CheckBox reveal = new CheckBox("Show the reveal button");
        reveal.setChecked(true);
        reveal.setOnChange(password::setRevealButtonVisible);

        SearchInput search = new SearchInput();
        search.setPlaceholder("Search the party...");
        search.setDebounceSeconds(0.4f);
        search.setOnSearch(value -> status.setText(value.isEmpty()
                ? "Search cleared." : "Search: \"" + value + "\""));

        Spinner standard = new Spinner();
        standard.setDiameter(40);
        standard.setThickness(4);
        Spinner halfArc = new Spinner();
        halfArc.setDiameter(28);
        halfArc.setThickness(2.5f);
        halfArc.setArcDegrees(180);
        halfArc.setColor(BLUE);
        Spinner fast = new Spinner();
        fast.setDiameter(52);
        fast.setThickness(5);
        fast.setSpeed(0.7f);
        fast.setTrackVisible(false);
        fast.setColor(GREEN);
        HBox spinnerRow = new HBox();
        spinnerRow.setGap(16);
        spinnerRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        spinnerRow.add(standard).add(halfArc).add(fast);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(sliderCaption("Pagination: ellipses keep the first, current and last pages visible"))
                .add(pagination)
                .add(sliderCaption("Password input: the eye toggles the mask"))
                .add(password)
                .add(reveal)
                .add(sliderCaption("Search input: debounced search with a clear action"))
                .add(search)
                .add(sliderCaption("Spinner: rotating arc with an optional track"))
                .add(spinnerRow)
                .add(sliderCaption("Drag and drop: matching kinds and swapping widgets"))
                .add(DemoDragDrop.create(status))
                .add(status);
        return new DemoScrollPage(page);
    }

    private Widget createElementsPage() {
        Label status = new Label("Badges mark counters, keycaps show shortcuts, "
                + "and the empty state fills a view without content.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Badge counter = new Badge("12");
        Badge highlighted = new Badge("New");
        highlighted.setBackgroundColor(GREEN);
        highlighted.setTextColor(Color.BLACK.withAlpha(0.78f));
        highlighted.setBorderWidth(1);
        highlighted.setBorderColor(GREEN.withAlpha(0.55f));
        Badge dot = new Badge();
        dot.setDotSize(10);
        dot.setBackgroundColor(ORANGE);
        Badge overflow = new Badge("99+");
        overflow.setBackgroundColor(PURPLE);
        overflow.setTextColor(Color.WHITE);

        HBox badgeRow = new HBox();
        badgeRow.setGap(10);
        badgeRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        badgeRow.add(counter).add(highlighted).add(dot).add(overflow)
                .add(createNotificationSample());

        Keycap ctrl = decorativeKeycap("Ctrl");
        Keycap plus = decorativeKeycap("+");
        Keycap shift = decorativeKeycap("Shift");
        Keycap shortcut = decorativeKeycap("K");
        Keycap caps = new Keycap("Caps Lock");
        caps.setTooltipText("Click to toggle the persistent held state.");
        caps.setOnAction(() -> {
            caps.setDown(!caps.isDown());
            status.setText("Caps Lock " + (caps.isDown() ? "on." : "off."));
        });
        Keycap disabled = new Keycap("F1");
        disabled.setInteractive(false);
        disabled.setEnabled(false);

        HBox keyRow = new HBox();
        keyRow.setGap(6);
        keyRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        keyRow.add(ctrl).add(plus).add(shift)
                .add(decorativeKeycap("+")).add(shortcut)
                .add(new Spacer()).add(caps).add(disabled);

        EmptyState empty = new EmptyState("No projects yet",
                "Create your first project to keep quests, notes and shared files together. "
                        + "The description wraps at the available width and stays centered.");
        empty.setIcon("folder.svg");
        empty.setActionText("Create project");
        empty.setOnAction(() -> status.setText("Empty state action: create project."));
        empty.setContentPadding(20);
        empty.setGap(10);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(sliderCaption("Badges: counter, outlined label, dot and a corner badge"))
                .add(badgeRow)
                .add(sliderCaption("Keycaps: decorative shortcuts, a clickable held state and a "
                        + "disabled key"))
                .add(keyRow)
                .add(sliderCaption("Empty state: icon, title, wrapped description and action"))
                .add(new EmptyStateStage(empty))
                .add(status);
        return page;
    }

    private Keycap decorativeKeycap(String text) {
        Keycap keycap = new Keycap(text);
        keycap.setInteractive(false);
        return keycap;
    }

    private Panel createNotificationSample() {
        Panel panel = new Panel();
        panel.setBounds(new Rect(0, 0, 60, 60));
        Image icon = new Image();
        icon.setIcon("notification.svg");
        icon.setBounds(new Rect(12, 12, 36, 36));
        Badge count = new Badge("3");
        count.setAutoSize(false);
        count.setPadding(2);
        count.setBounds(new Rect(40, -6, 22, 22));
        panel.add(icon).add(count);
        return panel;
    }

    private Widget createFormPage() {
        Input name = new Input("Aria");
        name.setPlaceholder("Player name");

        Input email = new Input("aria@example.com");
        email.setPlaceholder("you@example.com");

        Dropdown role = new Dropdown();
        role.setPlaceholder("Choose a class");
        role.addOption("Knight");
        role.addOption("Ranger");
        role.addOption("Mage");
        role.setSelectedIndex(0);

        CheckBox updates = new CheckBox("Send me patch notes");
        updates.setChecked(true);

        FormField nameField = new FormField("Display name", name,
                "Shown to other players in the party.");
        FormField emailField = new FormField("Email", email,
                "Used for account recovery only.");
        FormField roleField = new FormField("Class", role,
                "Determines the starting abilities.");
        FormField updatesField = new FormField("Notifications", updates,
                "You can change this at any time.");

        CheckBox showError = new CheckBox("Show validation error");
        showError.setOnChange(checked -> emailField.setError(checked
                ? "Enter a valid email address." : ""));

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(nameField).add(emailField).add(roleField).add(updatesField).add(showError)
                .add(DemoFormWidgets.tagInputs())
                .add(DemoFormWidgets.propertyGrids());
        return new DemoScrollPage(page);
    }

    private Widget createQueriesPage() {
        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(DemoFormWidgets.filterBars())
                .add(DemoFormWidgets.queryBuilders());
        return new DemoScrollPage(page);
    }

    private Widget createPickersPage() {
        Label status = new Label("Start: 09:30    Reminder: 18:45\nUS date: " + LocalDate.now()
                + "    RU date: " + LocalDate.now()
                + "\nRGB: #4F8EF7FF    Alpha: #FF6B3573");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        TimePicker startTime = new TimePicker(LocalTime.of(9, 30));
        startTime.setMinuteStep(15);
        startTime.setMinimumSize(160, 36);
        startTime.setMaximumSize(240, 46);
        startTime.setTooltipText("Fifteen-minute steps; type any HH:mm time in the popup.");
        TimePicker reminderTime = new TimePicker(LocalTime.of(18, 45));
        reminderTime.setMinuteStep(1);
        reminderTime.setMinimumSize(160, 36);
        reminderTime.setMaximumSize(240, 46);
        reminderTime.setTooltipText("One-minute steps and manual entry.");
        DatePicker enDate = new DatePicker(LocalDate.now(), Locale.US);
        enDate.setMinimumSize(200, 36);
        enDate.setMaximumSize(280, 46);
        enDate.setTooltipText("Weeks start on Sunday and names use en-US.");
        DatePicker ruDate = new DatePicker(LocalDate.now(), Locale.forLanguageTag("ru-RU"));
        ruDate.setMinimumSize(200, 36);
        ruDate.setMaximumSize(280, 46);
        ruDate.setTooltipText("Weeks start on Monday and month names use ru.");
        ColorPicker rgbColor = new ColorPicker(Color.hex("#4F8EF7"));
        rgbColor.setMinimumSize(200, 36);
        rgbColor.setMaximumSize(260, 46);
        rgbColor.setTooltipText("Opaque color: drag the square, strips or type hex/RGB values.");
        ColorPicker alphaColor = new ColorPicker(new Color(1f, 0.42f, 0.21f, 0.45f));
        alphaColor.setMinimumSize(200, 36);
        alphaColor.setMaximumSize(260, 46);
        alphaColor.setTooltipText("Semi-transparent color: the swatch and popup preview show "
                + "the alpha over a checkerboard.");
        Runnable updateStatus = () -> status.setText("Start: " + startTime.time()
                + "    Reminder: " + reminderTime.time()
                + "\nUS date: " + enDate.date() + "    RU date: " + ruDate.date()
                + "\nRGB: " + rgbColor.color().toHex()
                + "    Alpha: " + alphaColor.color().toHex());
        startTime.setOnChange(value -> updateStatus.run());
        reminderTime.setOnChange(value -> updateStatus.run());
        enDate.setOnChange(value -> updateStatus.run());
        ruDate.setOnChange(value -> updateStatus.run());
        rgbColor.setOnChange(value -> updateStatus.run());
        alphaColor.setOnChange(value -> updateStatus.run());

        HBox timeRow = new HBox();
        timeRow.setGap(12);
        timeRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        timeRow.add(startTime).add(reminderTime);

        HBox dateRow = new HBox();
        dateRow.setGap(12);
        dateRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        dateRow.add(enDate).add(ruDate);

        HBox colorRow = new HBox();
        colorRow.setGap(12);
        colorRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        colorRow.add(rgbColor).add(alphaColor);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(sliderCaption("Time picker: clock button opens hour and minute columns; "
                        + "the popup field accepts a typed time"))
                .add(timeRow)
                .add(sliderCaption("Date pickers: en-US weeks start on Sunday, ru-RU weeks "
                        + "start on Monday; single arrows change the month, double arrows change "
                        + "the year, and adjacent-month days are muted"))
                .add(dateRow)
                .add(sliderCaption("Color pickers: opaque RGB and semi-transparent variants; "
                        + "checkerboard previews make alpha visible"))
                .add(colorRow)
                .add(status);
        return new DemoScrollPage(page);
    }

    private VBox createLayoutsPage() {
        Grid grid = new Grid();
        grid.setPadding(6);
        grid.setGap(6);
        grid.setColumns(GridTrack.fixed(92), GridTrack.proportional(1), GridTrack.proportional(2));
        grid.setRows(GridTrack.fixed(28), GridTrack.proportional(1), GridTrack.proportional(1));
        String[] gridLabels = {"fixed 92", "1×", "2×", "fixed 28", "cell", "cell",
                "weighted", "cell", "cell"};
        for (int index = 0; index < gridLabels.length; index++) {
            grid.add(layoutCell(gridLabels[index], layoutColor(index)));
        }
        grid.setGrow(1);

        Flow flow = new Flow();
        flow.setPadding(6);
        flow.setGap(6);
        for (int index = 0; index < 10; index++) {
            LayoutDemoBox cell = layoutCell("Item " + (index + 1), layoutColor(index));
            cell.setBounds(new Rect(0, 0, 62 + (index % 3) * 18, 26));
            flow.add(cell);
        }

        Stack manual = new Stack();
        manual.setGrow(1);
        manual.add(layoutCell("1", BLUE, new Rect(0, 0, 150, 70)));
        manual.add(layoutCell("2", GREEN, new Rect(22, 14, 150, 70)));
        manual.add(layoutCell("3", ORANGE, new Rect(44, 28, 150, 70)));

        Stack fill = new Stack();
        fill.setFill(true);
        fill.setPadding(6);
        fill.setGrow(1);
        fill.add(layoutCell("base layer", PURPLE)
                .alignLabel(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM));
        fill.add(layoutCell("fill overlay", ORANGE.withAlpha(0.5f))
                .alignLabel(HorizontalAlignment.RIGHT, VerticalAlignment.TOP));

        HBox stackRow = new HBox();
        stackRow.setGap(10);
        stackRow.add(manual).add(fill);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(8);
        page.add(sliderCaption("Grid: fixed 92 px, proportional 1× and 2× columns, "
                        + "fixed and weighted rows"))
                .add(grid)
                .add(sliderCaption("Flow: wraps at the available width and keeps every item "
                        + "at its natural size"))
                .add(flow)
                .add(sliderCaption("Stack: manual bounds on the left, fill on the right"))
                .add(stackRow);
        return page;
    }

    private Color layoutColor(int index) {
        return switch (index % 4) {
            case 0 -> BLUE;
            case 1 -> GREEN;
            case 2 -> ORANGE;
            default -> PURPLE;
        };
    }

    private LayoutDemoBox layoutCell(String text, Color accent) {
        return new LayoutDemoBox(text, accent);
    }

    private LayoutDemoBox layoutCell(String text, Color accent, Rect bounds) {
        LayoutDemoBox cell = new LayoutDemoBox(text, accent);
        cell.setBounds(bounds);
        return cell;
    }

    private Widget createContainersPage() {
        Label groupStatus = new Label("The title sits on the upper border and the content follows it.");
        groupStatus.setFontFamily(fontFamily);
        groupStatus.setAutoSize(false);
        groupStatus.setWrap(true);

        CheckBox shadows = new CheckBox("Draw shadows");
        shadows.setChecked(true);
        shadows.setOnChange(checked -> groupStatus.setText("Shadows "
                + (checked ? "enabled." : "disabled.")));
        CheckBox transitions = new CheckBox("Animate transitions");
        transitions.setOnChange(checked -> groupStatus.setText("Transitions "
                + (checked ? "enabled." : "disabled.")));

        VBox groupContent = new VBox();
        groupContent.setGap(6);
        groupContent.add(sliderCaption("The frame carries a title on its upper border."))
                .add(shadows).add(transitions).add(groupStatus);

        GroupBox groupBox = new GroupBox("Group box");
        groupBox.setContentPadding(12);
        groupBox.setContent(groupContent);
        groupBoxes.add(groupBox);

        LayoutDemoBox cell = layoutCell("child", BLUE);
        cell.setBounds(new Rect(0, 0, 90, 40));
        Anchor anchor = new Anchor(Anchor.Alignment.CENTER);
        anchor.setPadding(14);
        anchor.setChild(cell);

        GroupBox stage = new AnchorStage();
        stage.setContent(anchor);
        groupBoxes.add(stage);

        SegmentedButton positions = new SegmentedButton();
        positions.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
        positions.setTooltipText("Places the child in one of the nine anchor positions.");
        for (String label : List.of("TL", "TC", "TR", "CL", "C", "CR", "BL", "BC", "BR")) {
            positions.addSegment(label);
        }
        positions.setSelectedIndex(4);

        CheckBox fillX = new CheckBox("fillX");
        fillX.setTooltipText("Stretches the child across the available width.");
        CheckBox fillY = new CheckBox("fillY");
        fillY.setTooltipText("Stretches the child across the available height.");

        Label anchorStatus = new Label();
        anchorStatus.setFontFamily(fontFamily);
        anchorStatus.setAutoSize(false);
        anchorStatus.setWrap(true);
        Runnable updateAnchorStatus = () -> anchorStatus.setText("Alignment: "
                + anchor.alignment() + ", fillX " + (anchor.isFillX() ? "on" : "off")
                + ", fillY " + (anchor.isFillY() ? "on" : "off") + ".");
        positions.setOnSelectionChange(index -> {
            anchor.setAlignment(Anchor.Alignment.values()[index]);
            updateAnchorStatus.run();
        });
        fillX.setOnChange(checked -> {
            anchor.setFillX(checked);
            updateAnchorStatus.run();
        });
        fillY.setOnChange(checked -> {
            anchor.setFillY(checked);
            updateAnchorStatus.run();
        });
        updateAnchorStatus.run();

        HBox anchorControls = new HBox();
        anchorControls.setGap(12);
        anchorControls.setCrossAlignment(HBox.CrossAlignment.CENTER);
        anchorControls.add(positions).add(fillX).add(fillY);

        VBox anchorContent = new VBox();
        anchorContent.setGap(8);
        anchorContent.add(sliderCaption("Choose a position (T top, B bottom, L left, R right, "
                        + "C center). Fill stretches the child across that axis."))
                .add(anchorControls).add(stage).add(anchorStatus);

        GroupBox anchorBox = new GroupBox("Anchor");
        anchorBox.setContentPadding(12);
        anchorBox.setContent(anchorContent);
        groupBoxes.add(anchorBox);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(groupBox).add(anchorBox);
        return page;
    }

    private Widget createControlsPage() {
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

        Label alignmentStatus = new Label("Alignment: Left");
        alignmentStatus.setFontFamily(fontFamily);
        alignmentStatus.setAutoSize(false);
        alignmentStatus.setWrap(true);

        Label alignmentPreview = new Label("This paragraph follows the radio group selection "
                + "and shows the chosen alignment across the available width.");
        alignmentPreview.setFontFamily(fontFamily);
        alignmentPreview.setAutoSize(false);
        alignmentPreview.setWrap(true);
        alignmentPreview.setAlignment(HorizontalAlignment.LEFT);

        RadioGroup alignmentGroup = new RadioGroup();
        alignmentGroup.addOption("Left");
        alignmentGroup.addOption("Center");
        alignmentGroup.addOption("Right");
        alignmentGroup.setSelectedIndex(0);
        alignmentGroup.setOnChange(index -> {
            alignmentPreview.setAlignment(switch (index) {
                case 1 -> HorizontalAlignment.CENTER;
                case 2 -> HorizontalAlignment.RIGHT;
                default -> HorizontalAlignment.LEFT;
            });
            alignmentStatus.setText("Alignment: "
                    + alignmentGroup.options().get(index).text());
        });

        Label deliveryStatus = new Label("Notifications: Daily digest");
        deliveryStatus.setFontFamily(fontFamily);
        deliveryStatus.setAutoSize(false);
        deliveryStatus.setWrap(true);

        RadioGroup deliveryGroup = new RadioGroup();
        deliveryGroup.addOption("Immediately");
        deliveryGroup.addOption("Daily digest");
        RadioBox weeklyDigest = deliveryGroup.addOption("Weekly digest");
        weeklyDigest.setEnabled(false);
        deliveryGroup.setSelectedIndex(1);
        deliveryGroup.setOnChange(index -> deliveryStatus.setText("Notifications: "
                + deliveryGroup.options().get(index).text()));

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
                .add(small).add(medium).add(large)
                .add(sliderCaption("Radio groups: click an option or use the arrow keys. "
                        + "Disabled options are skipped."))
                .add(alignmentGroup).add(alignmentPreview).add(alignmentStatus)
                .add(deliveryGroup).add(deliveryStatus)
                .add(segmentsSeparator)
                .add(icons).add(actions).add(alignment).add(multiple).add(status);
        return new DemoScrollPage(page);
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

    private Widget createAccordionPage() {
        Accordion multiple = new Accordion();
        multiple.setExpansionDuration(0.25f);
        Accordion.Section profile = multiple.addSection("Profile",
                accordionLabel("Edit the display name, avatar and contact details."));
        bindIcon("user.svg", multiple.iconSize(), profile::setIcon);
        Accordion.Section notifications = multiple.addSection("Notifications",
                accordionLabel("Choose which events should send a notification."));
        bindIcon("notification.svg", multiple.iconSize(), notifications::setIcon);
        Accordion.Section security = multiple.addSection("Security",
                accordionLabel("Two-factor authentication, trusted devices and active sessions."));
        bindIcon("shield.svg", multiple.iconSize(), security::setIcon);
        security.setEnabled(false);
        multiple.expand(profile);

        Accordion exclusive = new Accordion();
        exclusive.setExclusive(true);
        exclusive.setExpansionDuration(0.3f);
        Accordion.Section appearance = exclusive.addSection("Appearance",
                accordionLabel("Pick a theme, accent color and text size for this workspace."));
        bindIcon("palette.svg", exclusive.iconSize(), appearance::setIcon);
        Accordion.Section language = exclusive.addSection("Language",
                accordionLabel("Switch the interface language; translations load instantly."));
        bindIcon("earth.svg", exclusive.iconSize(), language::setIcon);
        Accordion.Section advanced = exclusive.addSection("Advanced",
                accordionLabel("Developer options and experimental features are hidden here."));
        bindIcon("settings.svg", exclusive.iconSize(), advanced::setIcon);
        exclusive.expand(appearance);

        Label status = accordionLabel("First list: Profile is open. Second list: Appearance is open.");
        multiple.setOnExpandedChange(section -> status.setText("First list: " + section.title()
                + (section.isExpanded() ? " opened." : " closed.")));
        exclusive.setOnExpandedChange(section -> status.setText("Second list: " + section.title()
                + (section.isExpanded() ? " opened." : " closed.")));

        CheckBox oneAtATime = new CheckBox("First list: one open at a time");
        oneAtATime.setOnChange(multiple::setExclusive);
        CheckBox titleIcons = new CheckBox("Show title icons");
        titleIcons.setChecked(true);
        titleIcons.setOnChange(checked -> {
            multiple.setShowIcons(checked);
            exclusive.setShowIcons(checked);
        });
        CheckBox enableLocked = new CheckBox("Enable the locked section");
        enableLocked.setOnChange(security::setEnabled);

        HBox options = new HBox();
        options.setGap(14);
        options.add(oneAtATime).add(titleIcons).add(enableLocked);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(multiple).add(exclusive).add(options).add(status);
        return page;
    }

    private Label accordionLabel(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        return label;
    }

    private Widget createProgressPage() {
        List<ProgressBar> bars = new ArrayList<>();
        ProgressBar rounded = progressBar(ProgressBar.Shape.ROUNDED, 0.68f);
        rounded.setBounds(new Rect(0, 0, 190, 16));
        rounded.setCornerRadius(8);
        ProgressBar sharp = progressBar(ProgressBar.Shape.RECTANGLE, 0.34f);
        sharp.setBounds(new Rect(0, 0, 190, 16));
        ProgressBar pill = progressBar(ProgressBar.Shape.PILL, 0.52f);
        pill.setBounds(new Rect(0, 0, 190, 18));
        ProgressBar outline = progressBar(ProgressBar.Shape.ROUNDED, 0.77f);
        outline.setBounds(new Rect(0, 0, 190, 18));
        outline.setAppearance(ProgressBar.Appearance.OUTLINE);
        outline.setThickness(2);
        ProgressBar perimeterRounded = progressBar(ProgressBar.Shape.ROUNDED, 0.64f);
        perimeterRounded.setBounds(new Rect(0, 0, 190, 52));
        perimeterRounded.setCornerRadius(14);
        perimeterRounded.setAppearance(ProgressBar.Appearance.PERIMETER);
        perimeterRounded.setThickness(5);
        ProgressBar perimeterSharp = progressBar(ProgressBar.Shape.RECTANGLE, 0.39f);
        perimeterSharp.setBounds(new Rect(0, 0, 190, 52));
        perimeterSharp.setAppearance(ProgressBar.Appearance.PERIMETER);
        perimeterSharp.setThickness(5);
        ProgressBar circle = progressBar(ProgressBar.Shape.CIRCLE, 0.61f);
        circle.setBounds(new Rect(0, 0, 64, 64));
        circle.setAppearance(ProgressBar.Appearance.FILLED);
        circle.setThickness(8);
        circle.setShowText(true);
        ProgressBar circleOutline = progressBar(ProgressBar.Shape.CIRCLE, 0.31f);
        circleOutline.setBounds(new Rect(0, 0, 64, 64));
        circleOutline.setAppearance(ProgressBar.Appearance.OUTLINE);
        circleOutline.setThickness(8);
        circleOutline.setFillColor(BLUE);
        circleOutline.setShowText(true);
        ProgressBar circleRadial = progressBar(ProgressBar.Shape.CIRCLE, 0.74f);
        circleRadial.setBounds(new Rect(0, 0, 64, 64));
        circleRadial.setAppearance(ProgressBar.Appearance.RADIAL);
        circleRadial.setFillColor(ORANGE);
        circleRadial.setShowText(true);
        ProgressBar polygon = progressBar(ProgressBar.Shape.POLYGON, 0.57f);
        polygon.setBounds(new Rect(0, 0, 64, 64));
        polygon.setPolygon(ProgressBar.regularPolygon(6));
        polygon.setAppearance(ProgressBar.Appearance.PERIMETER);
        polygon.setThickness(6);
        polygon.setShowText(true);
        ProgressBar polygonFill = progressBar(ProgressBar.Shape.POLYGON, 0.57f);
        polygonFill.setBounds(new Rect(0, 0, 64, 64));
        polygonFill.setPolygon(ProgressBar.regularPolygon(6));
        polygonFill.setAppearance(ProgressBar.Appearance.FILLED);
        polygonFill.setFillColor(GREEN);
        polygonFill.setShowText(true);
        ProgressBar polygonRadial = progressBar(ProgressBar.Shape.POLYGON, 0.68f);
        polygonRadial.setBounds(new Rect(0, 0, 64, 64));
        polygonRadial.setPolygon(ProgressBar.regularPolygon(6));
        polygonRadial.setAppearance(ProgressBar.Appearance.RADIAL);
        polygonRadial.setFillColor(PURPLE);
        polygonRadial.setShowText(true);
        ProgressBar gradient = progressBar(ProgressBar.Shape.ROUNDED, 0.82f);
        gradient.setBounds(new Rect(0, 0, 190, 18));
        gradient.setCornerRadius(9);
        gradient.setFillGradient(GREEN, BLUE);

        bars.add(rounded);
        bars.add(sharp);
        bars.add(pill);
        bars.add(outline);
        bars.add(perimeterRounded);
        bars.add(perimeterSharp);
        bars.add(circle);
        bars.add(circleOutline);
        bars.add(circleRadial);
        bars.add(polygon);
        bars.add(polygonFill);
        bars.add(polygonRadial);
        bars.add(gradient);

        HBox filledRow = progressRow(rounded, sharp);
        gradient.setGrow(1);
        HBox gradientRow = new HBox();
        gradientRow.add(gradient);
        HBox outlineRow = progressRow(pill, outline);
        HBox perimeterRow = progressRow(perimeterRounded, perimeterSharp);
        HBox shapesTop = shapeRow(circle, circleOutline, circleRadial);
        HBox shapesBottom = shapeRow(polygon, polygonFill, polygonRadial);

        randomizeProgressButton = new Button("Randomize values");
        randomizeProgressButton.setBounds(new Rect(0, 0, 200, 38));
        randomizeProgressButton.setIconSize(16);
        bindIcon("refresh.svg", 16, randomizeProgressButton::setIcon);
        randomizeProgressButton.setOnAction(() -> {
            for (ProgressBar bar : bars) {
                bar.setValue((float) ThreadLocalRandom.current().nextDouble(bar.minimum(),
                        bar.maximum()));
            }
        });
        HBox actions = new HBox();
        actions.setCrossAlignment(HBox.CrossAlignment.CENTER);
        actions.add(new Spacer()).add(randomizeProgressButton).add(new Spacer());

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(8);
        page.add(hyperlink)
                .add(sliderCaption("Filled bars"))
                .add(filledRow)
                .add(sliderCaption("Gradient fill"))
                .add(gradientRow)
                .add(sliderCaption("Outline bar"))
                .add(outlineRow)
                .add(sliderCaption("Perimeter edge"))
                .add(perimeterRow)
                .add(sliderCaption("Circular and polygon"))
                .add(shapesTop)
                .add(shapesBottom)
                .add(actions);
        return page;
    }

    private HBox shapeRow(ProgressBar... bars) {
        HBox row = new HBox();
        row.setGap(14);
        row.setCrossAlignment(HBox.CrossAlignment.CENTER);
        for (ProgressBar bar : bars) {
            row.add(bar);
        }
        return row;
    }

    private HBox progressRow(ProgressBar first, ProgressBar second) {
        first.setGrow(1);
        second.setGrow(1);
        HBox row = new HBox();
        row.setGap(12);
        row.add(first).add(second);
        return row;
    }

    private ProgressBar progressBar(ProgressBar.Shape shape, float value) {
        ProgressBar bar = new ProgressBar(value);
        bar.setShape(shape);
        bar.setAnimationRate(9);
        return bar;
    }

    private Widget createInputPage() {
        Label status = new Label("Focus a field and try selection, clipboard and undo shortcuts.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Input name = new Input("LixUI");
        name.setPlaceholder("Type your name...");
        name.setOnChange(value -> status.setText("Name changed: \"" + value + "\""));
        name.setOnSubmit(() -> status.setText("Submitted: \"" + name.text() + "\""));

        Input limited = new Input();
        limited.setPlaceholder("Up to 32 characters");
        limited.setMaxLength(32);
        limited.setOnChange(value -> status.setText("Length limit: "
                + value.codePointCount(0, value.length()) + " / " + limited.maxLength()));

        Input password = new Input("secret");
        password.setSecret(true);
        password.setPlaceholder("Password");
        password.setOnChange(value -> {
            String raw = password.rawText();
            status.setText("Password length: " + raw.codePointCount(0, raw.length()));
        });

        Input phone = new Input();
        phone.setMask("+7 (xxx) xxx-xx-xx");
        phone.setAllowedCharacters(Input.CharacterKind.DIGITS);
        phone.setPlaceholder("+7 (___) ___-__-__");
        phone.setOnChange(value -> status.setText("Phone: " + value
                + "    raw: " + phone.rawText()));

        Input date = new Input();
        date.setMask("99.99.9999");
        date.setAllowedCharacters(Input.CharacterKind.DIGITS);
        date.setPlaceholder("DD.MM.YYYY");
        date.setOnChange(value -> status.setText("Date: " + value));

        HBox shortFields = new HBox();
        shortFields.setGap(8);
        limited.setGrow(1);
        password.setGrow(1);
        shortFields.add(limited).add(password);

        HBox maskedFields = new HBox();
        maskedFields.setGap(8);
        phone.setGrow(1);
        date.setGrow(1);
        maskedFields.add(phone).add(date);

        Input readOnly = new Input("Read-only text can still be selected and copied");
        readOnly.setReadOnly(true);

        Label splitStatus = new Label("Split button: primary action or menu command.");
        splitStatus.setFontFamily(fontFamily);
        splitStatus.setAutoSize(false);
        splitStatus.setWrap(true);

        SplitButton split = new SplitButton("Save");
        split.setTooltipText("The left part runs the primary action; the arrow opens a context menu.");
        split.setOnAction(() -> splitStatus.setText("Primary action: saved."));
        ContextMenu.Item saveAs = split.menu().addItem("Save as",
                () -> splitStatus.setText("Menu command: save as."));
        saveAs.setShortcut("Ctrl+Shift+S");
        bindIcon("export.svg", split.menu().iconSize(), saveAs::setIcon);
        ContextMenu.Item saveAll = split.menu().addItem("Save all",
                () -> splitStatus.setText("Menu command: save all."));
        saveAll.setShortcut("Ctrl+Shift+A");
        bindIcon("save.svg", split.menu().iconSize(), saveAll::setIcon);
        bindIcon("save.svg", split.iconSize(), split::setIcon);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(8);
        page.add(sliderCaption("Text input"))
                .add(name)
                .add(sliderCaption("Length limit and password"))
                .add(shortFields)
                .add(sliderCaption("Phone and date masks (digits only)"))
                .add(maskedFields)
                .add(sliderCaption("Read-only"))
                .add(readOnly)
                .add(sliderCaption("Split button"))
                .add(split)
                .add(splitStatus)
                .add(status);
        return new DemoScrollPage(page);
    }

    private Widget createInputAreaPage() {
        Label status = new Label("Enter inserts a line break; Ctrl+Enter submits.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        InputArea area = new InputArea("A multiline text area\nwith soft wrapping enabled "
                + "by default.\n\nUse arrow keys, Home/End, Page Up/Page Down and the wheel "
                + "to navigate longer notes.");
        area.setPlaceholder("Write a note...");
        area.setGrow(1);
        area.setOnChange(value -> {
            String raw = area.rawText();
            status.setText("Characters: " + raw.codePointCount(0, raw.length())
                    + "    Lines: " + (raw.chars().filter(character -> character == '\n')
                    .count() + 1));
        });
        area.setOnSubmit(() -> status.setText("Submitted with Ctrl+Enter."));

        InputArea disabled = new InputArea(
                "A disabled text area cannot be edited or focused.\n"
                        + "Its selection highlight and caret are hidden.");
        disabled.setEnabled(false);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(8);
        page.add(sliderCaption("Multiline input"))
                .add(area)
                .add(sliderCaption("Disabled"))
                .add(disabled)
                .add(status);
        return page;
    }

    private Widget createCodePage() {
        Label status = new Label("Tab indents, Shift+Tab outdents, Ctrl+/ comments, "
                + "Enter auto-indents, Ctrl+Tab moves focus.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        InputCode code = new InputCode("package demo;\n\n"
                + "public final class Example {\n"
                + "    private static final int ANSWER = 42;\n\n"
                + "    public static void main(String[] args) {\n"
                + "        System.out.println(\"Hello, LixUI!\"); // comment\n"
                + "        System.out.println(ANSWER);\n"
                + "    }\n"
                + "}\n");
        code.setPlaceholder("// Write Java code...");
        code.setLineColumnPlacement(InputCode.LineColumnPlacement.BOTTOM);
        code.setGrow(1);
        code.setOnChange(value -> {
            String raw = code.rawText();
            status.setText("Characters: " + raw.codePointCount(0, raw.length())
                    + "    Lines: " + (raw.chars().filter(character -> character == '\n')
                    .count() + 1));
        });
        code.setOnSubmit(() -> status.setText("Submitted with Ctrl+Enter."));

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(8);
        page.add(sliderCaption("Code editor"))
                .add(code)
                .add(status);
        return page;
    }

    private Widget createDropdownsPage() {
        Dropdown dropdown = new Dropdown();
        dropdown.setMinimumSize(320, 40);
        dropdown.setMaximumSize(420, 46);
        dropdown.setPlaceholder("Choose a class");
        dropdown.setMaxPopupHeight(160);
        addClassOption(dropdown, "Knight", "sword.svg");
        addClassOption(dropdown, "Ranger", "bow.svg");
        addClassOption(dropdown, "Mage", "potion.svg");
        addClassOption(dropdown, "Healer", "shield.svg");
        addClassOption(dropdown, "Rogue", "dagger.svg");
        dropdown.setSelectedIndex(0);

        MultiSelect multiselect = new MultiSelect();
        multiselect.setMinimumSize(320, 40);
        multiselect.setMaximumSize(420, 46);
        multiselect.setPlaceholder("Choose abilities");
        multiselect.setMaxPopupHeight(180);
        addAbilityOption(multiselect, "Swordsmanship", "sword.svg");
        addAbilityOption(multiselect, "Archery", "bow.svg");
        addAbilityOption(multiselect, "Alchemy", "potion.svg");
        addAbilityOption(multiselect, "Shielding", "shield.svg");
        addAbilityOption(multiselect, "Lockpicking", "key.svg");
        addAbilityOption(multiselect, "Survival", "torch.svg");
        addAbilityOption(multiselect, "Cooking", "bread.svg");
        addAbilityOption(multiselect, "Repair", "tool-kit.svg");
        multiselect.setSelected(0, true);
        multiselect.setSelected(2, true);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.setCrossAlignment(VBox.CrossAlignment.START);
        page.add(sliderCaption("Dropdown"))
                .add(dropdown)
                .add(sliderCaption("Multi-select"))
                .add(multiselect);
        return new DemoScrollPage(page);
    }

    private Widget createContextMenuPage() {
        Label contextStatus = new Label("Right-click a table row to open its context menu.");
        contextStatus.setFontFamily(fontFamily);
        contextStatus.setAutoSize(false);
        contextStatus.setWrap(true);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.setCrossAlignment(VBox.CrossAlignment.START);
        page.add(sliderCaption("Context menu table"))
                .add(createContextMenuTable(contextStatus))
                .add(contextStatus);
        return new DemoScrollPage(page);
    }

    private Table createContextMenuTable(Label status) {
        Table table = new Table();
        table.setFillWidth(true);
        table.setSelectionMode(Table.SelectionMode.SINGLE);
        table.addColumn("Item", 160);
        table.addColumn("Type", 130);
        table.addColumn("Value", 100);
        List<ContextMenu> menus = new ArrayList<>();
        menus.add(addContextMenuRow(table, "Iron Sword", "Weapon", "120", status));
        menus.add(addContextMenuRow(table, "Oak Bow", "Weapon", "95", status));
        menus.add(addContextMenuRow(table, "Healing Potion", "Consumable", "45", status));
        menus.add(addContextMenuRow(table, "Steel Helmet", "Armor", "180", status));
        menus.add(addContextMenuRow(table, "Ancient Ring", "Trinket", "320", status));
        table.setContextMenuProvider((widget, x, y) -> {
            int index = table.rowAt(y);
            return index >= 0 && index < menus.size() ? menus.get(index) : null;
        });
        return table;
    }

    private ContextMenu addContextMenuRow(Table table, String name, String type, String value,
                                          Label status) {
        Table.Row row = table.addRow(name, type, value);
        ContextMenu menu = new ContextMenu();
        menu.setSubmenuGap(5);

        ContextMenu.Item open = menu.addItem("Open", () -> status.setText("Opened: " + name));
        bindIcon("open-the-door.svg", menu.iconSize(), open::setIcon);
        ContextMenu.Item inspect = menu.addItem("Inspect",
                () -> status.setText("Inspecting: " + name));
        inspect.setShortcut("Enter");
        bindIcon("search.svg", menu.iconSize(), inspect::setIcon);

        ContextMenu.Item move = menu.addItem("Move to", null);
        bindIcon("chest.svg", menu.iconSize(), move::setIcon);
        ContextMenu.Item inventory = move.addChild(new ContextMenu.Item("Inventory",
                () -> status.setText("Moved to inventory: " + name)));
        bindIcon("chest.svg", menu.iconSize(), inventory::setIcon);
        ContextMenu.Item vault = move.addChild(new ContextMenu.Item("Vault",
                () -> status.setText("Moved to vault: " + name)));
        bindIcon("lock.svg", menu.iconSize(), vault::setIcon);
        ContextMenu.Item archive = new ContextMenu.Item("Archive", null);
        bindIcon("book.svg", menu.iconSize(), archive::setIcon);
        ContextMenu.Item common = archive.addChild(new ContextMenu.Item("Common shelf",
                () -> status.setText("Archived to common shelf: " + name)));
        ContextMenu.Item rare = archive.addChild(new ContextMenu.Item("Rare shelf",
                () -> status.setText("Archived to rare shelf: " + name)));
        rare.setTone(ContextMenu.Tone.ACCENT);
        bindIcon("star.svg", menu.iconSize(), rare::setIcon);
        move.addChild(archive);

        menu.addSeparator();
        ContextMenu.Item duplicate = menu.addItem("Duplicate", null);
        duplicate.setShortcut("Ctrl+D");
        duplicate.setEnabled(false);
        bindIcon("copy.svg", menu.iconSize(), duplicate::setIcon);

        ContextMenu.Item select = menu.addItem("Select row",
                () -> table.setSelectedRows(List.of(row)));
        bindIcon("tick.svg", menu.iconSize(), select::setIcon);
        ContextMenu.Item remove = menu.addItem("Delete",
                () -> status.setText("Delete requested: " + name));
        remove.setShortcut("Del");
        remove.setTone(ContextMenu.Tone.DANGER);
        bindIcon("trash.svg", menu.iconSize(), remove::setIcon);
        return menu;
    }

    private void addClassOption(Dropdown dropdown, String label, String icon) {
        SelectOption option = dropdown.addOption(label);
        bindIcon(icon, dropdown.iconSize(), option::setIcon);
    }

    private void addAbilityOption(MultiSelect multiselect, String label, String icon) {
        SelectOption option = new SelectOption(label);
        multiselect.addOption(option);
        bindIcon(icon, multiselect.iconSize(), option::setIcon);
    }

    private Widget createReorderPage() {
        Label queueStatus = reorderStatus("Queue: drag a grip to move one task.");
        Label boardStatus = reorderStatus("Board: select rows with Ctrl or Shift, then drag a grip.");

        ReorderableList queue = new ReorderableList();
        queue.setSelectionMode(Table.SelectionMode.SINGLE);
        queue.setRowHeight(28);
        queue.addColumn("Task", 140);
        queue.addColumn("State", 70);
        queue.addRow("Backlog", "New");
        queue.addRow("Design", "Active");
        queue.addRow("Review", "Waiting");
        queue.addRow("Ship", "Done");
        queue.setOnReorder((from, to) -> {
            Table.Row moved = queue.rows().get(to);
            queueStatus.setText("onReorder(" + from + ", " + to + "): \""
                    + moved.cell(1).text() + "\"");
        });
        queue.setSelectionListener(rows -> {
            if (!rows.isEmpty()) {
                queueStatus.setText("Selected \"" + rows.get(0).cell(1).text() + "\".");
            }
        });

        ReorderableList board = new ReorderableList();
        board.setSelectionMode(Table.SelectionMode.MULTIPLE);
        board.addColumn("Hero", 100);
        board.addColumn("Class", 80);
        board.addColumn("Level", 60);
        String[][] heroes = {
                {"Aria", "Ranger", "12"},
                {"Bran", "Knight", "9"},
                {"Cyra", "Mage", "15"},
                {"Dain", "Guardian", "7"},
                {"Elara", "Healer", "11"},
                {"Fen", "Rogue", "14"},
                {"Gale", "Monk", "10"},
                {"Hale", "Bard", "8"}
        };
        for (String[] hero : heroes) {
            board.addRow(hero);
        }
        board.setOnReorder((from, to) -> {
            Table.Row first = board.rows().get(to);
            boardStatus.setText("onReorder(" + from + ", " + to + "): "
                    + board.selectedRows().size() + " rows, first \""
                    + first.cell(1).text() + "\"");
        });
        board.setSelectionListener(rows -> {
            if (rows.size() > 1) {
                boardStatus.setText("Selected " + rows.size() + " rows.");
            }
        });

        VBox queueColumn = new VBox();
        queueColumn.setGap(6);
        queueColumn.add(sliderCaption("Single-selection queue")).add(queue);

        VBox boardColumn = new VBox();
        boardColumn.setGap(6);
        boardColumn.add(sliderCaption("Multi-selection board")).add(board);

        HBox columns = new HBox();
        columns.setGap(14);
        columns.setCrossAlignment(HBox.CrossAlignment.START);
        columns.add(queueColumn).add(boardColumn);

        VBox content = new VBox();
        content.setPadding(CONTENT_PADDING);
        content.setGap(10);
        content.add(sliderCaption("Two variants: a short single-select queue and a long "
                        + "multi-select board with block moves."))
                .add(columns)
                .add(queueStatus)
                .add(boardStatus);
        return new DemoScrollPage(content);
    }

    private Label reorderStatus(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }

    private Widget createToastPage() {
        Label status = new Label("Toasts stack in the selected corner and expire automatically.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        SegmentedButton corner = new SegmentedButton();
        corner.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
        for (String name : List.of("Top left", "Top right", "Bottom left", "Bottom right")) {
            corner.addSegment(name);
        }
        corner.setSelectedIndex(1);
        corner.setOnSelectionChange(index -> {
            ToastManager.Corner selected = ToastManager.Corner.values()[index];
            toasts.setCorner(selected);
            status.setText("Corner: " + selected + ". Visible limit: "
                    + toasts.maxVisible() + ".");
        });

        SegmentedButton duration = new SegmentedButton();
        duration.setSelectionMode(SegmentedButton.SelectionMode.SINGLE);
        for (String name : List.of("2s", "4s", "8s")) {
            duration.addSegment(name);
        }
        duration.setSelectedIndex(1);
        float[] durations = {2, 4, 8};
        duration.setOnSelectionChange(index -> {
            toasts.setDefaultDuration(durations[index]);
            status.setText("Default lifetime: " + durations[index] + "s.");
        });

        Button plainToast = new Button("Plain show()");
        plainToast.setOnAction(() -> {
            toasts.show("Settings saved.");
            status.setText("Shown the plain show(String) toast with the default lifetime.");
        });
        Button infoToast = new Button("Info");
        infoToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("The keyboard shortcut was copied.")
                    .type(ToastManager.Type.INFO)
                    .title("Heads up")
                    .duration(5));
            status.setText("Shown an info toast with a title and the default info icon.");
        });
        Button successToast = new Button("Success");
        successToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("Your preferences were updated.")
                    .type(ToastManager.Type.SUCCESS)
                    .title("Saved")
                    .duration(5));
            status.setText("Shown a success toast with a title and the default success icon.");
        });
        Button warningToast = new Button("Warning");
        warningToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("The server did not respond in time.")
                    .type(ToastManager.Type.WARNING)
                    .title("Check connection")
                    .duration(6));
            status.setText("Shown a warning toast with a title and a warning icon.");
        });
        Button errorToast = new Button("Error");
        errorToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("The file could not be uploaded. Try again.")
                    .type(ToastManager.Type.ERROR)
                    .title("Upload failed")
                    .duration(6));
            status.setText("Shown an error toast with a title and an error icon.");
        });
        Button customIconToast = new Button("Custom icon");
        customIconToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("ImageSource.icon(\"heart.svg\") and a custom "
                            + "accent replace the info defaults.")
                    .type(ToastManager.Type.INFO)
                    .title("Custom icon")
                    .icon(ImageSource.icon("heart.svg"))
                    .accent(Color.hex("#F07EA6"))
                    .duration(6));
            status.setText("Shown a toast with an explicit icon source and accent color.");
        });

        Flow typeActions = new Flow();
        typeActions.setGap(8);
        typeActions.add(plainToast).add(infoToast).add(successToast)
                .add(warningToast).add(errorToast).add(customIconToast);

        Button longTextToast = new Button("Max lines");
        longTextToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("A long notification wraps across several lines. "
                            + "This card keeps only two lines and replaces the rest with an ellipsis "
                            + "so the stack stays compact even when the message keeps going.")
                    .type(ToastManager.Type.WARNING)
                    .title("Limited to two lines")
                    .maxLines(2)
                    .maxWidth(220)
                    .duration(8));
            status.setText("Shown a toast limited to two lines with a trailing ellipsis.");
        });
        Button wideTextToast = new Button("Wide wrap");
        wideTextToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("A wide notification uses a larger text column "
                            + "and wraps at the configured maximum width instead of growing "
                            + "without a bound. The card itself is allowed to grow between the "
                            + "minimum and maximum card widths, so wide content stays readable "
                            + "and still leaves a comfortable margin around the toast.")
                    .type(ToastManager.Type.INFO)
                    .title("Wide wrap")
                    .minCardWidth(360)
                    .maxWidth(520)
                    .maxCardWidth(560)
                    .maxLines(4)
                    .duration(8));
            status.setText("Shown a wide toast wrapping at 520 units inside a 560 unit card.");
        });
        Button timerOnToast = new Button("Timer on");
        timerOnToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("The accent bar shows the remaining lifetime.")
                    .type(ToastManager.Type.SUCCESS)
                    .title("Timer visible")
                    .showTimer(true)
                    .duration(8));
            status.setText("Shown a toast with the remaining-time bar enabled.");
        });
        Button timerOffToast = new Button("No timer");
        timerOffToast.setOnAction(() -> {
            toasts.show(new ToastManager.Options("This toast still expires and pauses on hover, "
                            + "but the remaining-time bar is hidden.")
                    .type(ToastManager.Type.INFO)
                    .title("Timer hidden")
                    .showTimer(false)
                    .duration(6));
            status.setText("Shown a toast without the remaining-time bar.");
        });
        Button hoverToast = new Button("Hover test");
        hoverToast.setOnAction(() -> {
            toasts.show("Hover this toast to pause its countdown, then move away to resume.", 8);
            status.setText("Shown an eight second toast. Hover it to pause the timer.");
        });

        Flow contentActions = new Flow();
        contentActions.setGap(8);
        contentActions.add(longTextToast).add(wideTextToast)
                .add(timerOnToast).add(timerOffToast).add(hoverToast);

        Button burst = new Button("Queue 4");
        burst.setOnAction(() -> {
            for (int index = 1; index <= 4; index++) {
                toasts.show("Queued notification " + index + ".");
            }
            status.setText("Queued 4 toasts; " + toasts.visibleCount()
                    + " visible, " + toasts.queuedCount() + " waiting.");
        });
        Button clear = new Button("Dismiss all");
        clear.setOnAction(() -> {
            toasts.dismissAll();
            status.setText("All toasts are fading out.");
        });

        Flow queueActions = new Flow();
        queueActions.setGap(8);
        queueActions.add(burst).add(clear);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(sliderCaption("Corner"))
                .add(corner)
                .add(sliderCaption("Default lifetime"))
                .add(duration)
                .add(sliderCaption("Types, titles and icons"))
                .add(typeActions)
                .add(sliderCaption("Line limit, wrap width, timer and hover pause"))
                .add(contentActions)
                .add(sliderCaption("Queue"))
                .add(queueActions)
                .add(status);
        return page;
    }

    private Widget createKeybindPage() {
        Label status = new Label("Click a field and press a shortcut or mouse button. "
                + "Escape cancels; the cross clears.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Keybind primary = new Keybind(Key.K, new Modifiers(true, true, false, false));
        primary.setOnChange(binding -> status.setText("Primary attack: "
                + keybindText(binding) + "."));
        primary.setTooltipText("Click and press a new shortcut.");

        Keybind interact = new Keybind(Key.E, Modifiers.NONE);
        interact.setOnChange(binding -> status.setText("Interact: "
                + keybindText(binding) + "."));

        Keybind menu = new Keybind(Key.TAB, Modifiers.NONE);
        menu.setOnChange(binding -> status.setText("Menu: "
                + keybindText(binding) + "."));
        menu.setTooltipText("Tab is captured while the field is in capture mode.");

        Keybind unbound = new Keybind();
        unbound.setOnChange(binding -> status.setText("Unassigned: "
                + keybindText(binding) + "."));
        unbound.setTooltipText("Empty fields wait for the first shortcut.");

        Keybind mouse = new Keybind(MouseButton.RIGHT, Modifiers.NONE);
        mouse.setOnChange(binding -> status.setText("Aim: "
                + keybindText(binding) + "."));
        mouse.setTooltipText("Click the field, then press a mouse button.");

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(10);
        page.add(sliderCaption("Combat and interface shortcuts"))
                .add(keybindRow("Primary attack", primary))
                .add(keybindRow("Interact", interact))
                .add(keybindRow("Menu (Tab allowed)", menu))
                .add(keybindRow("Unassigned", unbound))
                .add(keybindRow("Aim (mouse allowed)", mouse))
                .add(sliderCaption("Click to capture, press a key or mouse button, Esc to cancel"))
                .add(status);
        return page;
    }

    private HBox keybindRow(String caption, Keybind field) {
        Label label = new Label(caption);
        label.setFontFamily(fontFamily);
        label.setAutoSize(false);
        HBox row = new HBox();
        row.setGap(12);
        row.setCrossAlignment(HBox.CrossAlignment.CENTER);
        row.add(label).add(field);
        return row;
    }

    private static String keybindText(Keybind.Binding binding) {
        return binding == null ? "cleared" : binding.text();
    }

    private SplitPanel createTablePage() {
        SplitPanel stacked = new SplitPanel(SplitPanel.Orientation.VERTICAL);
        stacked.setDividerThickness(8);
        stacked.setDividerPosition(0.5f);
        stacked.setMinimumPaneSize(90);
        stacked.setFirst(createPartyTable());
        stacked.setSecond(createInventoryTable());

        SplitPanel tables = new SplitPanel();
        tables.setDividerThickness(8);
        tables.setDividerPosition(0.5f);
        tables.setMinimumPaneSize(140);
        tables.setFirst(stacked);
        tables.setSecond(createMarketTable());
        return tables;
    }

    private Table createPartyTable() {
        Table table = new Table();
        Table.Column hero = table.addColumn("Hero", 100);
        bindIcon("user.svg", table.iconSize(), hero::setIcon);
        table.addColumn("Class", 80);
        Table.Column level = table.addColumn("Level", 60);
        bindIcon("level.svg", table.iconSize(), level::setIcon);

        addPartyRow(table, "Aria", "Ranger", 12, "bow.svg");
        addPartyRow(table, "Bran", "Knight", 9, "sword.svg");
        addPartyRow(table, "Cyra", "Mage", 15, "potion.svg");
        addPartyRow(table, "Dain", "Guardian", 7, "shield.svg");
        addPartyRow(table, "Elara", "Healer", 11, "potion.svg");
        addPartyRow(table, "Fen", "Rogue", 14, "sword.svg");
        return table;
    }

    private void addPartyRow(Table table, String hero, String role, int level, String icon) {
        Table.Cell heroCell = Table.Cell.of(hero, null, level);
        bindIcon(icon, table.iconSize(), heroCell::setIcon);
        table.addRow(new Table.Row(heroCell, Table.Cell.of(role),
                Table.Cell.of(Integer.toString(level), null, level)));
    }

    private Table createInventoryTable() {
        Table table = new Table();
        table.setColumnResizingEnabled(false);
        table.setSelectionMode(Table.SelectionMode.MULTIPLE);
        table.setDragSelectionEnabled(false);
        Table.Column item = table.addColumn("Item", 110);
        bindIcon("chest.svg", table.iconSize(), item::setIcon);
        table.addColumn("Type", 90);
        table.addColumn("Value", 60);

        addInventoryRow(table, "Iron Sword", "Weapon", 120, "sword.svg");
        addInventoryRow(table, "Oak Bow", "Weapon", 95, "bow.svg");
        addInventoryRow(table, "Round Shield", "Armor", 140, "shield.svg");
        addInventoryRow(table, "Healing Potion", "Consumable", 45, "potion.svg");
        addInventoryRow(table, "Mana Potion", "Consumable", 60, "potion.svg");
        addInventoryRow(table, "Steel Helmet", "Armor", 180, "shield.svg");
        addInventoryRow(table, "Short Dagger", "Weapon", 80, "sword.svg");
        addInventoryRow(table, "Travel Cloak", "Armor", 55, "shirt.svg");
        addInventoryRow(table, "Ancient Ring", "Trinket", 320, "ring.svg");
        addInventoryRow(table, "Lucky Coin", "Trinket", 90, "star-coin.svg");
        addInventoryRow(table, "Torch", "Tool", 15, "torch.svg");
        addInventoryRow(table, "Repair Kit", "Tool", 75, "tool-kit.svg");
        addInventoryRow(table, "Bread", "Food", 10, "bread.svg");
        addInventoryRow(table, "Water Flask", "Food", 12, "water.svg");
        return table;
    }

    private void addInventoryRow(Table table, String name, String type, int value, String icon) {
        Table.Cell itemCell = Table.Cell.of(name, null, value);
        bindIcon(icon, table.iconSize(), itemCell::setIcon);
        table.addRow(new Table.Row(itemCell, Table.Cell.of(type),
                Table.Cell.of(Integer.toString(value), null, value)));
    }

    private Table createMarketTable() {
        Table table = new Table();
        table.setColumnPadding(6);
        table.setSelectionMode(Table.SelectionMode.MULTIPLE);
        table.setDragSelectionEnabled(true);
        table.addColumn("ID", 45);
        Table.Column item = table.addColumn("Item", 120);
        bindIcon("chest.svg", table.iconSize(), item::setIcon);
        table.addColumn("Rarity", 85);
        table.addColumn("Weight", 70);
        table.addColumn("Value", 70);
        table.addColumn("Stock", 65);
        table.addColumn("Seller", 105);

        addMarketRow(table, 1, "Runed Sword", "Rare", "3.2", 850, 4, "Aria", "sword.svg");
        addMarketRow(table, 2, "Elven Bow", "Epic", "1.4", 1240, 2, "Bran", "bow.svg");
        addMarketRow(table, 3, "Tower Shield", "Uncommon", "8.0", 420, 9, "Cyra", "shield.svg");
        addMarketRow(table, 4, "Healing Draught", "Common", "0.3", 55, 24, "Dain", "potion.svg");
        addMarketRow(table, 5, "Mana Draught", "Common", "0.3", 70, 18, "Elara", "potion.svg");
        addMarketRow(table, 6, "Silver Ring", "Rare", "0.1", 610, 6, "Fen", "ring.svg");
        addMarketRow(table, 7, "Chest Key", "Common", "0.1", 25, 40, "Aria", "key.svg");
        addMarketRow(table, 8, "Lucky Coin", "Uncommon", "0.1", 180, 12, "Bran", "star-coin.svg");
        addMarketRow(table, 9, "Torch", "Common", "0.5", 18, 50, "Cyra", "torch.svg");
        addMarketRow(table, 10, "Repair Kit", "Uncommon", "1.0", 95, 15, "Dain", "tool-kit.svg");
        addMarketRow(table, 11, "Travel Cloak", "Common", "1.2", 65, 8, "Elara", "shirt.svg");
        addMarketRow(table, 12, "Steel Helmet", "Uncommon", "4.5", 240, 5, "Fen", "shield.svg");
        addMarketRow(table, 13, "Ancient Tome", "Epic", "2.0", 1580, 1, "Aria", "book.svg");
        addMarketRow(table, 14, "Short Dagger", "Common", "0.8", 85, 20, "Bran", "sword.svg");
        addMarketRow(table, 15, "Bread", "Common", "0.2", 12, 60, "Cyra", "bread.svg");
        addMarketRow(table, 16, "Water Flask", "Common", "0.4", 14, 45, "Dain", "water.svg");
        return table;
    }

    private void addMarketRow(Table table, int id, String name, String rarity, String weight,
                              int value, int stock, String seller, String icon) {
        Table.Cell itemCell = Table.Cell.of(name, null, value);
        bindIcon(icon, table.iconSize(), itemCell::setIcon);
        table.addRow(new Table.Row(
                Table.Cell.of(Integer.toString(id), null, id),
                itemCell,
                Table.Cell.of(rarity),
                Table.Cell.of(weight, null, Float.parseFloat(weight)),
                Table.Cell.of(Integer.toString(value), null, value),
                Table.Cell.of(Integer.toString(stock), null, stock),
                Table.Cell.of(seller)));
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
        Theme next = isLight(manager.context().theme()) ? Theme.dark() : Theme.light();
        applyTheme(next);
    }

    private void applyTheme(Theme theme) {
        manager.setTheme(theme);
        updateThemeButtonIcon();
        applyStyles();
        int index = themeIndex(theme);
        if (index >= 0 && themeSelect.selectedIndex() != index) {
            syncingThemeSelection = true;
            try {
                themeSelect.setSelectedIndex(index);
            } finally {
                syncingThemeSelection = false;
            }
        }
    }

    private void updateThemeButtonIcon() {
        themeButton.setIcon(isLight(manager.context().theme())
                ? darkThemeIcon : lightThemeIcon);
    }

    private static int themeIndex(Theme theme) {
        for (int index = 0; index < THEME_CHOICES.size(); index++) {
            if (THEME_CHOICES.get(index).theme().equals(theme)) {
                return index;
            }
        }
        return -1;
    }

    private void applyStyles() {
        Theme theme = manager.context().theme();
        themeButton.setStyle(withRadius(theme.controlStyle(), 8));
        themeButton.setIconTint(theme.text());
        window.setStyle(withRadius(theme.panelStyle(), 12));
        statsPanel.setStyle(withRadius(theme.panelStyle(), 8));
        outerTabs.setStyle(transparentPanelStyle(theme));
        for (TabPanel tabs : groupTabs) {
            tabs.setStyle(transparentPanelStyle(theme));
        }
        innerTabs.setStyle(withRadius(theme.panelStyle(), 8));
        for (GroupBox groupBox : groupBoxes) {
            groupBox.setStyle(groupBoxStyle(theme));
        }
        if (randomizeProgressButton != null) {
            randomizeProgressButton.setStyle(withRadius(theme.primaryStyle(), 8));
            randomizeProgressButton.setIconTint(theme.onPrimary());
        }
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
        hyperlink.setFontFamily(fontFamily);
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

    private static WidgetStyle groupBoxStyle(Theme theme) {
        return new WidgetStyle(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                Color.TRANSPARENT, theme.text(), theme.textMuted(), theme.border(),
                theme.focusRing(), 10, 12, 1);
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
        lastDeltaSeconds = deltaSeconds;
        updateFrameRate(deltaSeconds);
        manager.update(deltaSeconds);
        statsPanel.setFps(fps);
        statsPanel.setVisibleWidgets(countVisibleWidgets());
    }

    /**
     * Lays out and renders the widget tree.
     */
    void render() {
        // Lay out first so the window corner reserved for the resize handle is up to date.
        window.layout(window.bounds());
        float contentWidth = Math.max(0, window.bounds().width() - window.contentInsetRight());
        float contentHeight = Math.max(0, window.bounds().height()
                - window.contentInsetTop() - window.contentInsetBottom());
        outerTabs.setSize(contentWidth, contentHeight);
        statsPanel.resizeToContent(manager.renderer(), THEME_BUTTON_SIZE);
        manager.render();
        statsPanel.setDrawCalls(backend.drawCallCount());
        benchmarkPage.recordLiveFrame(lastDeltaSeconds, backend.drawCallCount());
    }

    /**
     * Closes the manager and releases context-owned resources.
     */
    @Override
    public void close() {
        toasts.close();
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

    private final class EmptyStateStage extends Panel {
        private static final float PREFERRED_HEIGHT = 230;
        private final EmptyState content;

        private EmptyStateStage(EmptyState content) {
            this.content = content;
            add(content);
        }

        /**
         * Reports a preferred height so the centered empty state has visible travel.
         *
         * @param constraints available and required dimensions
         * @return the preferred stage size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(constraints.maxWidth(), PREFERRED_HEIGHT);
        }

        /**
         * Stretches the empty state over the whole stage.
         */
        @Override
        protected void onLayout() {
            content.layout(new Rect(0, 0, bounds().width(), bounds().height()));
        }
    }

    private final class AnchorStage extends GroupBox {
        private static final float PREFERRED_HEIGHT = 180;

        private AnchorStage() {
            setContentPadding(0);
        }

        /**
         * Reports a fixed stage height so the anchored child has visible travel.
         *
         * @param constraints available and required dimensions
         * @return the preferred stage size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(constraints.maxWidth(), PREFERRED_HEIGHT);
        }
    }

    private final class LayoutDemoBox extends Panel {
        private static final float INSET = 5;
        private final Label label;

        private LayoutDemoBox(String text, Color accent) {
            label = new Label(text);
            label.setFontFamily(fontFamily);
            label.setAutoSize(false);
            label.setAlignment(HorizontalAlignment.CENTER);
            label.setVerticalAlignment(VerticalAlignment.CENTER);
            label.setTextColor(Color.BLACK.withAlpha(0.78f));
            add(label);
            setStyle(new WidgetStyle(accent, accent, accent, accent.withAlpha(0.5f),
                    Color.BLACK, Color.BLACK.withAlpha(0.6f), Color.TRANSPARENT,
                    Color.TRANSPARENT, 6, 0, 0));
        }

        /**
         * Places the label inside the box inset.
         */
        @Override
        protected void onLayout() {
            label.layout(new Rect(INSET, INSET,
                    Math.max(0, bounds().width() - INSET * 2),
                    Math.max(0, bounds().height() - INSET * 2)));
        }

        private LayoutDemoBox alignLabel(HorizontalAlignment horizontal,
                                         VerticalAlignment vertical) {
            label.setAlignment(horizontal);
            label.setVerticalAlignment(vertical);
            return this;
        }
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

    private record ThemeChoice(String label, Theme theme) {
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
