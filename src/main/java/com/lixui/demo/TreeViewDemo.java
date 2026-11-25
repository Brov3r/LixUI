package com.lixui.demo;

import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Label;
import com.lixui.widget.container.SplitPanel;
import com.lixui.widget.container.TreeView;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the tree view demo shown on the Tree page of the Data tab.
 */
final class TreeViewDemo {
    private static final String[] REGIONS = {"Emberfall", "Silverwood", "Hollow Coast"};
    private static final String[][] LOCATIONS = {
            {"Ashen Gate", "Old Forge", "Cinder Market"},
            {"Glass Orchard", "Moonwell", "Rootway"},
            {"Tidewatch", "Salt Caves", "Sunken Bell"}
    };

    private TreeViewDemo() {
    }

    /**
     * Creates the interactive and compact trees above the icon and disabled branch example.
     *
     * @param fontFamily demo font family, or null when no system font is available
     * @return the tree demo page
     */
    static Widget create(FontFamily fontFamily) {
        SplitPanel examples = new SplitPanel();
        examples.setDividerThickness(8);
        examples.setDividerPosition(0.6f);
        examples.setMinimumPaneSize(150);
        examples.setGrow(1);
        examples.setFirst(createInteractivePane(fontFamily));
        examples.setSecond(createCompactPane(fontFamily));

        SplitPanel split = new SplitPanel(SplitPanel.Orientation.VERTICAL);
        split.setDividerThickness(8);
        split.setDividerPosition(0.62f);
        split.setMinimumPaneSize(150);
        split.setGrow(1);
        split.setFirst(examples);
        split.setSecond(createIconPane(fontFamily));

        VBox page = new VBox();
        page.setPadding(8);
        page.add(split);
        return page;
    }

    private static Widget createInteractivePane(FontFamily fontFamily) {
        TreeView tree = new TreeView();
        tree.setGrow(1);
        tree.setIndent(18);

        TreeView.Node world = tree.addRoot("World", true);
        for (int index = 0; index < REGIONS.length; index++) {
            TreeView.Node region = world.addChild(REGIONS[index]);
            for (String location : LOCATIONS[index]) {
                TreeView.Node place = region.addChild(location);
                place.addChild("NPCs");
                place.addChild("Quests");
            }
            region.setExpanded(true);
        }

        TreeView.Node party = tree.addRoot("Party", true);
        for (String member : List.of("Aria - Ranger", "Bran - Knight", "Cyra - Mage")) {
            party.addChild(member);
        }

        TreeView.Node inventory = tree.addRoot("Inventory");
        for (String section : List.of("Weapons", "Armor", "Consumables", "Quest items")) {
            inventory.addChild(section);
        }

        Label status = label(fontFamily, "Select a node to inspect its path.");
        status.setFontStyle(FontStyle.ITALIC);
        tree.setSelectionListener(node -> status.setText(node == null
                ? "No node selected." : "Selected: " + path(node)));

        CheckBox animate = new CheckBox("Animate branch height");
        animate.setChecked(true);
        animate.setOnChange(checked -> tree.setExpansionDuration(checked ? 0.2f : 0));

        VBox pane = new VBox();
        pane.setPadding(8);
        pane.setGap(6);
        pane.add(caption(fontFamily, "World tree - interactive, animated branches"))
                .add(label(fontFamily, "Click arrows to expand and labels to select. "
                        + "Right/Left expand, collapse or move between parent and child."))
                .add(tree)
                .add(animate)
                .add(status);
        return pane;
    }

    private static Widget createIconPane(FontFamily fontFamily) {
        TreeView tree = new TreeView();
        tree.setGrow(1);
        tree.setRowHeight(26);
        tree.setIndent(16);
        tree.setExpansionDuration(0);

        TreeView.Node project = tree.addRoot("LixUI", true);
        project.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node assets = project.addChild("assets");
        assets.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node icons = assets.addChild("icons");
        icons.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node folderIcon = icons.addChild("folder.svg");
        folderIcon.setIcon(ImageSource.icon("file.svg"));
        TreeView.Node fileIcon = icons.addChild("file.svg");
        fileIcon.setIcon(ImageSource.icon("file.svg"));

        TreeView.Node source = project.addChild("src");
        source.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node main = source.addChild("main");
        main.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node sourceFile = main.addChild("TreeView.java");
        sourceFile.setIcon(ImageSource.icon("file.svg"));
        TreeView.Node tests = project.addChild("tests");
        tests.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node testFile = tests.addChild("TreeViewTest.java");
        testFile.setIcon(ImageSource.icon("file.svg"));

        TreeView.Node build = project.addChild("build");
        build.setIcon(ImageSource.icon("lock.svg"));
        build.setDisabled(true);
        TreeView.Node classes = build.addChild("classes");
        classes.setIcon(ImageSource.icon("folder.svg"));
        TreeView.Node libs = build.addChild("libs");
        libs.setIcon(ImageSource.icon("folder.svg"));
        build.setExpanded(true);

        TreeView.Node secrets = project.addChild("local.properties");
        secrets.setIcon(ImageSource.icon("lock.svg"));
        secrets.setDisabled(true);

        assets.setExpanded(true);
        icons.setExpanded(true);
        source.setExpanded(true);
        main.setExpanded(true);
        tests.setExpanded(true);

        Label status = label(fontFamily, "");
        status.setFontStyle(FontStyle.ITALIC);
        tree.setSelectionListener(node -> status.setText(node == null
                ? "No node selected." : "Selected: " + path(node)));
        tree.select(sourceFile);

        VBox pane = new VBox();
        pane.setPadding(8);
        pane.setGap(6);
        pane.add(caption(fontFamily, "Project explorer - node icons and disabled branches"))
                .add(label(fontFamily, "Folder, file and lock icons sit before the label. "
                        + "build and local.properties are disabled: they and their children are "
                        + "muted and ignore clicks and keys."))
                .add(tree)
                .add(status);
        return pane;
    }

    private static Widget createCompactPane(FontFamily fontFamily) {
        TreeView tree = new TreeView();
        tree.setGrow(1);
        tree.setRowHeight(24);
        tree.setIndent(14);
        tree.setExpansionDuration(0);

        TreeView.Node project = tree.addRoot("LixUI", true);
        TreeView.Node source = project.addChild("src");
        TreeView.Node main = source.addChild("main");
        TreeView.Node java = main.addChild("java");
        TreeView.Node widgets = java.addChild("com.lixui.widget");
        TreeView.Node container = widgets.addChild("container");
        TreeView.Node virtualList = container.addChild("VirtualList.java");
        container.addChild("TreeView.java");
        container.addChild("Table.java");
        container.addChild("ScrollPanel.java");
        TreeView.Node control = widgets.addChild("control");
        control.addChild("Input.java");
        control.addChild("Dropdown.java");
        control.addChild("Label.java");
        TreeView.Node demo = java.addChild("com.lixui.demo");
        demo.addChild("TreeViewDemo.java");
        demo.addChild("VirtualListDemo.java");
        demo.addChild("DemoWidgetScene.java");
        TreeView.Node tests = project.addChild("src/test");
        TreeView.Node testJava = tests.addChild("java");
        testJava.addChild("VirtualListTest.java");
        testJava.addChild("TreeViewTest.java");

        source.setExpanded(true);
        main.setExpanded(true);
        java.setExpanded(true);
        widgets.setExpanded(true);
        container.setExpanded(true);
        control.setExpanded(true);
        demo.setExpanded(true);
        tests.setExpanded(true);
        testJava.setExpanded(true);

        Label status = label(fontFamily, "");
        status.setFontStyle(FontStyle.ITALIC);
        tree.setSelectionListener(node -> status.setText(node == null
                ? "No node selected." : "Selected: " + path(node)));
        tree.select(virtualList);

        VBox pane = new VBox();
        pane.setPadding(8);
        pane.setGap(6);
        pane.add(caption(fontFamily, "Project tree - compact, animation off"))
                .add(label(fontFamily, "Narrow fixed layout with an initial selection. "
                        + "Selection follows the node when rows shift."))
                .add(tree)
                .add(status);
        return pane;
    }

    private static String path(TreeView.Node node) {
        List<String> parts = new ArrayList<>();
        for (TreeView.Node current = node; current != null; current = current.parent()) {
            parts.add(current.label());
        }
        StringBuilder result = new StringBuilder();
        for (int index = parts.size() - 1; index >= 0; index--) {
            if (!result.isEmpty()) {
                result.append(" / ");
            }
            result.append(parts.get(index));
        }
        return result.toString();
    }

    private static Label caption(FontFamily family, String text) {
        Label label = label(family, text);
        label.setFontStyle(FontStyle.BOLD);
        return label;
    }

    private static Label label(FontFamily family, String text) {
        Label label = new Label(text);
        label.setFontFamily(family);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }
}
