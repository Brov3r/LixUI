package com.lixui.demo;

import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Combobox;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.MultiSelectCombo;
import com.lixui.widget.control.SelectOption;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.List;

/**
 * Builds the select page of the demo scene with several combobox variations.
 */
final class DemoSelectPage {
    private static final float FIELD_WIDTH = 320;
    private static final float FIELD_HEIGHT = 40;

    private final FontFamily fontFamily;
    private final DemoIconBinder iconBinder;

    /**
     * Creates a page builder.
     *
     * @param fontFamily font family used by captions and the status label
     * @param iconBinder callback that loads a bundled icon at the requested size
     */
    DemoSelectPage(FontFamily fontFamily, DemoIconBinder iconBinder) {
        this.fontFamily = fontFamily;
        this.iconBinder = iconBinder;
    }

    /**
     * Creates the select page content.
     *
     * @return a vertical page with thematic combobox variations
     */
    Widget create() {
        Label status = new Label("Search a class, pick a row or confirm a custom hero title.");
        status.setFontFamily(fontFamily);
        status.setAutoSize(false);
        status.setWrap(true);

        Combobox classes = field("Choose a class");
        addOption(classes, "Knight", "sword.svg");
        addOption(classes, "Ranger", "bow.svg");
        addOption(classes, "Mage", "potion.svg");
        addOption(classes, "Healer", "shield.svg");
        addOption(classes, "Rogue", "dagger.svg");
        classes.setSelectedIndex(0);
        classes.setOnChange(option -> status.setText(option == null
                ? "Class cleared." : "Class: " + option.label()));

        Combobox titles = field("Pick a class or type a title");
        titles.setAllowCustom(true);
        addOption(titles, "Warden of the North", "shield.svg");
        addOption(titles, "Keeper of Ashes", "torch.svg");
        addOption(titles, "Voice of the Wilds", "bow.svg");
        titles.setOnChange(option -> status.setText(option == null
                ? (titles.value() == null ? "Custom title cleared."
                : "Custom title: \"" + titles.value() + "\"")
                : "Title: " + option.label()));

        Combobox talents = field("Filter talents");
        talents.setMaxPopupHeight(150);
        addOption(talents, "Swordsmanship", "sword.svg");
        addOption(talents, "Archery", "bow.svg");
        addOption(talents, "Alchemy", "potion.svg");
        addOption(talents, "Shielding", "shield.svg");
        addOption(talents, "Lockpicking", "key.svg");
        addOption(talents, "Survival", "torch.svg");
        addOption(talents, "Cooking", "bread.svg");
        addOption(talents, "Repair", "tool-kit.svg");
        SelectOption forbidden = addOption(talents, "Necromancy", "book.svg");
        forbidden.setDisabled(true);
        talents.setOnChange(option -> status.setText(option == null
                ? "Talent cleared." : "Talent: " + option.label()));

        CheckBox customValues = new CheckBox("Accept custom titles");
        customValues.setChecked(true);
        customValues.setOnChange(titles::setAllowCustom);

        MultiSelectCombo abilities = new MultiSelectCombo();
        abilities.setPlaceholder("Choose abilities");
        abilities.setMinimumSize(FIELD_WIDTH, FIELD_HEIGHT);
        abilities.setMaximumSize(FIELD_WIDTH + 100, FIELD_HEIGHT + 6);
        abilities.setMaxPopupHeight(180);
        addOption(abilities, "Swordsmanship", "sword.svg");
        addOption(abilities, "Archery", "bow.svg");
        addOption(abilities, "Alchemy", "potion.svg");
        addOption(abilities, "Shielding", "shield.svg");
        addOption(abilities, "Lockpicking", "key.svg");
        addOption(abilities, "Survival", "torch.svg");
        addOption(abilities, "Cooking", "bread.svg");
        addOption(abilities, "Repair", "tool-kit.svg");
        abilities.setSelected(0, true);
        abilities.setSelected(2, true);
        abilities.setOnChange((option, checked) -> status.setText("Abilities: "
                + abilities.selectedCount() + " selected, " + option.label()
                + (checked ? " added." : " removed.")));

        MultiSelectCombo regions = new MultiSelectCombo();
        regions.setPlaceholder("Choose regions");
        regions.setMinimumSize(FIELD_WIDTH, FIELD_HEIGHT);
        regions.setMaximumSize(FIELD_WIDTH + 100, FIELD_HEIGHT + 6);
        regions.setMaxPopupHeight(200);
        for (String name : List.of("Aldermoor", "Bracken Vale", "Cinder Coast", "Duskmere",
                "Elderwood", "Frost Hollow", "Glimmer Fen", "Highpass", "Ironreach",
                "Juniper Steppe", "Kestrel Isles", "Lowmarch", "Mistfall", "Northwatch")) {
            regions.addOption(name);
        }
        regions.setSelected(1, true);
        regions.setSelected(3, true);
        regions.setSelected(6, true);
        regions.options().get(4).setDisabled(true);
        regions.options().get(9).setDisabled(true);
        regions.setOnChange((option, checked) -> status.setText("Regions: "
                + regions.selectedCount() + " selected, " + option.label()
                + (checked ? " added." : " removed.")));

        Button selectAll = new Button("Select all");
        selectAll.setOnAction(() -> {
            regions.setAll(true);
            status.setText("All available regions selected: " + regions.selectedCount() + ".");
        });
        Button clearRegions = new Button("Clear");
        clearRegions.setOnAction(() -> {
            regions.clearSelection();
            status.setText("Regions cleared.");
        });
        HBox regionActions = new HBox();
        regionActions.setGap(8);
        regionActions.setCrossAlignment(HBox.CrossAlignment.CENTER);
        regionActions.add(selectAll).add(clearRegions);

        HBox options = new HBox();
        options.setGap(12);
        options.setCrossAlignment(HBox.CrossAlignment.CENTER);
        options.add(customValues);

        VBox page = new VBox();
        page.setPadding(10);
        page.setGap(10);
        page.add(caption("Class combobox: filter rows with the keyboard, Enter selects, "
                        + "Escape closes without a change"))
                .add(classes)
                .add(caption("Custom values: Enter on unmatched text keeps it as the title"))
                .add(titles)
                .add(options)
                .add(caption("Long list: the popup scrolls and the disabled talent cannot be "
                        + "picked"))
                .add(talents)
                .add(caption("Multi-select combobox: the popup stays open while abilities are "
                        + "toggled"))
                .add(abilities)
                .add(caption("Icon-free multi-select: a long list with disabled regions and "
                        + "bulk actions"))
                .add(regions)
                .add(regionActions)
                .add(status);
        return new DemoScrollPage(page);
    }

    private Combobox field(String placeholder) {
        Combobox combobox = new Combobox();
        combobox.setPlaceholder(placeholder);
        combobox.setMinimumSize(FIELD_WIDTH, FIELD_HEIGHT);
        combobox.setMaximumSize(FIELD_WIDTH + 100, FIELD_HEIGHT + 6);
        return combobox;
    }

    private SelectOption addOption(Combobox combobox, String label, String icon) {
        SelectOption option = combobox.addOption(label);
        iconBinder.bind(icon, combobox.iconSize(), option::setIcon);
        return option;
    }

    private SelectOption addOption(MultiSelectCombo combobox, String label, String icon) {
        SelectOption option = combobox.addOption(label);
        iconBinder.bind(icon, combobox.iconSize(), option::setIcon);
        return option;
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
