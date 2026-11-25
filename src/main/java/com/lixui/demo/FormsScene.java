package com.lixui.demo;

import com.lixui.graphics.Color;
import com.lixui.theme.Theme;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Alert;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.CheckBox;
import com.lixui.widget.control.Combobox;
import com.lixui.widget.control.DatePicker;
import com.lixui.widget.control.Input;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.MultiSelectCombo;
import com.lixui.widget.control.PasswordInput;
import com.lixui.widget.control.RangeSlider;
import com.lixui.widget.control.SearchInput;
import com.lixui.widget.control.Separator;
import com.lixui.widget.control.Slider;
import com.lixui.widget.control.TagInput;
import com.lixui.widget.control.TimePicker;
import com.lixui.widget.layout.HBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Builds the forms scene with text fields, selects, tags, pickers, sliders and buttons.
 */
final class FormsScene {
    private static final float CONTROL_HEIGHT = 38;
    private static final Theme LIGHT = Theme.light()
            .withPrimary(Color.hex("#2F7D62"))
            .withOnPrimary(Color.WHITE)
            .withFocusRing(Color.hex("#2F7D62"));

    private FormsScene() {
    }

    static ShowcasePage create(ShowcaseKit kit) {
        Label status = kit.muted("All changes are stored locally.");
        ShowcaseCard account = createAccountCard(kit, status);
        ShowcaseCard preferences = createPreferencesCard(kit);

        HBox body = new HBox();
        body.setGap(16);
        body.add(ShowcaseKit.fixWidth(account, 600))
                .add(ShowcaseKit.fixWidth(preferences, 616));

        return new ShowcasePage(kit, 2, 4, "Workspace settings",
                "Form controls arranged in a two-column settings sheet.",
                LIGHT, Theme.dark(), true, body);
    }

    private static ShowcaseCard createAccountCard(ShowcaseKit kit, Label status) {
        ShowcaseCard card = kit.card().padding(20).gap(12);
        card.with(kit.heading("Profile"))
                .with(kit.muted("Update the details other members see in shared workspaces."));

        Input name = new Input("Aria Snow");
        name.setPlaceholder("Display name");
        name.setMinimumSize(0, CONTROL_HEIGHT);

        PasswordInput password = new PasswordInput("aurora-2026");
        password.setPlaceholder("Password");
        password.setMinimumSize(0, CONTROL_HEIGHT);
        HBox nameRow = new HBox();
        nameRow.setGap(12);
        nameRow.add(grow(kit.field("Display name", name)))
                .add(grow(kit.field("Password", password)));
        card.with(nameRow);

        SearchInput search = new SearchInput();
        search.setPlaceholder("Search projects, files and people");
        search.setMinimumSize(0, CONTROL_HEIGHT);
        card.with(kit.field("Search", search));

        Combobox plan = new Combobox();
        plan.setPlaceholder("Choose a plan");
        plan.addOption("Starter");
        plan.addOption("Studio");
        plan.addOption("Scale");
        plan.setSelectedIndex(1);
        plan.setMinimumSize(0, CONTROL_HEIGHT);

        MultiSelectCombo teams = new MultiSelectCombo();
        teams.setPlaceholder("Assign teams");
        teams.addOption("Design");
        teams.addOption("Research");
        teams.addOption("Platform");
        teams.addOption("Growth");
        teams.setSelected(0, true);
        teams.setSelected(1, true);
        teams.setMinimumSize(0, CONTROL_HEIGHT);
        HBox selectRow = new HBox();
        selectRow.setGap(12);
        selectRow.add(grow(kit.field("Plan", plan)))
                .add(grow(kit.field("Teams", teams)));
        card.with(selectRow);

        DatePicker date = new DatePicker(LocalDate.now().plusDays(7), Locale.US);
        date.setMinimumSize(0, CONTROL_HEIGHT);
        TimePicker time = new TimePicker(LocalTime.of(9, 30));
        time.setMinuteStep(15);
        time.setMinimumSize(0, CONTROL_HEIGHT);
        HBox dateRow = new HBox();
        dateRow.setGap(12);
        dateRow.add(grow(kit.field("Kickoff date", date)))
                .add(grow(kit.field("Standup time", time)));
        card.with(dateRow);

        TagInput tags = new TagInput(List.of("design-system", "accessibility", "analytics",
                "platform", "documentation", "release", "security", "localization",
                "performance", "testing", "api"));
        tags.setMaxRows(1);
        tags.setPlaceholder("Add a label");
        tags.setMinimumSize(0, CONTROL_HEIGHT);
        card.with(kit.field("Labels", tags));

        card.with(kit.spacer());
        card.with(new Separator());
        Button save = kit.primaryButton("Save changes");
        save.setMinimumSize(112, CONTROL_HEIGHT);
        Button cancel = new Button("Cancel");
        cancel.setMinimumSize(88, CONTROL_HEIGHT);
        Button reset = new Button("Reset");
        reset.setMinimumSize(80, CONTROL_HEIGHT);
        save.setOnAction(() -> status.setText("Changes saved just now."));
        cancel.setOnAction(() -> status.setText("Changes discarded."));
        reset.setOnAction(() -> status.setText("Defaults restored."));
        HBox actions = new HBox();
        actions.setGap(10);
        actions.setCrossAlignment(HBox.CrossAlignment.CENTER);
        actions.add(save).add(cancel).add(reset).add(kit.spacer()).add(status);
        card.with(actions);
        return card;
    }

    private static ShowcaseCard createPreferencesCard(ShowcaseKit kit) {
        ShowcaseCard card = kit.card().padding(18).gap(10);
        card.with(kit.heading("Preferences"))
                .with(kit.muted("Density, motion and notification defaults for this workspace."));

        Slider volume = new Slider();
        volume.setRange(0, 100);
        volume.setStep(1);
        volume.setValue(65);
        volume.setTickCount(5);
        volume.setMinimumSize(0, 32);
        card.with(kit.field("Interface volume", volume));

        Slider zoom = new Slider();
        zoom.setRange(50, 150);
        zoom.setStep(5);
        zoom.setValue(100);
        zoom.setTickCount(5);
        zoom.setShowRangeLabels(true);
        zoom.setMinimumSize(0, 32);
        card.with(kit.field("Interface scale", zoom));

        RangeSlider contentWidth = new RangeSlider();
        contentWidth.setRange(0, 100);
        contentWidth.setStep(5);
        contentWidth.setValues(25, 80);
        contentWidth.setTickCount(5);
        contentWidth.setMinimumSize(0, 32);
        card.with(kit.field("Content width range", contentWidth));

        card.with(new Separator());
        card.with(kit.heading("Interface"));

        CheckBox compact = new CheckBox("Compact mode");
        compact.setIndicator(CheckBox.Indicator.SWITCH);
        compact.setChecked(true);
        CheckBox grid = new CheckBox("Show layout grid");
        CheckBox motion = new CheckBox("Reduce motion");
        card.with(compact).with(grid).with(motion);

        card.with(kit.spacer());
        card.with(new Separator());
        card.with(kit.heading("Validation"));
        CheckBox validate = new CheckBox("Show the validation warning");
        Alert validation = new Alert(Alert.Tone.SUCCESS);
        validation.setTitle("Profile looks good");
        validation.setMessage("Every required field is filled in.");
        validate.setOnChange(checked -> {
            validation.setTone(checked ? Alert.Tone.WARNING : Alert.Tone.SUCCESS);
            validation.setTitle(checked ? "Check the display name" : "Profile looks good");
            validation.setMessage(checked
                    ? "The name must be at least three characters long."
                    : "Every required field is filled in.");
        });
        card.with(validate).with(validation);
        return card;
    }

    private static Widget grow(Widget widget) {
        widget.setGrow(1);
        return widget;
    }
}
