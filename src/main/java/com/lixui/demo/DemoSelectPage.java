package com.lixui.demo;

import com.lixui.render.Texture;
import com.lixui.text.FontFamily;
import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Combobox;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.SelectOption;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.function.Consumer;

/**
 * Builds the select page of the demo scene with several combobox variations.
 */
final class DemoSelectPage {
    private static final float FIELD_WIDTH = 320;
    private static final float FIELD_HEIGHT = 40;

    private final FontFamily fontFamily;
    private final IconBinder iconBinder;

    /**
     * Creates a page builder.
     *
     * @param fontFamily font family used by captions and the status label
     * @param iconBinder callback that loads a bundled icon at the requested size
     */
    DemoSelectPage(FontFamily fontFamily, IconBinder iconBinder) {
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
                .add(status);
        return page;
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

    private Label caption(String text) {
        Label label = new Label(text);
        label.setFontFamily(fontFamily);
        label.setFontStyle(FontStyle.BOLD);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }

    /**
     * Loads a bundled icon and assigns the texture to a target.
     */
    @FunctionalInterface
    interface IconBinder {
        /**
         * Binds one icon.
         *
         * @param name bundled icon resource name
         * @param size requested icon size in logical UI units
         * @param assign consumer receiving the loaded texture
         */
        void bind(String name, float size, Consumer<Texture> assign);
    }
}
