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
 * Builds the menu bar page with dropdown icons, shortcuts, checked rows and submenus.
 */
final class DemoMenuPage {
    private static final float BAR_HEIGHT = 34;

    private final FontFamily fontFamily;
    private final DemoIconBinder iconBinder;

    /**
     * Creates a page builder.
     *
     * @param fontFamily font family used by captions and the status label
     * @param iconBinder callback that loads a bundled icon at the requested size
     */
    DemoMenuPage(FontFamily fontFamily, DemoIconBinder iconBinder) {
        this.fontFamily = fontFamily;
        this.iconBinder = iconBinder;
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
                .add(caption("Compact bar: short icon-free menus with shortcuts, checked commands "
                        + "and a disabled row"))
                .add(compactRow)
                .add(caption("Dropdowns reuse the context menu: icons, shortcuts, check marks, "
                        + "disabled rows and nested submenus"))
                .add(status);
        return new DemoScrollPage(page);
    }

    private void createCompactMenus(MenuBar bar, Label status) {
        MenuBar.Menu actions = bar.addMenu("Actions");
        item(actions, "New", null, "Ctrl+N", () -> status.setText("Compact: new."));
        item(actions, "Open", null, "Ctrl+O", () -> status.setText("Compact: open."));
        item(actions, "Save", null, "Ctrl+S", () -> status.setText("Compact: saved."));
        actions.addSeparator();

        ContextMenu.Item autosave = actions.addItem("Autosave", null);
        autosave.setShortcut("Ctrl+Shift+A");
        autosave.setChecked(true);
        autosave.setAction(() -> {
            autosave.setChecked(!autosave.isChecked());
            status.setText("Compact: autosave " + (autosave.isChecked() ? "on." : "off."));
        });
        ContextMenu.Item sync = actions.addItem("Cloud sync", null);
        sync.setShortcut("Ctrl+Shift+C");
        sync.setEnabled(false);

        MenuBar.Menu options = bar.addMenu("Options");
        ContextMenu.Item hints = options.addItem("Show hints", null);
        hints.setShortcut("Ctrl+H");
        hints.setChecked(true);
        hints.setAction(() -> {
            hints.setChecked(!hints.isChecked());
            status.setText("Compact: hints " + (hints.isChecked() ? "shown." : "hidden."));
        });
        ContextMenu.Item density = options.addItem("Compact density", null);
        density.setShortcut("Ctrl+D");
        density.setAction(() -> {
            density.setChecked(!density.isChecked());
            status.setText("Compact: density "
                    + (density.isChecked() ? "compact." : "comfortable."));
        });

        MenuBar.Menu help = bar.addMenu("Help");
        item(help, "Keyboard map", null, "F1",
                () -> status.setText("Compact: keyboard map."));
        item(help, "About", null, null, () -> status.setText("Compact: about."));
    }

    private void createFileMenu(MenuBar bar, Label status) {
        MenuBar.Menu file = bar.addMenu("File");
        item(file, "New project", "file.svg", "Ctrl+N",
                () -> status.setText("File: new project."));
        item(file, "Open...", "open-the-door.svg", "Ctrl+O",
                () -> status.setText("File: open project."));
        item(file, "Save", "save.svg", "Ctrl+S",
                () -> status.setText("File: saved."));
        item(file, "Save as...", "export.svg", "Ctrl+Shift+S",
                () -> status.setText("File: save as."));
        file.addSeparator();

        ContextMenu.Item recent = file.addItem("Recent", null);
        icon(file, recent, "book.svg");
        ContextMenu.Item quests = recent.addChild(new ContextMenu.Item("quests.json",
                () -> status.setText("Recent: opened quests.json.")));
        quests.setChecked(true);
        icon(file, quests, "book.svg");
        ContextMenu.Item notes = recent.addChild(new ContextMenu.Item("notes.md",
                () -> status.setText("Recent: opened notes.md.")));
        icon(file, notes, "book.svg");
        ContextMenu.Item backup = recent.addChild(new ContextMenu.Item("recovery.bak", null));
        backup.setEnabled(false);
        backup.setChecked(true);
        icon(file, backup, "book.svg");

        file.addSeparator();
        ContextMenu.Item quit = item(file, "Quit", "power-switch.svg", "Alt+F4",
                () -> status.setText("File: quit requested."));
        quit.setTone(ContextMenu.Tone.DANGER);
    }

    private void createEditMenu(MenuBar bar, Label status) {
        MenuBar.Menu edit = bar.addMenu("Edit");
        item(edit, "Undo", "undo.svg", "Ctrl+Z", () -> status.setText("Edit: undo."));
        item(edit, "Redo", "redo.svg", "Ctrl+Y", () -> status.setText("Edit: redo."));
        edit.addSeparator();
        item(edit, "Cut", "scissors.svg", "Ctrl+X", () -> status.setText("Edit: cut."));
        item(edit, "Copy", "copy.svg", "Ctrl+C", () -> status.setText("Edit: copy."));
        item(edit, "Paste", "paste.svg", "Ctrl+V", () -> status.setText("Edit: paste."));
        edit.addSeparator();

        ContextMenu.Item grid = edit.addItem("Show grid", null);
        grid.setChecked(true);
        icon(edit, grid, "grid.svg");
        grid.setAction(() -> {
            grid.setChecked(!grid.isChecked());
            status.setText("Edit: grid " + (grid.isChecked() ? "shown." : "hidden."));
        });

        ContextMenu.Item wrap = edit.addItem("Word wrap", null);
        icon(edit, wrap, "text.svg");
        wrap.setAction(() -> {
            wrap.setChecked(!wrap.isChecked());
            status.setText("Edit: word wrap " + (wrap.isChecked() ? "on." : "off."));
        });
    }

    private void createViewMenu(MenuBar bar, Label status) {
        MenuBar.Menu view = bar.addMenu("View");

        ContextMenu.Item layout = view.addItem("Layout", null);
        icon(view, layout, "grid.svg");
        ContextMenu.Item compact = layout.addChild(new ContextMenu.Item("Compact",
                () -> status.setText("View: compact layout.")));
        icon(view, compact, "grid.svg");
        ContextMenu.Item comfortable = layout.addChild(new ContextMenu.Item("Comfortable",
                () -> status.setText("View: comfortable layout.")));
        comfortable.setChecked(true);
        icon(view, comfortable, "grid.svg");
        ContextMenu.Item spacious = layout.addChild(new ContextMenu.Item("Spacious",
                () -> status.setText("View: spacious layout.")));
        icon(view, spacious, "grid.svg");

        ContextMenu.Item theme = view.addItem("Theme", null);
        icon(view, theme, "palette.svg");
        ContextMenu.Item dark = theme.addChild(new ContextMenu.Item("Dark",
                () -> status.setText("View: dark theme.")));
        dark.setChecked(true);
        icon(view, dark, "palette.svg");
        ContextMenu.Item light = theme.addChild(new ContextMenu.Item("Light",
                () -> status.setText("View: light theme.")));
        icon(view, light, "palette.svg");
    }

    private void createHelpMenu(MenuBar bar, Label status) {
        MenuBar.Menu help = bar.addMenu("Help");
        item(help, "Documentation", "book.svg", "F1",
                () -> status.setText("Help: documentation."));
        item(help, "About LixUI", "info.svg", null,
                () -> status.setText("Help: LixUI demo menu bar."));
    }

    private ContextMenu.Item item(MenuBar.Menu menu, String label, String icon,
                                  String shortcut, Runnable action) {
        ContextMenu.Item item = menu.addItem(label, action);
        if (icon != null) {
            icon(menu, item, icon);
        }
        item.setShortcut(shortcut);
        return item;
    }

    private void icon(MenuBar.Menu menu, ContextMenu.Item item, String icon) {
        iconBinder.bind(icon, menu.iconSize(), item::setIcon);
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
