package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.layout.Flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A tag editor that combines a text field with removable chips.
 * Enter adds the typed value, a chip cross removes it, and Backspace in an empty field removes the
 * last tag. Chips wrap onto new lines and the editor grows with the rows. A row limit or a
 * constrained height replaces the tags that no longer fit with an overflow chip such as
 * {@code +3}.
 * <p>
 * The input is the only focusable part; pressing anywhere on the surface focuses it. A validator
 * and a tag limit reject new values without discarding the typed text, so the user can correct it.
 */
public class TagInput extends Panel {
    private static final float CHIP_GAP = 4;
    private static final float ROW_GAP = 6;
    private static final float CROSS_SLOT = 16;
    private static final float CROSS_MARK = 3;
    private static final float CROSS_STROKE = 1.4f;
    private static final float CROSS_INSET = 3;

    private final Flow chipArea = new Flow();
    private final Input field = new TagField();
    private final List<String> tags = new ArrayList<>();
    private final List<Chip> chips = new ArrayList<>();
    private String placeholder = "Add a tag...";
    private int maxTags;
    private int maxRows;
    private Predicate<String> validator;
    private Consumer<List<String>> onTagsChanged;
    private Chip overflowChip = new Chip("", false);

    /**
     * Creates an empty tag editor.
     */
    public TagInput() {
        this(List.of());
    }

    /**
     * Creates a tag editor filled with the supplied values.
     * Values are normalized and filtered like values added through {@link #addTag(String)}.
     *
     * @param tags initial tags
     * @throws NullPointerException if {@code tags} or one of its elements is null
     */
    public TagInput(List<String> tags) {
        chipArea.setGap(CHIP_GAP);
        field.setPlaceholder(placeholder);
        field.setOnSubmit(this::commitField);
        add(chipArea).add(field);
        setTags(tags);
    }

    /**
     * Returns the current tags in insertion order.
     *
     * @return an immutable snapshot of the tags
     */
    public final List<String> tags() {
        return List.copyOf(tags);
    }

    /**
     * Replaces all tags with the accepted values from the supplied list.
     * Empty and duplicate values are ignored, the validator filters the rest, and the list is cut
     * at {@link #maxTags()} when a limit is configured. The callback runs once when the resulting
     * list differs from the current one.
     *
     * @param tags new tags
     * @throws NullPointerException if {@code tags} or one of its elements is null
     */
    public final void setTags(List<String> tags) {
        Objects.requireNonNull(tags, "tags");
        List<String> accepted = new ArrayList<>();
        for (String tag : tags) {
            String normalized = normalize(Objects.requireNonNull(tag, "tag"));
            if (normalized == null || accepted.contains(normalized) || !isValid(normalized)) {
                continue;
            }
            if (maxTags > 0 && accepted.size() >= maxTags) {
                break;
            }
            accepted.add(normalized);
        }
        if (accepted.equals(this.tags)) {
            return;
        }
        this.tags.clear();
        this.tags.addAll(accepted);
        rebuildChips();
        notifyTagsChanged();
    }

    /**
     * Adds a tag when it is non-empty, unique, accepted by the validator and within the limit.
     *
     * @param tag tag to add
     * @return true when the tag was added
     */
    public final boolean addTag(String tag) {
        String normalized = normalize(Objects.requireNonNull(tag, "tag"));
        if (normalized == null || tags.contains(normalized) || !isValid(normalized)) {
            return false;
        }
        if (maxTags > 0 && tags.size() >= maxTags) {
            return false;
        }
        tags.add(normalized);
        rebuildChips();
        notifyTagsChanged();
        return true;
    }

    /**
     * Removes a tag by exact value.
     *
     * @param tag tag to remove
     * @return true when the tag was present
     */
    public final boolean removeTag(String tag) {
        if (!tags.remove(tag)) {
            return false;
        }
        rebuildChips();
        notifyTagsChanged();
        return true;
    }

    /**
     * Removes every tag.
     */
    public final void clearTags() {
        if (tags.isEmpty()) {
            return;
        }
        tags.clear();
        rebuildChips();
        notifyTagsChanged();
    }

    /**
     * Returns the maximum number of tags.
     *
     * @return the limit, or zero when the count is unlimited
     */
    public final int maxTags() {
        return maxTags;
    }

    /**
     * Sets the maximum number of tags.
     * Lowering the limit below the current count removes the extra trailing tags.
     *
     * @param maxTags non-negative limit, or zero for no limit
     * @throws IllegalArgumentException if the limit is negative
     */
    public final void setMaxTags(int maxTags) {
        if (maxTags < 0) {
            throw new IllegalArgumentException("Maximum tag count must be non-negative.");
        }
        this.maxTags = maxTags;
        if (maxTags > 0 && tags.size() > maxTags) {
            tags.subList(maxTags, tags.size()).clear();
            rebuildChips();
            notifyTagsChanged();
        }
    }

    /**
     * Returns the maximum number of chip rows.
     *
     * @return the row limit, or zero when the editor grows without a row limit
     */
    public final int maxRows() {
        return maxRows;
    }

    /**
     * Sets the maximum number of chip rows.
     * Tags that do not fit into the limit are represented by a single overflow chip. A zero limit
     * lets the editor grow with every row.
     *
     * @param maxRows non-negative row limit, or zero for no limit
     * @throws IllegalArgumentException if the limit is negative
     */
    public final void setMaxRows(int maxRows) {
        if (maxRows < 0) {
            throw new IllegalArgumentException("Maximum row count must be non-negative.");
        }
        this.maxRows = maxRows;
    }

    /**
     * Returns the validator applied to new tags.
     *
     * @return the validator, or null when every non-empty tag is accepted
     */
    public final Predicate<String> validator() {
        return validator;
    }

    /**
     * Assigns a validator for new tags.
     * The validator runs for typed and programmatic additions; existing tags are not revalidated.
     *
     * @param validator validator, or null to accept every non-empty tag
     */
    public final void setValidator(Predicate<String> validator) {
        this.validator = validator;
    }

    /**
     * Returns the placeholder shown while the field is empty.
     *
     * @return the placeholder text
     */
    public final String placeholder() {
        return placeholder;
    }

    /**
     * Sets the placeholder shown while the field is empty.
     *
     * @param placeholder placeholder text
     * @throws NullPointerException if {@code placeholder} is null
     */
    public final void setPlaceholder(String placeholder) {
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
        field.setPlaceholder(placeholder);
    }

    /**
     * Returns the callback invoked after the tag list changes.
     *
     * @return the callback, or null when none is assigned
     */
    public final Consumer<List<String>> onTagsChanged() {
        return onTagsChanged;
    }

    /**
     * Assigns a callback invoked with an immutable snapshot after the tag list changes.
     *
     * @param onTagsChanged callback, or null to clear it
     */
    public final void setOnTagsChanged(Consumer<List<String>> onTagsChanged) {
        this.onTagsChanged = onTagsChanged;
    }

    /**
     * Reports whether the nested field currently holds keyboard focus.
     *
     * @return true when the text field is focused
     */
    public final boolean isFieldFocused() {
        return field.isFocused();
    }

    /**
     * Returns the control style for the outer surface.
     *
     * @return the context control style, or null when the editor is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        return context() == null ? null : context().theme().controlStyle();
    }

    /**
     * Uses the local style or the control style instead of an inherited panel style.
     *
     * @return the resolved surface style, or null when the editor is not attached
     */
    @Override
    protected WidgetStyle surfaceStyle() {
        return hasLocalStyle() ? style() : defaultStyle();
    }

    /**
     * Shows the focus border of the outer surface while the nested field is focused.
     *
     * @return the active editor state
     */
    @Override
    protected WidgetState state() {
        if (!isEnabled()) {
            return WidgetState.DISABLED;
        }
        if (field.isFocused()) {
            return WidgetState.FOCUSED;
        }
        return super.state();
    }

    /**
     * Measures the field and the wrapped chip rows.
     * Tags beyond the row limit are replaced by the overflow chip in the measurement.
     *
     * @param constraints available and required dimensions
     * @return the natural editor size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float inset = inset();
        float availableWidth = Math.max(0, constraints.maxWidth() - inset * 2);
        Size fieldSize = field.measure(LayoutConstraints.loose(availableWidth, constraints.maxHeight()));
        Pack pack = plan(availableWidth, -1);
        float height = fieldSize.height() + inset * 2;
        if (pack.rows > 0) {
            height += pack.height + ROW_GAP;
        }
        float width = Math.max(fieldSize.width(), pack.width) + inset * 2;
        return new Size(width, height);
    }

    /**
     * Places the chip rows above the field and hides chips that exceed the row or height limit.
     */
    @Override
    protected void onLayout() {
        float inset = inset();
        float width = Math.max(0, bounds().width() - inset * 2);
        float innerHeight = Math.max(0, bounds().height() - inset * 2);
        Size fieldSize = field.measure(LayoutConstraints.loose(width, innerHeight));
        float fieldHeight = Math.min(fieldSize.height(), innerHeight);
        float chipBudget = innerHeight - fieldHeight - (tags.isEmpty() ? 0 : ROW_GAP);
        Pack pack = plan(width, chipBudget);
        applyVisibility(pack);

        float y = inset;
        if (pack.rows > 0) {
            chipArea.layout(new Rect(inset, y, width, pack.height));
            y += pack.height + ROW_GAP;
        }
        field.layout(new Rect(inset, y, width, fieldHeight));
        field.setEnabled(isEnabled());
    }

    /**
     * Restores the nested field and chip styles after the editor is attached.
     */
    @Override
    protected void onAttached() {
        applyTheme();
    }

    /**
     * Reapplies the theme colors after a context change.
     */
    @Override
    protected void onContextChanged() {
        applyTheme();
    }

    /**
     * Keeps the nested field and chips in sync with the editor enabled state.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        field.setEnabled(isEnabled());
        for (Chip chip : chips) {
            chip.setEnabled(isEnabled());
        }
        overflowChip.setEnabled(isEnabled());
    }

    /**
     * Focuses the nested field when the pointer presses an empty part of the surface.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the press focuses the field
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT || !isEnabled()) {
            return false;
        }
        focusField();
        return true;
    }

    private void commitField() {
        String pending = field.text();
        if (pending.isEmpty()) {
            return;
        }
        if (addTag(pending)) {
            field.setText("");
        }
    }

    private void focusField() {
        UiContext uiContext = context();
        if (uiContext != null && isEnabled()) {
            uiContext.requestFocus(field);
        }
    }

    private void removeLastTag() {
        if (!tags.isEmpty()) {
            removeTag(tags.get(tags.size() - 1));
        }
    }

    private boolean isValid(String tag) {
        return validator == null || validator.test(tag);
    }

    private void notifyTagsChanged() {
        if (onTagsChanged != null) {
            onTagsChanged.accept(List.copyOf(tags));
        }
    }

    private void rebuildChips() {
        chipArea.removeAll();
        chips.clear();
        for (String tag : tags) {
            Chip chip = new Chip(tag, true);
            chips.add(chip);
            chipArea.add(chip);
        }
        overflowChip = new Chip("", false);
        chipArea.add(overflowChip);
        applyTheme();
    }

    private void applyVisibility(Pack pack) {
        for (int index = 0; index < chips.size(); index++) {
            chips.get(index).setVisible(index < pack.visible);
        }
        overflowChip.setText("+" + pack.hidden);
        overflowChip.setVisible(pack.hidden > 0 && pack.rows > 0);
        chipArea.setVisible(pack.rows > 0);
    }

    /**
     * Finds the largest visible tag count that fits the row limit and the height budget.
     * A negative height budget means the height is unbounded, which is used during measurement.
     */
    private Pack plan(float availableWidth, float heightBudget) {
        int total = tags.size();
        for (int visible = total; visible >= 0; visible--) {
            int hidden = total - visible;
            if (hidden > 0) {
                overflowChip.setText("+" + hidden);
            }
            Pack pack = pack(visible, hidden > 0, availableWidth);
            if (!fitsWidth(visible, hidden > 0, availableWidth)) {
                continue;
            }
            if (maxRows > 0 && pack.rows > maxRows) {
                continue;
            }
            if (heightBudget >= 0 && pack.height > heightBudget) {
                continue;
            }
            return pack.withHidden(hidden);
        }
        return new Pack(0, 0, 0, 0, 0);
    }

    private boolean fitsWidth(int visible, boolean overflow, float availableWidth) {
        for (int index = 0; index < visible; index++) {
            if (chipSize(chips.get(index)).width() > availableWidth) {
                return false;
            }
        }
        return !overflow || chipSize(overflowChip).width() <= availableWidth;
    }

    private Pack pack(int visible, boolean overflow, float availableWidth) {
        float lineWidth = 0;
        float lineHeight = 0;
        float widestLine = 0;
        float totalHeight = 0;
        int rows = 0;
        boolean lineEmpty = true;
        for (int index = 0; index < visible; index++) {
            Size size = chipSize(chips.get(index));
            if (!lineEmpty && lineWidth + CHIP_GAP + size.width() > availableWidth) {
                widestLine = Math.max(widestLine, lineWidth);
                totalHeight += lineHeight + ROW_GAP;
                rows++;
                lineWidth = 0;
                lineHeight = 0;
                lineEmpty = true;
            }
            if (!lineEmpty) {
                lineWidth += CHIP_GAP;
            }
            lineWidth += size.width();
            lineHeight = Math.max(lineHeight, size.height());
            lineEmpty = false;
        }
        if (overflow) {
            Size size = chipSize(overflowChip);
            if (!lineEmpty && lineWidth + CHIP_GAP + size.width() > availableWidth) {
                widestLine = Math.max(widestLine, lineWidth);
                totalHeight += lineHeight + ROW_GAP;
                rows++;
                lineWidth = 0;
                lineHeight = 0;
            } else if (!lineEmpty) {
                lineWidth += CHIP_GAP;
            }
            lineWidth += size.width();
            lineHeight = Math.max(lineHeight, size.height());
            lineEmpty = false;
        }
        if (!lineEmpty) {
            widestLine = Math.max(widestLine, lineWidth);
            totalHeight += lineHeight;
            rows++;
        }
        return new Pack(visible, 0, widestLine, totalHeight, rows);
    }

    private static Size chipSize(Chip chip) {
        return chip.measure(LayoutConstraints.loose(Float.MAX_VALUE, Float.MAX_VALUE));
    }

    private float inset() {
        WidgetStyle style = surfaceStyle();
        return style == null ? 0 : style.padding();
    }

    private void applyTheme() {
        UiContext uiContext = context();
        if (uiContext == null) {
            return;
        }
        Theme theme = uiContext.theme();
        field.setStyle(new WidgetStyle(Color.TRANSPARENT, Color.TRANSPARENT,
                Color.TRANSPARENT, Color.TRANSPARENT, theme.text(),
                theme.textMuted().withAlpha(0.6f), Color.TRANSPARENT, Color.TRANSPARENT,
                0, 0, 0));
        WidgetStyle chipStyle = chipStyle(theme);
        for (Chip chip : chips) {
            chip.setStyle(chipStyle);
        }
        overflowChip.setStyle(chipStyle);
    }

    private static WidgetStyle chipStyle(Theme theme) {
        Color background = Color.mix(theme.surface(), theme.primary(), 0.2f);
        Color hover = Color.mix(theme.surfaceHover(), theme.primary(), 0.3f);
        Color pressed = Color.mix(theme.surfacePressed(), theme.primary(), 0.4f);
        return new WidgetStyle(background, hover, pressed, theme.surface(), theme.text(),
                theme.textMuted(), theme.primary().withAlpha(0.35f), theme.focusRing(),
                6, 6, 1);
    }

    private static String normalize(String tag) {
        String trimmed = tag.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Pack(int visible, int hidden, float width, float height, int rows) {
        private Pack withHidden(int hidden) {
            return new Pack(visible, hidden, width, height, rows);
        }
    }

    private final class TagField extends Input {
        /**
         * Removes the last tag when Backspace is pressed in an empty field.
         *
         * @param key pressed key
         * @param modifiers active modifiers
         * @param repeat true for a repeated key press
         * @return true when the key is consumed
         */
        @Override
        protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
            if (key == Key.BACKSPACE && text().isEmpty() && !hasSelection()
                    && !tags.isEmpty()) {
                removeLastTag();
                return true;
            }
            return super.onKeyPressed(key, modifiers, repeat);
        }
    }

    private final class Chip extends Button {
        private final String tag;
        private final boolean removable;

        private Chip(String tag, boolean removable) {
            super(tag);
            this.tag = tag;
            this.removable = removable;
            setFocusable(false);
        }

        /**
         * Reserves the trailing slot for the remove cross.
         *
         * @return the cross slot width, or zero for the overflow chip
         */
        @Override
        protected float trailingSlotWidth() {
            return removable ? CROSS_SLOT : 0;
        }

        /**
         * Paints the chip surface, label and the remove cross.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            super.onDraw(renderer, frame);
            if (!removable || localBounds().isEmpty()) {
                return;
            }
            WidgetStyle style = surfaceStyle();
            if (style == null) {
                return;
            }
            float centerX = localBounds().width() - CROSS_SLOT / 2;
            float centerY = localBounds().height() / 2;
            Color color = isHovered() || isPressed() ? style.text(state()) : style.border(state());
            renderer.strokeLine(new Point(centerX - CROSS_MARK, centerY - CROSS_MARK),
                    new Point(centerX + CROSS_MARK, centerY + CROSS_MARK),
                    CROSS_STROKE, Paint.solid(color));
            renderer.strokeLine(new Point(centerX - CROSS_MARK, centerY + CROSS_MARK),
                    new Point(centerX + CROSS_MARK, centerY - CROSS_MARK),
                    CROSS_STROKE, Paint.solid(color));
        }

        /**
         * Removes the tag on a cross click and focuses the field on a body click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the click is consumed
         */
        @Override
        protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            focusField();
            if (removable && x >= localBounds().width() - CROSS_SLOT) {
                removeTag(tag);
            }
            return true;
        }

        /**
         * Treats a double click like a single click.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button clicked button
         * @param modifiers active modifiers
         * @return true when the click is consumed
         */
        @Override
        protected boolean onDoubleClick(float x, float y, MouseButton button,
                                        Modifiers modifiers) {
            return onClick(x, y, button, modifiers);
        }
    }
}
