package com.lixui.demo;

import com.lixui.text.FontStyle;
import com.lixui.widget.Widget;
import com.lixui.widget.control.FilterBar;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.PropertyGrid;
import com.lixui.widget.control.QueryBuilder;
import com.lixui.widget.control.TagInput;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Builds the form widget samples shown by the demo scene.
 */
final class DemoFormWidgets {
    private static final float GAP = 6;
    private static final float COLUMN_GAP = 12;

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
                "localization", "security", "analytics"));
        skills.setOnTagsChanged(tags -> status.setText(tagStatus(tags)));
        skills.setOnTagRejected(tag -> status.setText(tagRejection(skills, tag)));

        TagInput singleRow = new TagInput(List.of("accessibility", "performance",
                "documentation", "localization", "integration", "security"));
        singleRow.setMaxRows(1);
        singleRow.setPlaceholder("Single row with +N overflow");
        singleRow.setOnTagsChanged(tags -> status.setText(tagStatus(tags)));
        status.setText(tagStatus(skills.tags()));

        VBox group = new VBox();
        group.setGap(GAP);
        group.add(caption("Tag input: Enter adds a chip, the cross removes it, and rows grow "
                        + "with the tag count. The limit is eight tags and every tag needs at "
                        + "least two characters"))
                .add(skills)
                .add(caption("Single-row mode: tags that do not fit collapse into an overflow "
                        + "counter"))
                .add(singleRow)
                .add(status);
        return group;
    }

    /**
     * Builds two filter bar variations.
     *
     * @return the filter bar section
     */
    static Widget filterBars() {
        Label status = status("Filters are ready to change.");
        FilterBar quick = new FilterBar();
        quick.addFilter("status", "Status", "Online", "Away", "Offline");
        quick.setValue("status", "Online");
        quick.setOnChange(state -> status.setText(describe(state)));

        FilterBar detailed = new FilterBar();
        detailed.addFilter("class", "Class", "Knight", "Ranger", "Mage");
        detailed.addFilter("region", "Region", "Europe", "America", "Asia");
        detailed.addFilter("level", "Level", "1-10", "11-20", "21+");
        detailed.setValue("region", "Europe");
        detailed.setOnChange(state -> status.setText(describe(state)));
        status.setText(describe(quick.filterState()));

        VBox group = new VBox();
        group.setGap(GAP);
        group.add(caption("Filter bar: debounced search and one dropdown per filter"))
                .add(quick)
                .add(caption("Three filters with an initial selection"))
                .add(detailed)
                .add(status);
        return group;
    }

    /**
     * Builds two property grid variations.
     *
     * @return the property grid section
     */
    static Widget propertyGrids() {
        Label status = status("Change a property to see the value here.");
        PropertyGrid display = new PropertyGrid();
        display.addBoolean("fullscreen", "Fullscreen", "Display", false);
        display.addSelect("quality", "Quality", "Display",
                List.of("Low", "Medium", "High"), 1);
        display.addNumber("hud", "HUD scale", "Display", 100, 50, 200, 5, 0);
        display.addBoolean("vsync", "VSync", "Display", true);

        PropertyGrid connection = new PropertyGrid();
        connection.addText("host", "Host", "Connection", "play.example.com");
        connection.addNumber("port", "Port", "Connection", 25565, 1, 65535, 1, 0);
        connection.addSelect("region", "Region", "Connection",
                List.of("Europe", "America", "Asia"), 0);

        Consumer<PropertyGrid.Property<?>> listener =
                property -> status.setText(property.label() + ": " + property.value());
        display.setOnValueChange(listener);
        connection.setOnValueChange(listener);
        display.setGrow(1);
        connection.setGrow(1);

        HBox columns = new HBox();
        columns.setGap(COLUMN_GAP);
        columns.add(display).add(connection);

        VBox group = new VBox();
        group.setGap(GAP);
        group.add(caption("Property grid: typed editors, section search and reset"))
                .add(columns)
                .add(status);
        return group;
    }

    private static String describe(FilterBar.State state) {
        return "Query: \"" + state.query() + "\"    Active filters: "
                + state.activeFilters() + "    " + state.values();
    }

    private static String tagStatus(List<String> tags) {
        return "Tags: " + tags.size() + " - " + String.join(", ", tags);
    }

    private static String tagRejection(TagInput input, String tag) {
        // Mirror the acceptance order so the status names the rule that blocked the value.
        if (input.tags().contains(tag)) {
            return "Rejected \"" + tag + "\": the tag already exists.";
        }
        Predicate<String> validator = input.validator();
        if (validator != null && !validator.test(tag)) {
            return "Rejected \"" + tag + "\": the validator requires at least two characters.";
        }
        return "Rejected \"" + tag + "\": the limit of " + input.maxTags()
                + " tags is reached.";
    }

    /**
     * Builds two query builder variations.
     *
     * @return the query builder section
     */
    static Widget queryBuilders() {
        Label status = status("Change a rule to see the state here.");
        QueryBuilder and = new QueryBuilder();
        and.addField("Status", "is", "is not");
        and.addField("Level", ">=", "<=", "=");
        and.setRules(List.of(
                new QueryBuilder.Rule("Status", "is", "online"),
                new QueryBuilder.Rule("Level", ">=", "10")));
        and.setOnChange((rules, combinator) -> status.setText(describe(rules, combinator)));

        QueryBuilder or = new QueryBuilder();
        or.addField("Region", "equals", "not equals");
        or.addField("Score", ">", "<");
        or.setCombinator(QueryBuilder.Combinator.OR);
        or.setRules(List.of(
                new QueryBuilder.Rule("Region", "equals", "Europe"),
                new QueryBuilder.Rule("Score", ">", "80")));
        or.setOnChange((rules, combinator) -> status.setText(describe(rules, combinator)));

        VBox group = new VBox();
        group.setGap(GAP);
        group.add(caption("Query builder: field, operator and value rows joined with AND"))
                .add(and)
                .add(caption("OR mode with two rules"))
                .add(or)
                .add(status);
        return group;
    }

    private static String describe(List<QueryBuilder.Rule> rules,
                                   QueryBuilder.Combinator combinator) {
        return rules.size() + " rule(s) joined with " + combinator
                + (rules.isEmpty() ? "" : "    first: " + rules.get(0).field()
                + " " + rules.get(0).operator() + " " + rules.get(0).value());
    }

    private static Label status(String text) {
        Label status = new Label(text);
        status.setAutoSize(false);
        status.setWrap(true);
        return status;
    }

    private static Label caption(String text) {
        Label label = new Label(text);
        label.setFontStyle(FontStyle.BOLD);
        label.setAutoSize(false);
        label.setWrap(true);
        return label;
    }
}
