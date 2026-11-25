package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.input.InputState;
import com.lixui.input.event.CharTyped;
import com.lixui.render.Viewport;
import com.lixui.text.Font;
import com.lixui.text.FontData;
import com.lixui.text.SystemFonts;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.WidgetManager;
import com.lixui.widget.testsupport.TestBackend;
import com.lixui.widget.testsupport.TestViewports;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryBuilderTest {
    private static final Viewport VIEWPORT = TestViewports.LOGICAL;

    @Test
    void addsRulesWithTheFirstFieldAndReportsChanges() {
        QueryBuilder builder = new QueryBuilder();
        builder.addField("Status", "is", "is not");
        builder.addField("Level", "=", ">=", "<=");
        List<List<QueryBuilder.Rule>> changes = new ArrayList<>();
        builder.setOnChange((rules, combinator) -> changes.add(rules));

        builder.addRule();

        assertEquals(List.of(new QueryBuilder.Rule("Status", "is", "")), builder.rules());
        assertEquals(1, changes.size());
        assertThrows(IllegalStateException.class, () -> new QueryBuilder().addRule());
    }

    @Test
    void setRulesReplacesRowsAndValidatesFieldsAndOperators() {
        QueryBuilder builder = new QueryBuilder();
        builder.addField("Status", "is", "is not");
        builder.addField("Level", "=", ">=", "<=");
        List<QueryBuilder.Rule> rules = List.of(
                new QueryBuilder.Rule("Status", "is not", "offline"),
                new QueryBuilder.Rule("Level", ">=", "10"));
        List<List<QueryBuilder.Rule>> changes = new ArrayList<>();
        builder.setOnChange((ignored, combinator) -> changes.add(rules));

        builder.setRules(rules);

        assertEquals(rules, builder.rules());
        assertEquals(1, changes.size());
        assertEquals(rules, changes.get(0));

        assertThrows(IllegalArgumentException.class, () -> builder.setRules(List.of(
                new QueryBuilder.Rule("Missing", "=", "x"))));
        assertThrows(IllegalArgumentException.class, () -> builder.setRules(List.of(
                new QueryBuilder.Rule("Status", "contains", "x"))));
        assertEquals(rules, builder.rules());
    }

    @Test
    void settingTheCurrentRulesIsANoOp() {
        QueryBuilder builder = new QueryBuilder();
        builder.addField("Status", "is", "is not");
        List<QueryBuilder.Rule> rules = List.of(
                new QueryBuilder.Rule("Status", "is", "online"));
        builder.setRules(rules);
        List<List<QueryBuilder.Rule>> changes = new ArrayList<>();
        builder.setOnChange((current, combinator) -> changes.add(current));

        builder.setRules(builder.rules());

        assertTrue(changes.isEmpty());
        assertEquals(rules, builder.rules());
    }

    @Test
    void feedbackLoopKeepsFocusWhileTyping() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            QueryBuilder builder = new QueryBuilder();
            builder.addField("Status", "is", "is not");
            builder.setRules(List.of(new QueryBuilder.Rule("Status", "is", "")));
            builder.setBounds(new Rect(0, 0, 480, 200));
            manager.add(builder);
            manager.render();

            Input input = findInput(builder);
            assertNotNull(input);
            manager.focus(input);
            List<List<QueryBuilder.Rule>> changes = new ArrayList<>();
            builder.setOnChange((rules, combinator) -> {
                changes.add(rules);
                builder.setRules(rules);
            });

            manager.handle(new CharTyped('x'));

            assertEquals(1, changes.size());
            assertEquals("x", input.text());
            assertEquals(input, manager.focused());
        }
    }

    @Test
    void removesRulesAndChangesTheCombinator() {
        QueryBuilder builder = new QueryBuilder();
        builder.addField("Status", "is");
        builder.addRule();
        builder.addRule();
        List<QueryBuilder.Combinator> combinators = new ArrayList<>();
        builder.setOnChange((rules, combinator) -> combinators.add(combinator));

        builder.removeRule(1);
        assertEquals(1, builder.rules().size());
        combinators.clear();

        builder.setCombinator(QueryBuilder.Combinator.OR);
        assertEquals(QueryBuilder.Combinator.OR, builder.combinator());
        assertEquals(List.of(QueryBuilder.Combinator.OR), combinators);

        assertThrows(IllegalArgumentException.class, () -> builder.removeRule(5));
        assertThrows(NullPointerException.class, () -> builder.setCombinator(null));
    }

    @Test
    void growsWithTheRuleCount() {
        TestBackend backend = new TestBackend();
        try (WidgetManager manager = manager(backend)) {
            manager.setDefaultFont(font(manager));
            QueryBuilder builder = new QueryBuilder();
            builder.addField("Status", "is", "is not");
            builder.addField("Level", ">=", "<=");
            builder.setBounds(new Rect(0, 0, 480, 400));
            manager.add(builder);
            manager.render();
            builder.addRule();
            float oneRule = builder.measure(LayoutConstraints.loose(480, 2000)).height();

            builder.setRules(List.of(
                    new QueryBuilder.Rule("Status", "is", "online"),
                    new QueryBuilder.Rule("Level", ">=", "10"),
                    new QueryBuilder.Rule("Level", "<=", "20")));
            float threeRules = builder.measure(LayoutConstraints.loose(480, 2000)).height();

            assertTrue(threeRules > oneRule);
        }
    }

    @Test
    void rejectsInvalidFields() {
        QueryBuilder builder = new QueryBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.addField(" "));
        assertThrows(IllegalArgumentException.class, () -> builder.addField("Status", "is", "is"));
        builder.addField("Status", "is");
        assertThrows(IllegalArgumentException.class, () -> builder.addField("Status", "is"));
        assertThrows(NullPointerException.class, () -> builder.addField(null));
        assertThrows(NullPointerException.class, () -> builder.addField("Level", (String) null));
        assertEquals(List.of(new QueryBuilder.Field("Status", List.of("is"))), builder.fields());
    }

    private static Input findInput(Widget widget) {
        for (Widget child : widget.children()) {
            if (child instanceof Input input) {
                return input;
            }
            Input nested = findInput(child);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Font font(WidgetManager manager) {
        Optional<FontData> data = SystemFonts.findDefault();
        Assumptions.assumeTrue(data.isPresent(), "No readable system font is installed.");
        return manager.context().createFont(data.get(), 16);
    }

    private static WidgetManager manager(TestBackend backend) {
        WidgetManager manager = new WidgetManager(new InputState(), backend);
        manager.resize(VIEWPORT);
        return manager;
    }
}
