package com.lixui.demo;

import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Label;
import com.lixui.widget.container.ImageGallery;
import com.lixui.widget.container.TabPanel;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.VBox;

/**
 * Builds the image gallery demos shown on the gallery tab.
 */
final class ImageGalleryDemo {
    private static final String[][] ICONS = {
            {"broadsword.svg", "Broadsword"},
            {"bow.svg", "Oak Bow"},
            {"potion.svg", "Healing Potion"},
            {"shield.svg", "Tower Shield"},
            {"chest.svg", "Storage Chest"},
            {"star.svg", "Lucky Star"},
            {"rainbow.svg", "Rainbow"},
            {"image.svg", "Image file"},
            {"grid.svg", "Grid layout"},
            {"clock.svg", "Clock"},
            {"calendar.svg", "Calendar"},
            {"user-list.svg", "Party list"}
    };

    private ImageGalleryDemo() {
    }

    /**
     * Creates the single- and multi-selection gallery examples.
     *
     * @param fontFamily demo font family, or null when no system font is available
     * @return a tab panel with both gallery variants
     */
    static Widget create(FontFamily fontFamily) {
        TabPanel tabs = new TabPanel();
        tabs.addTab("Icons", createIconGallery(fontFamily));
        tabs.addTab("Cards", createCardGallery(fontFamily));
        return tabs;
    }

    private static Widget createIconGallery(FontFamily fontFamily) {
        ImageGallery gallery = new ImageGallery();
        gallery.setGrow(1);
        gallery.setColumns(3);
        gallery.setThumbnailSize(84);
        for (String[] icon : ICONS) {
            gallery.addItem(ImageSource.icon(icon[0]), icon[1]);
        }

        Label status = label(fontFamily, "Select an item, then press Enter to open it.");
        status.setFontStyle(FontStyle.ITALIC);
        gallery.setSelectionListener(indices -> status.setText(indices.isEmpty()
                ? "Select an item, then press Enter to open it."
                : "Selected: " + gallery.items().get(indices.get(0)).caption()));
        gallery.setActivationListener(items -> {
            if (!items.isEmpty()) {
                status.setText("Opened: " + items.get(0).caption());
            }
        });

        return pane(fontFamily, "Icon gallery - single selection",
                "Click a thumbnail to select it. Enter activates the selected item.",
                gallery, status);
    }

    private static Widget createCardGallery(FontFamily fontFamily) {
        ImageGallery gallery = new ImageGallery();
        gallery.setGrow(1);
        gallery.setColumns(2);
        gallery.setThumbnailSize(110);
        gallery.setSelectionMode(ImageGallery.SelectionMode.MULTIPLE);
        for (int index = 0; index < 24; index++) {
            String[] icon = ICONS[index % ICONS.length];
            gallery.addItem(ImageSource.icon(icon[0]), icon[1] + " #" + (index + 1));
        }

        Label status = label(fontFamily, "Ctrl toggles, Shift selects a range, Enter opens.");
        status.setFontStyle(FontStyle.ITALIC);
        gallery.setSelectionListener(indices -> status.setText(indices.isEmpty()
                ? "Ctrl toggles, Shift selects a range, Enter opens."
                : "Selected " + indices.size() + " item(s)."));
        gallery.setActivationListener(items -> status.setText(items.isEmpty()
                ? "Nothing to open." : "Opened " + items.size() + " item(s)."));

        return pane(fontFamily, "Card gallery - multiple selection",
                "Scroll the grid, then use Ctrl or Shift with clicks. Enter opens the selection.",
                gallery, status);
    }

    private static Widget pane(FontFamily fontFamily, String caption, String hint,
                               ImageGallery gallery, Label status) {
        VBox pane = new VBox();
        pane.setPadding(8);
        pane.setGap(6);
        pane.add(caption(fontFamily, caption))
                .add(label(fontFamily, hint))
                .add(gallery)
                .add(status);
        return pane;
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
