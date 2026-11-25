package com.lixui.demo;

import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Breadcrumbs;
import com.lixui.widget.control.Label;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

/**
 * Builds the breadcrumbs page with full, constrained, short and icon trails.
 */
final class DemoBreadcrumbsPage {
    private static final float BAR_HEIGHT = 30;
    private static final float NARROW_WIDTH = 230;

    private final FontFamily fontFamily;

    /**
     * Creates a page builder.
     *
     * @param fontFamily font family used by captions and the status label
     */
    DemoBreadcrumbsPage(FontFamily fontFamily) {
        this.fontFamily = fontFamily;
    }

    /**
     * Creates the breadcrumbs page content.
     *
     * @return a scrollable page with breadcrumb variations
     */
    Widget create() {
        Label status = new Label("Pick any clickable crumb.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Breadcrumbs full = trail(status, "Camp", "Quests", "The Ashen Vale",
                "Ruined Chapel", "Altar");
        full.setMinimumSize(0, BAR_HEIGHT);

        Breadcrumbs narrow = trail(status, "Camp", "Quests", "The Ashen Vale",
                "Ruined Chapel", "Altar");
        narrow.setMinimumSize(0, BAR_HEIGHT);
        narrow.setMaximumSize(NARROW_WIDTH, BAR_HEIGHT);
        HBox narrowRow = new HBox();
        narrowRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        narrowRow.add(narrow);

        Breadcrumbs shortTrail = trail(status, "Dashboard", "Settings");
        shortTrail.setMinimumSize(0, BAR_HEIGHT);

        Breadcrumbs iconTrail = new Breadcrumbs();
        iconTrail.setMinimumSize(0, BAR_HEIGHT);
        iconCrumb(iconTrail, "Home", "house.svg", status);
        iconCrumb(iconTrail, "Projects", "folder.svg", status);
        iconCrumb(iconTrail, "LixUI", "settings.svg", null);

        VBox page = new VBox();
        page.setPadding(10);
        page.setGap(10);
        page.add(caption("Full trail: every crumb except the current location runs its callback"))
                .add(full)
                .add(caption("Constrained trail: the middle collapses into an ellipsis while "
                        + "the first and last crumbs stay clickable"))
                .add(narrowRow)
                .add(caption("Short trail: no ellipsis and no separators around empty space"))
                .add(shortTrail)
                .add(caption("Icon trail: bundled icons are drawn before the labels and counted "
                        + "in the width"))
                .add(iconTrail)
                .add(status);
        return new DemoScrollPage(page);
    }

    private Breadcrumbs.Crumb iconCrumb(Breadcrumbs breadcrumbs, String label, String icon,
                                        Label status) {
        Breadcrumbs.Crumb crumb = breadcrumbs.addCrumb(label,
                status == null ? null : () -> status.setText("Breadcrumb: " + label));
        crumb.setIcon(ImageSource.icon(icon));
        return crumb;
    }

    private Breadcrumbs trail(Label status, String... labels) {
        Breadcrumbs breadcrumbs = new Breadcrumbs();
        for (int index = 0; index < labels.length; index++) {
            String label = labels[index];
            if (index == labels.length - 1) {
                breadcrumbs.addCrumb(label);
            } else {
                breadcrumbs.addCrumb(label, () -> status.setText("Breadcrumb: " + label));
            }
        }
        return breadcrumbs;
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
