package com.lixui.widget.control;

import com.lixui.geometry.Size;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A small query editor with rules made of a field, an operator and a value.
 * Rules are combined with AND or OR, can be added and removed at runtime, and report a snapshot
 * through a change callback. The editor stacks its rows vertically and grows with their count.
 * <p>
 * Fields and their operators are registered before rules are added; every rule must reference a
 * registered field and one of its operators.
 */
public class QueryBuilder extends Widget {
    private static final float ROW_GAP = 8;
    private static final float FIELD_GAP = 8;
    private static final float DEFAULT_FIELD_WIDTH = 120;
    private static final float DEFAULT_OPERATOR_WIDTH = 96;

    private final VBox root = new VBox();
    private final HBox combinatorRow = new HBox();
    private final Dropdown combinatorSelect = new Dropdown();
    private final Button addButton = new Button("Add rule");
    private final List<Field> fields = new ArrayList<>();
    private final List<RuleRow> rows = new ArrayList<>();
    private Combinator combinator = Combinator.AND;
    private BiConsumer<List<Rule>, Combinator> onChange;
    private boolean updating;

    /**
     * Creates an empty query builder without fields or rules.
     */
    public QueryBuilder() {
        root.setGap(ROW_GAP);
        combinatorRow.setGap(FIELD_GAP);
        combinatorRow.setCrossAlignment(HBox.CrossAlignment.CENTER);
        Label matchLabel = new Label("Match");
        combinatorSelect.setMinimumSize(DEFAULT_OPERATOR_WIDTH, 0);
        combinatorSelect.addOption("AND");
        combinatorSelect.addOption("OR");
        combinatorSelect.setSelectedIndex(0);
        combinatorSelect.setOnChange(option -> {
            if (updating) {
                return;
            }
            combinator = combinatorSelect.selectedIndex() == 1 ? Combinator.OR : Combinator.AND;
            notifyChange();
        });
        combinatorRow.add(matchLabel).add(combinatorSelect);
        addButton.setOnAction(this::addRule);
        addButton.setEnabled(false);
        root.add(combinatorRow).add(addButton);
        add(root);
    }

    /**
     * Registers a field with its operators.
     * A field without explicit operators accepts the equality operator only.
     *
     * @param name field name
     * @param operators operator labels
     * @throws NullPointerException if the name or an operator is null
     * @throws IllegalArgumentException if the name or an operator is blank, the name is already
     *                                  registered, or an operator repeats
     */
    public final void addField(String name, String... operators) {
        Field field = new Field(name, operators.length == 0
                ? List.of("=") : List.of(operators));
        if (fields.stream().anyMatch(existing -> existing.name().equals(field.name()))) {
            throw new IllegalArgumentException("Field is already registered: " + name);
        }
        fields.add(field);
        addButton.setEnabled(true);
        updating = true;
        try {
            for (RuleRow row : rows) {
                row.refreshFields();
            }
        } finally {
            updating = false;
        }
    }

    /**
     * Returns the registered fields.
     *
     * @return an immutable snapshot of the fields
     */
    public final List<Field> fields() {
        return List.copyOf(fields);
    }

    /**
     * Appends a rule with the first registered field and its first operator.
     *
     * @throws IllegalStateException if no field is registered
     */
    public final void addRule() {
        if (fields.isEmpty()) {
            throw new IllegalStateException("Register a field before adding rules.");
        }
        RuleRow row = new RuleRow(fields.get(0));
        rows.add(row);
        insertRowWidget(row);
        notifyChange();
    }

    /**
     * Replaces every rule.
     * Passing the current rules is a no-op, which keeps callbacks that write a snapshot back into
     * the builder from rebuilding rows and dropping focus.
     *
     * @param rules new rules
     * @throws NullPointerException if {@code rules} or one of its elements is null
     * @throws IllegalArgumentException if a rule references an unknown field or operator
     */
    public final void setRules(List<Rule> rules) {
        Objects.requireNonNull(rules, "rules");
        List<Rule> snapshot = List.copyOf(rules);
        if (snapshot.equals(rules())) {
            return;
        }
        for (Rule rule : snapshot) {
            validatedField(rule);
        }
        updating = true;
        try {
            clearRows();
            root.remove(addButton);
            for (Rule rule : snapshot) {
                RuleRow row = new RuleRow(fieldByName(rule.field()));
                row.setRule(rule);
                rows.add(row);
                root.add(row.row);
            }
            root.add(addButton);
        } finally {
            updating = false;
        }
        notifyChange();
    }

    /**
     * Removes a rule by index.
     *
     * @param index zero-based rule index
     * @throws IllegalArgumentException if the index is out of range
     */
    public final void removeRule(int index) {
        if (index < 0 || index >= rows.size()) {
            throw new IllegalArgumentException("Rule index is out of range.");
        }
        RuleRow row = rows.remove(index);
        root.remove(row.row);
        notifyChange();
    }

    /**
     * Returns the current rules in display order.
     *
     * @return an immutable snapshot of the rules
     */
    public final List<Rule> rules() {
        List<Rule> snapshot = new ArrayList<>();
        for (RuleRow row : rows) {
            snapshot.add(row.snapshot());
        }
        return List.copyOf(snapshot);
    }

    /**
     * Returns the combinator applied between rules.
     *
     * @return the current combinator
     */
    public final Combinator combinator() {
        return combinator;
    }

    /**
     * Replaces the combinator applied between rules.
     *
     * @param combinator new combinator
     * @throws NullPointerException if {@code combinator} is null
     */
    public final void setCombinator(Combinator combinator) {
        Objects.requireNonNull(combinator, "combinator");
        if (this.combinator == combinator) {
            return;
        }
        this.combinator = combinator;
        updating = true;
        try {
            combinatorSelect.setSelectedIndex(combinator == Combinator.OR ? 1 : 0);
        } finally {
            updating = false;
        }
        notifyChange();
    }

    /**
     * Returns the callback invoked when the rules or the combinator change.
     *
     * @return the callback, or null when none is assigned
     */
    public final BiConsumer<List<Rule>, Combinator> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked with immutable rules and the current combinator.
     *
     * @param onChange callback, or null to clear it
     */
    public final void setOnChange(BiConsumer<List<Rule>, Combinator> onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the stacked rules.
     *
     * @param constraints available and required dimensions
     * @return the natural editor size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return root.measure(constraints);
    }

    /**
     * Stacks the rules and the add button vertically.
     */
    @Override
    protected void onLayout() {
        root.layout(localBounds());
    }

    private void insertRowWidget(RuleRow row) {
        root.remove(addButton);
        root.add(row.row);
        root.add(addButton);
    }

    private void clearRows() {
        for (RuleRow row : rows) {
            root.remove(row.row);
        }
        rows.clear();
    }

    private Field validatedField(Rule rule) {
        Field field = fieldByName(rule.field());
        if (!field.operators().contains(rule.operator())) {
            throw new IllegalArgumentException("Unknown operator for field "
                    + rule.field() + ": " + rule.operator());
        }
        return field;
    }

    private Field fieldByName(String name) {
        for (Field field : fields) {
            if (field.name().equals(name)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unknown field: " + name);
    }

    private void notifyChange() {
        if (updating) {
            return;
        }
        if (onChange != null) {
            onChange.accept(rules(), combinator);
        }
    }

    /**
     * A rule field and the operators it supports.
     *
     * @param name field name
     * @param operators non-empty operator labels
     */
    public record Field(String name, List<String> operators) {
        /**
         * Creates a field definition.
         *
         * @param name field name
         * @param operators operator labels
         * @throws NullPointerException if the name or an operator is null
         * @throws IllegalArgumentException if the name or an operator is blank, the operator list
         *                                  is empty, or an operator repeats
         */
        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(operators, "operators");
            if (name.isBlank()) {
                throw new IllegalArgumentException("Field name must not be blank.");
            }
            if (operators.isEmpty()) {
                throw new IllegalArgumentException("A field needs at least one operator.");
            }
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String operator : operators) {
                Objects.requireNonNull(operator, "operator");
                if (operator.isBlank()) {
                    throw new IllegalArgumentException("Operator must not be blank.");
                }
                if (!unique.add(operator)) {
                    throw new IllegalArgumentException("Operator repeats: " + operator);
                }
            }
            operators = List.copyOf(unique);
        }
    }

    /**
     * A query rule snapshot.
     *
     * @param field field name
     * @param operator operator label
     * @param value comparison value, possibly empty
     */
    public record Rule(String field, String operator, String value) {
        /**
         * Creates a rule.
         *
         * @param field field name
         * @param operator operator label
         * @param value comparison value
         * @throws NullPointerException if an argument is null
         */
        public Rule {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * The logical combinator applied between rules.
     */
    public enum Combinator {
        /** Every rule must match. */
        AND,
        /** At least one rule must match. */
        OR
    }

    private final class RuleRow {
        private final HBox row = new HBox();
        private final Dropdown fieldSelect = new Dropdown();
        private final Dropdown operatorSelect = new Dropdown();
        private final Input valueInput = new Input();
        private final Button removeButton = new Button("x");
        private Field selectedField;

        private RuleRow(Field field) {
            selectedField = field;
            row.setGap(FIELD_GAP);
            row.setCrossAlignment(HBox.CrossAlignment.CENTER);
            fieldSelect.setMinimumSize(DEFAULT_FIELD_WIDTH, 0);
            operatorSelect.setMinimumSize(DEFAULT_OPERATOR_WIDTH, 0);
            valueInput.setPlaceholder("Value");
            valueInput.setGrow(1);
            refreshFields();
            selectOperator(field.operators().get(0));

            fieldSelect.setOnChange(option -> {
                if (updating) {
                    return;
                }
                int index = fieldSelect.selectedIndex();
                if (index < 0 || index >= fields.size()) {
                    return;
                }
                selectedField = fields.get(index);
                updating = true;
                try {
                    selectOperator(selectedField.operators().get(0));
                } finally {
                    updating = false;
                }
                notifyChange();
            });
            operatorSelect.setOnChange(option -> notifyChange());
            valueInput.setOnChange(text -> notifyChange());
            removeButton.setOnAction(() -> {
                rows.remove(this);
                root.remove(row);
                notifyChange();
            });
            row.add(fieldSelect).add(operatorSelect).add(valueInput).add(removeButton);
        }

        private void refreshFields() {
            List<SelectOption> options = new ArrayList<>();
            int selected = 0;
            for (int index = 0; index < fields.size(); index++) {
                Field field = fields.get(index);
                options.add(new SelectOption(field.name()));
                if (field.name().equals(selectedField.name())) {
                    selected = index;
                }
            }
            fieldSelect.setOptions(options);
            fieldSelect.setSelectedIndex(selected);
        }

        private void selectOperator(String operator) {
            List<SelectOption> options = new ArrayList<>();
            int selected = 0;
            for (int index = 0; index < selectedField.operators().size(); index++) {
                String candidate = selectedField.operators().get(index);
                options.add(new SelectOption(candidate));
                if (candidate.equals(operator)) {
                    selected = index;
                }
            }
            operatorSelect.setOptions(options);
            operatorSelect.setSelectedIndex(selected);
        }

        private void setRule(Rule rule) {
            selectedField = fieldByName(rule.field());
            refreshFields();
            selectOperator(rule.operator());
            valueInput.setText(rule.value());
        }

        private Rule snapshot() {
            SelectOption operator = operatorSelect.selectedOption();
            return new Rule(selectedField.name(),
                    operator == null ? selectedField.operators().get(0) : operator.label(),
                    valueInput.text());
        }
    }
}
