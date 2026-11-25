package com.lixui.demo;

import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.MenuBar;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.overlay.ContextMenu;

/**
 * Builds the menu bar page with shortcuts, disabled rows and submenus.
 */
final class DemoMenuPage {
    private static final float BAR_HEIGHT = 34;

    private final FontFamily fontFamily;

    /**
     * Creates a page builder.
     *
     * @param fontFamily font family used by captions and the status label
     */
    DemoMenuPage(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Creates the menu bar page content.
     *
     * @return a scrollable page with a themed menu bar
     */
    Widget create() {
        Label status = new Label("Open a menu and pick a command.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        MenuBar bar = new MenuBar();
        bar.setMinimumSize(0, BAR_HEIGHT);
        createFileMenu(bar, status);
        createEditMenu(bar, status);
        createViewMenu(bar, status);
        createHelpMenu(bar, status);

        MenuBar compact = new MenuBar();
        compact.setMinimumSize(0, 28);
        createCompactMenus(compact, status);
        HBox compactRow = new HBox();
        compactRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        compactRow.add(compact);

        VBox page = new VBox();
        page.setPadding(10);
        page.setGap(10);
        page.add(caption("Menu bar: hover switches menus, outside press and Escape close them"))
                .add(bar)
                .add(caption("Compact bar: short menus with shortcuts and a disabled row"))
                .add(compactRow)
                .add(caption("Dropdowns reuse the context menu: shortcuts, disabled rows "
                        + "and nested submenus"))
                .add(status);
        return new DemoScrollPage(page);
    }

    private void createCompactMenus(MenuBar bar, Label status) {
        MenuBar.Menu actions = bar.addMenu("Actions");
        item(actions, "New", "Ctrl+N", () -> status.setText("Compact: new."));
        item(actions, "Open", "Ctrl+O", () -> status.setText("Compact: open."));
        item(actions, "Save", "Ctrl+S", () -> status.setText("Compact: saved."));
        actions.addSeparator();

        ContextMenu.Item autosave = actions.addItem("Autosave", null);
        autosave.setShortcut("Ctrl+Shift+A");
        autosave.setAction(() -> status.setText("Compact: autosave requested."));
        ContextMenu.Item sync = actions.addItem("Cloud sync", null);
        sync.setShortcut("Ctrl+Shift+C");
        sync.setEnabled(false);

        MenuBar.Menu options = bar.addMenu("Options");
        ContextMenu.Item hints = options.addItem("Show hints", null);
        hints.setShortcut("Ctrl+H");
        hints.setAction(() -> status.setText("Compact: hints shown."));
        ContextMenu.Item density = options.addItem("Compact density", null);
        density.setShortcut("Ctrl+D");
        density.setAction(() -> status.setText("Compact: compact density."));

        MenuBar.Menu help = bar.addMenu("Help");
        item(help, "Keyboard map", "F1", () -> status.setText("Compact: keyboard map."));
        item(help, "About", null, () -> status.setText("Compact: about."));
    }

    private void createFileMenu(MenuBar bar, Label status) {
        MenuBar.Menu file = bar.addMenu("File");
        item(file, "New project", "Ctrl+N", () -> status.setText("File: new project."));
        item(file, "Open...", "Ctrl+O", () -> status.setText("File: open project."));
        item(file, "Save", "Ctrl+S", () -> status.setText("File: saved."));
        item(file, "Save as...", "Ctrl+Shift+S", () -> status.setText("File: save as."));
        file.addSeparator();

        ContextMenu.Item recent = file.addItem("Recent", null);
        recent.addChild(new ContextMenu.Item("quests.json",
                () -> status.setText("Recent: opened quests.json.")));
        recent.addChild(new ContextMenu.Item("notes.md",
                () -> status.setText("Recent: opened notes.md.")));
        ContextMenu.Item backup = recent.addChild(new ContextMenu.Item("recovery.bak", null));
        backup.setEnabled(false);

        file.addSeparator();
        ContextMenu.Item quit = item(file, "Quit", "Alt+F4",
                () -> status.setText("File: quit requested."));
        quit.setTone(ContextMenu.Tone.DANGER);
    }

    private void createEditMenu(MenuBar bar, Label status) {
        MenuBar.Menu edit = bar.addMenu("Edit");
        item(edit, "Undo", "Ctrl+Z", () -> status.setText("Edit: undo."));
        item(edit, "Redo", "Ctrl+Y", () -> status.setText("Edit: redo."));
        edit.addSeparator();
        item(edit, "Cut", "Ctrl+X", () -> status.setText("Edit: cut."));
        item(edit, "Copy", "Ctrl+C", () -> status.setText("Edit: copy."));
        item(edit, "Paste", "Ctrl+V", () -> status.setText("Edit: paste."));
        edit.addSeparator();

        ContextMenu.Item grid = edit.addItem("Show grid", null);
        grid.setAction(() -> status.setText("Edit: grid shown."));

        ContextMenu.Item wrap = edit.addItem("Word wrap", null);
        wrap.setAction(() -> status.setText("Edit: word wrap on."));
    }

    private void createViewMenu(MenuBar bar, Label status) {
        MenuBar.Menu view = bar.addMenu("View");

        ContextMenu.Item layout = view.addItem("Layout", null);
        layout.addChild(new ContextMenu.Item("Compact",
                () -> status.setText("View: compact layout.")));
        layout.addChild(new ContextMenu.Item("Comfortable",
                () -> status.setText("View: comfortable layout.")));
        layout.addChild(new ContextMenu.Item("Spacious",
                () -> status.setText("View: spacious layout.")));

        ContextMenu.Item theme = view.addItem("Theme", null);
        theme.addChild(new ContextMenu.Item("Dark",
                () -> status.setText("View: dark theme.")));
        theme.addChild(new ContextMenu.Item("Light",
                () -> status.setText("View: light theme.")));
    }

    private void createHelpMenu(MenuBar bar, Label status) {
        MenuBar.Menu help = bar.addMenu("Help");
        item(help, "Documentation", "F1", () -> status.setText("Help: documentation."));
        item(help, "About LixUI", null, () -> status.setText("Help: LixUI demo menu bar."));
    }

    private ContextMenu.Item item(MenuBar.Menu menu, String label, String shortcut,
                                  Runnable action) {
        ContextMenu.Item item = menu.addItem(label, action);
        item.setShortcut(shortcut);
        return item;
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setFontStyle(FontStyle.BOLD);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }
}
