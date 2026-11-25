package com.lixui.demo;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.render.Texture;
import com.lixui.text.FontFamily;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Label;
import com.lixui.widget.layout.Flow;
import com.lixui.widget.layout.VBox;
import com.lixui.widget.overlay.RadialMenu;

import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the demo page for radial menus.
 * The page opens three differently sized menus with icons and adds a corner-open check for
 * viewport clamping.
 */
final class RadialMenuDemoPage {
    private static final float CONTENT_PADDING = 10;
    private static final float CONTENT_GAP = 10;
    private static final float ANIMATION_SECONDS = 0.22f;
    private static final float ICON_SIZE = 18;

    private final FontFamily fontFamily;
    private final IconBinder iconBinder;

    /**
     * Creates the demo page builder.
     *
     * @param fontFamily font family used by page labels, or null for the inherited font
     * @param iconBinder callback that binds a bundled icon at the requested logical size
     */
    RadialMenuDemoPage(FontFamily fontFamily, IconBinder iconBinder) {
        this.fontFamily = fontFamily;
        this.iconBinder = iconBinder;
    }

    /**
     * Builds the demo page.
     *
     * @return the page content
     */
    Widget build() {
        Label status = caption("Open a menu, then click a sector, the center, press Esc or click "
                + "outside to dismiss it.");
        RadialMenu file = createFileMenu(status);
        RadialMenu edit = createEditMenu(status);
        RadialMenu tools = createToolsMenu(status);
        RadialMenu longLabels = createLongLabelMenu(status);
        List<RadialMenu> menus = List.of(file, edit, tools, longLabels);

        Button fileButton = new Button("File actions");
        fileButton.setOnAction(() -> openAt(fileButton, file));
        Button editButton = new Button("Edit actions");
        editButton.setOnAction(() -> openAt(editButton, edit));
        Button toolsButton = new Button("Tools");
        toolsButton.setOnAction(() -> openAt(toolsButton, tools));
        Button longButton = new Button("Long labels");
        longButton.setOnAction(() -> openAt(longButton, longLabels));
        Button cornerButton = new Button("Open near corner");
        cornerButton.setOnAction(() -> {
            UiContext context = cornerButton.context();
            if (context != null) {
                Rect viewport = context.viewport();
                file.open(context, viewport.right() - 1, viewport.y() + 1);
            }
        });

        Flow buttons = new Flow();
        buttons.setGap(8);
        buttons.add(fileButton).add(editButton).add(toolsButton).add(longButton).add(cornerButton);

        CheckBox outside = new CheckBox("Close on outside click");
        outside.setChecked(true);
        outside.setOnChange(checked -> {
            for (RadialMenu menu : menus) {
                menu.setCloseOnOutsideClick(checked);
            }
        });

        CheckBox animate = new CheckBox("Animate open and close");
        animate.setChecked(true);
        animate.setOnChange(checked -> {
            for (RadialMenu menu : menus) {
                menu.setAnimationDuration(checked ? ANIMATION_SECONDS : 0);
            }
        });

        Flow controls = new Flow();
        controls.setGap(10);
        controls.add(outside).add(animate);

        VBox page = new VBox();
        page.setPadding(CONTENT_PADDING);
        page.setGap(CONTENT_GAP);
        page.add(caption("File has six icon sectors around a titled center, Edit has eight "
                        + "compact sectors, Tools has four large sectors, and Long labels uses "
                        + "narrow sectors with ellipsized continuous tokens. The center closes "
                        + "the menu; the last button opens the file menu at the viewport edge."))
                .add(buttons)
                .add(controls)
                .add(status);
        return page;
    }

    private RadialMenu createFileMenu(Label status) {
        RadialMenu menu = new RadialMenu();
        menu.setRadius(140);
        menu.setInnerRadiusRatio(0.4f);
        menu.setIconSize(ICON_SIZE);
        menu.setTitle("File");
        add(menu, "New", "file.svg", status);
        add(menu, "Open", "folder.svg", status);
        add(menu, "Save", "save.svg", status);
        add(menu, "Share", "share.svg", status);
        add(menu, "Rename", "pen.svg", status);
        add(menu, "Delete", "trash.svg", status);
        return menu;
    }

    private RadialMenu createEditMenu(Label status) {
        RadialMenu menu = new RadialMenu();
        menu.setRadius(128);
        menu.setInnerRadiusRatio(0.32f);
        menu.setIconSize(ICON_SIZE);
        menu.setTitle("Edit");
        add(menu, "Copy", "copy.svg", status);
        add(menu, "Paste", "paste.svg", status);
        add(menu, "Cut", "scissors.svg", status);
        add(menu, "Undo", "undo.svg", status);
        add(menu, "Redo", "redo.svg", status);
        add(menu, "Find", "search.svg", status);
        add(menu, "Settings", "settings.svg", status);
        add(menu, "Help", "question-mark.svg", status);
        return menu;
    }

    private RadialMenu createToolsMenu(Label status) {
        RadialMenu menu = new RadialMenu();
        menu.setRadius(108);
        menu.setInnerRadiusRatio(0.28f);
        menu.setIconSize(ICON_SIZE);
        menu.setTitle("Tools");
        add(menu, "Brush", "paintbrush.svg", status);
        add(menu, "Palette", "palette.svg", status);
        add(menu, "Wand", "wand.svg", status);
        add(menu, "Pick", "target.svg", status);
        return menu;
    }

    private RadialMenu createLongLabelMenu(Label status) {
        RadialMenu menu = new RadialMenu();
        menu.setRadius(124);
        menu.setInnerRadiusRatio(0.42f);
        menu.setIconSize(ICON_SIZE);
        menu.setTitle("Long");
        add(menu, "Short", "file.svg", status);
        add(menu, "SupercalifragilisticexpialidociousTokenWithoutSpaces0123456789",
                "pen.svg", status);
        add(menu, "A very long label that must stay inside its sector",
                "folder.svg", status);
        add(menu, "AnotherIrritatinglyLongContinuousTokenThatCannotWrap",
                "save.svg", status);
        add(menu, "Medium label", "share.svg", status);
        add(menu, "Done", "tick.svg", status);
        return menu;
    }

    private RadialMenu.Item add(RadialMenu menu, String label, String iconName, Label status) {
        RadialMenu.Item item = menu.addItem(new RadialMenu.Item(label,
                () -> status.setText("Radial action: " + label + ".")));
        iconBinder.bind(iconName, ICON_SIZE, item::setIcon);
        return item;
    }

    private void openAt(Widget anchor, RadialMenu menu) {
        UiContext context = anchor.context();
        if (context == null) {
            return;
        }
        Point position = anchor.absolutePosition();
        menu.open(context, position.x() + anchor.bounds().width() / 2,
                position.y() + anchor.bounds().height() / 2);
    }

    private Label caption(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }

    /**
     * Binds a bundled icon to a menu item at the requested logical size.
     */
    interface IconBinder {
        /**
         * Binds the icon by resource name.
         *
         * @param name bundled icon resource name
         * @param size logical icon edge length used for density-aware loading
         * @param assign consumer that receives the loaded texture
         */
        void bind(String name, float size, Consumer<Texture> assign);
    }
}
