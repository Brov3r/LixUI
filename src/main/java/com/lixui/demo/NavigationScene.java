package com.lixui.demo;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.theme.Theme;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Breadcrumbs;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.MenuBar;
import com.lixui.widget.control.ProgressBar;
import com.lixui.widget.control.Separator;
import com.lixui.widget.container.Accordion;
import com.lixui.widget.container.SplitPanel;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.overlay.ContextMenu;

/**
 * Builds the navigation scene with a menu bar, tabs, breadcrumbs, accordion, split panes and
 * pre-opened overlays for the screenshot.
 */
final class NavigationScene {
    private NavigationScene() {
    }

    static ShowcasePage create(ShowcaseKit kit) {
        Label status = kit.muted("Ready.");
        MenuBar menuBar = new MenuBar();

        Accordion accordion = createAccordion(kit);
        ShowcaseCard sidebar = kit.card();
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        Breadcrumbs.Crumb home = breadcrumbs.addCrumb("Home ", () -> status.setText("Home selected."));
        home.setIcon(ImageSource.icon("house.svg"));
        breadcrumbs.addCrumb("Components");
        sidebar.with(kit.heading("Navigator"))
                .with(ShowcaseKit.fixHeight(new ShowcaseSpacer(), 22))
                .with(breadcrumbs)
                .with(new Separator()).with(accordion).with(status);

        TabPanel tabs = new TabPanel();
        tabs.setContentPadding(14);
        tabs.addTab("Overview", overviewTab(kit));
        tabs.addTab("Members", membersTab(kit));
        tabs.addTab("Activity", activityTab(kit));
        tabs.select(0);

        Button refresh = new Button("Rebuild index");
        refresh.setMinimumSize(0, 36);
        ShowcaseTooltip tooltip = new ShowcaseTooltip(kit, "Rebuild the workspace index");
        tooltip.setVisible(false);
        kit.manager().add(tooltip);
        ShowcaseCard indexCard = kit.card().padding(14);
        indexCard.with(kit.heading("Workspace index"))
                .with(kit.wrappedMuted("The index keeps search and navigation fast. Rebuild it "
                        + "after large imports or a branch switch."))
                .with(refresh)
                .with(kit.badge("12,408 files indexed", kit.manager().context().theme().primary()));

        ShowcaseScroll notes = new ShowcaseScroll(notesContent(kit));
        ShowcaseCard notesCard = kit.card().padding(14);
        notesCard.with(kit.heading("Release notes")).with(notes);
        notes.setGrow(1);

        SplitPanel split = new SplitPanel(SplitPanel.Orientation.VERTICAL);
        split.setDividerThickness(8);
        split.setDividerPosition(0.62f);
        split.setMinimumPaneSize(110);
        split.setFirst(notesCard);
        split.setSecond(indexCard);

        HBox columns = new HBox();
        columns.setGap(16);
        columns.add(ShowcaseKit.fixWidth(sidebar, 300))
                .add(ShowcaseKit.fixWidth(tabs, 580))
                .add(ShowcaseKit.fixWidth(split, 320));

        VBox body = new VBox();
        body.setGap(12);
        body.add(ShowcaseKit.fixHeight(menuBar, 38)).add(columns);
        columns.setGrow(1);

        ContextMenu contextMenu = createContextMenu(status);
        ShowcasePage page = new ShowcasePage(kit, 3, 4, "Navigation workspace",
                "Tabs, breadcrumbs and split panes with the navigation overlays open.",
                Theme.catppuccinLatte(), Theme.nord(), false, body);
        page.onActivate(() -> {
            if (menuBar.menuCount() == 0) {
                populateMenuBar(kit, menuBar, status);
            }
            kit.manager().layout();
            Point menuOrigin = menuBar.absolutePosition();
            kit.input().pointerMoved(menuOrigin.x() + 145, menuOrigin.y() + 19);
            Point button = refresh.absolutePosition();
            Size tooltipSize = tooltip.measure(LayoutConstraints.loose(420, 120));
            tooltip.setBounds(new Rect(button.x() - tooltipSize.width() - 20,
                    button.y() + (refresh.bounds().height() - tooltipSize.height()) / 2,
                    tooltipSize.width(), tooltipSize.height()));
            tooltip.setVisible(true);
            contextMenu.open(kit.manager().context(), 690, 360);
        });
        page.onDeactivate(() -> {
            tooltip.setVisible(false);
            menuBar.clearMenus();
            contextMenu.close();
        });
        return page;
    }

    private static void populateMenuBar(ShowcaseKit kit, MenuBar bar, Label status) {
        MenuBar.Menu file = bar.addMenu("File");
        file.addItem("New workspace", () -> status.setText("New workspace requested."));
        file.addItem("Open...", () -> status.setText("Open dialog requested.")).setShortcut("Ctrl+O");
        file.addItem("Save", () -> status.setText("Workspace saved.")).setShortcut("Ctrl+S");
        file.addSeparator();
        file.addItem("Close workspace", () -> status.setText("Close requested."));
        MenuBar.Menu edit = bar.addMenu("Edit");
        edit.addItem("Undo", () -> status.setText("Undo requested.")).setShortcut("Ctrl+Z");
        edit.addItem("Redo", () -> status.setText("Redo requested.")).setShortcut("Ctrl+Y");
        edit.addSeparator();
        edit.addItem("Preferences", () -> status.setText("Preferences requested."));
        MenuBar.Menu view = bar.addMenu("View");
        view.addItem("Overview", () -> status.setText("Overview selected."));
        view.addItem("Activity", () -> status.setText("Activity selected."));
        view.addSeparator();
        view.addItem("Toggle theme", () -> status.setText("Press T to toggle the theme."));
        MenuBar.Menu help = bar.addMenu("Help");
        help.addItem("Documentation", () -> status.setText("Documentation requested."));
        help.addItem("Keyboard shortcuts", () -> status.setText("Shortcut reference requested."));
        help.addItem("About LixUI", () -> status.setText("LixUI showcase demo."));
    }

    private static ContextMenu createContextMenu(Label status) {
        ContextMenu menu = new ContextMenu();
        menu.setMinimumMenuWidth(210);
        menu.setSubmenuGap(6);
        menu.addItem("Open in new tab", () -> status.setText("Opened in a new tab."))
                .setShortcut("Ctrl+Enter");
        menu.addItem("Copy link", () -> status.setText("Link copied.")).setShortcut("Ctrl+C");
        menu.addItem("Duplicate", () -> status.setText("Item duplicated.")).setShortcut("Ctrl+D");
        menu.addSeparator();
        ContextMenu.Item move = menu.addItem("Move to", null);
        move.addChild(new ContextMenu.Item("Pinned", () -> status.setText("Moved to pinned.")));
        move.addChild(new ContextMenu.Item("Archive", () -> status.setText("Moved to archive.")));
        menu.addItem("Rename", () -> status.setText("Rename requested.")).setShortcut("F2");
        menu.addSeparator();
        ContextMenu.Item delete = menu.addItem("Delete", () -> status.setText("Delete requested."));
        delete.setShortcut("Del");
        delete.setTone(ContextMenu.Tone.DANGER);
        return menu;
    }

    private static Accordion createAccordion(ShowcaseKit kit) {
        Accordion accordion = new Accordion();
        accordion.setHeaderHeight(34);
        accordion.setExpansionDuration(0.2f);
        Accordion.Section project = accordion.addSection("Workspace",
                sectionText(kit, "Aurora design system · 24 members · 12,408 indexed files."));
        Accordion.Section permissions = accordion.addSection("Permissions",
                sectionText(kit, "Owners and editors can publish; guests can comment."));
        Accordion.Section archive = accordion.addSection("Archive",
                sectionText(kit, "Archived projects stay searchable for 90 days."));
        accordion.expand(project);
        permissions.setEnabled(true);
        archive.setEnabled(true);
        accordion.setGrow(1);
        return accordion;
    }

    private static Widget sectionText(ShowcaseKit kit, String text) {
        VBox content = new VBox();
        content.setPadding(4);
        content.add(kit.wrappedMuted(text));
        return content;
    }

    private static Widget overviewTab(ShowcaseKit kit) {
        VBox content = new VBox();
        content.setGap(12);
        content.add(kit.heading("Aurora design system"))
                .add(kit.wrappedMuted("A shared component library for the web console, the "
                        + "desktop client and the documentation site. The overview collects the "
                        + "release state and the work that is still in flight."));
        HBox badges = new HBox();
        badges.setGap(8);
        badges.add(kit.badge("Stable", kit.manager().context().theme().primary()));
        badges.add(kit.badge("v2.4.1", Color.hex("#4F8EF7")));
        badges.add(kit.badge("12 open issues", Color.hex("#C26A17")));
        content.add(badges);
        content.add(new Separator());
        content.add(kit.heading("Release progress"));
        ProgressBar progress = new ProgressBar(0.72f);
        progress.setMinimumSize(0, 14);
        progress.setMaximumSize(10_000, 14);
        progress.setFillGradient(Color.hex("#4F8EF7"), Color.hex("#9BD1C1"));
        content.add(progress);
        content.add(kit.wrappedMuted("72% of the milestone is complete. The remaining work is "
                + "focused on accessibility audits and the migration guide."));
        content.add(new Separator());
        content.add(kit.heading("Next up"))
                .add(kit.muted("· Publish the color token migration guide"))
                .add(kit.muted("· Review keyboard navigation for the data grid"))
                .add(kit.muted("· Ship the compact density preset"));
        content.add(kit.spacer());
        return content;
    }

    private static Widget membersTab(ShowcaseKit kit) {
        VBox content = new VBox();
        content.setGap(10);
        content.add(kit.heading("Members"))
                .add(kit.wrappedMuted("24 people have access to this workspace. Owners manage "
                        + "billing; editors publish components and documentation."));
        content.add(member(kit, "Aria Snow", "Owner"))
                .add(member(kit, "Bran Voss", "Editor"))
                .add(member(kit, "Cyra Idris", "Editor"))
                .add(member(kit, "Dain Holt", "Reviewer"))
                .add(member(kit, "Elara Wynn", "Guest"));
        content.add(kit.spacer());
        return content;
    }

    private static Widget member(ShowcaseKit kit, String name, String role) {
        HBox row = new HBox();
        row.setGap(12);
        row.setCrossAlignment(HBox.CrossAlignment.CENTER);
        row.add(kit.text(name)).add(kit.spacer()).add(kit.badge(role, Color.hex("#4E9A6A")));
        return row;
    }

    private static Widget activityTab(ShowcaseKit kit) {
        VBox content = new VBox();
        content.setGap(10);
        content.add(kit.heading("Activity"))
                .add(kit.wrappedMuted("Recent changes from every member of the workspace."));
        content.add(activity(kit, "Aria published Button v2.4", "12 minutes ago"))
                .add(activity(kit, "Bran opened a review for DataGrid", "40 minutes ago"))
                .add(activity(kit, "Cyra updated the color tokens", "2 hours ago"))
                .add(activity(kit, "Dain archived the legacy icons", "Yesterday"));
        content.add(kit.spacer());
        return content;
    }

    private static Widget activity(ShowcaseKit kit, String text, String time) {
        HBox row = new HBox();
        row.setGap(10);
        row.setCrossAlignment(HBox.CrossAlignment.CENTER);
        row.add(kit.text(text)).add(kit.spacer()).add(kit.muted(time));
        return row;
    }

    private static Widget notesContent(ShowcaseKit kit) {
        VBox content = new VBox();
        content.setGap(10);
        content.add(kit.heading("v2.4.1")).add(kit.wrappedMuted("Tabs keep their scroll offset "
                + "when a tab is moved or closed. The split pane divider now respects the minimum "
                + "pane size on both axes."));
        content.add(kit.heading("v2.4.0")).add(kit.wrappedMuted("Added the navigation overlay "
                + "stack, breadcrumb overflow and a keyboard focus ring for menu titles."));
        content.add(kit.heading("v2.3.2")).add(kit.wrappedMuted("Fixed tooltip placement near the "
                + "viewport edges and clamped context menus to the active monitor."));
        content.add(kit.heading("v2.3.1")).add(kit.wrappedMuted("Reduced the cost of repeated "
                + "icon uploads and made the theme switch preserve the current selection."));
        return content;
    }
}
