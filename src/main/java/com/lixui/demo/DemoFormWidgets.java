package com.lixui.demo;

import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.TagInput;
import com.lixui.widget.layout.VBox;

import java.util.List;

/**
 * Builds the form widget samples shown by the demo scene.
 */
final class DemoFormWidgets {
    private static final float GAP = 6;

    private DemoFormWidgets() {
    }

    /**
     * Builds two tag input variations.
     *
     * @return the tag input section
     */
    static Widget tagInputs() {
        Label status = new Label("Add or remove a tag to see the state here.");
        status.setAutoSize(false);
        status.setWrap(true);

        TagInput skills = new TagInput();
        skills.setPlaceholder("Type a skill and press Enter");
        skills.setMaxTags(8);
        skills.setValidator(tag -> tag.length() > 1);
        skills.setTags(List.of("design", "frontend", "quality", "documentation",
                "localization", "security", "analytics", "testing"));
        skills.setOnTagsChanged(tags -> status.setText(tagStatus(tags)));

        TagInput singleRow = new TagInput(List.of("accessibility", "performance",
                "documentation", "localization", "integration", "security"));
        singleRow.setMaxRows(1);
        singleRow.setPlaceholder("Single row with +N overflow");
        singleRow.setOnTagsChanged(tags -> status.setText(tagStatus(tags)));
        status.setText(tagStatus(skills.tags()));

        VBox group = new VBox();
        group.setGap(GAP);
        group.add(caption("Tag input: Enter adds a chip, the cross removes it, and rows grow "
                        + "with the tag count"))
                .add(skills)
                .add(caption("Single-row mode: tags that do not fit collapse into an overflow "
                        + "counter"))
                .add(singleRow)
                .add(status);
        return group;
    }

    private static String tagStatus(List<String> tags) {
        return "Tags: " + tags.size() + " - " + String.join(", ", tags);
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.setFontStyle(FontStyle.BOLD);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }
}
