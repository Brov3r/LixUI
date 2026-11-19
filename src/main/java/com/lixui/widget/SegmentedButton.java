package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A single button surface split into several adjacent segments with vertical dividers.
 * Every segment can carry a label, an optional icon and its own action, so the control looks like
 * one large button made of smaller ones. Segment actions run on click; the widget reports the
 * hovered segment through its text colors only.
 */
public class SegmentedButton extends Panel {
    private static final float DEFAULT_SEGMENT_PADDING = 12;
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float DEFAULT_ICON_GAP = 6;
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);

    private final List<Segment> segments = new ArrayList<>();
    private float segmentPadding = DEFAULT_SEGMENT_PADDING;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float iconGap = DEFAULT_ICON_GAP;
    /**
     * How clicking a segment changes the selection.
     */
    public enum SelectionMode {
        /** Clicks only run segment actions. */
        NONE,
        /** Like radio buttons: one segment stays selected. */
        SINGLE,
        /** Like check boxes: every segment toggles independently. */
        MULTIPLE
    }

    private boolean showIcons = true;
    private int hoveredIndex = -1;
    private int pressedIndex = -1;
    private SelectionMode selectionMode = SelectionMode.NONE;
    private final List<Integer> selectedIndices = new ArrayList<>();
    private IntConsumer onSelectionChange;
    private float[] segmentStarts = new float[0];
    private float[] segmentWidths = new float[0];

    /**
     * Creates an empty segmented button.
     */
    public SegmentedButton() {
    }

    /**
     * Appends a segment with a label.
     *
     * @param text segment label
     * @return the created segment
     * @throws NullPointerException if {@code text} is null
     */
    public final Segment addSegment(String text) {
        return addSegment(text, null);
    }

    /**
     * Appends a segment with a label and an icon.
     *
     * @param text segment label
     * @param icon segment icon, or null for a text-only segment
     * @return the created segment
     * @throws NullPointerException if {@code text} is null
     */
    public final Segment addSegment(String text, Texture icon) {
        Segment segment = new Segment(text, icon);
        segments.add(segment);
        return segment;
    }

    /**
     * Returns the segments in display order.
     *
     * @return an immutable snapshot of the segments
     */
    public final List<Segment> segments() {
        return List.copyOf(segments);
    }

    /**
     * Removes a segment.
     *
     * @param segment segment to remove
     * @return true when the segment belonged to this widget
     * @throws NullPointerException if {@code segment} is null
     */
    public final boolean removeSegment(Segment segment) {
        Objects.requireNonNull(segment, "segment");
        int index = segments.indexOf(segment);
        if (index < 0) {
            return false;
        }
        segments.remove(index);
        hoveredIndex = -1;
        pressedIndex = -1;
        if (selectedIndices.remove(Integer.valueOf(index))) {
            notifySelection(index);
        }
        for (int position = 0; position < selectedIndices.size(); position++) {
            int selected = selectedIndices.get(position);
            if (selected > index) {
                selectedIndices.set(position, selected - 1);
            }
        }
        return true;
    }

    /**
     * Removes every segment.
     */
    public final void clearSegments() {
        segments.clear();
        hoveredIndex = -1;
        pressedIndex = -1;
        for (int index : List.copyOf(selectedIndices)) {
            selectedIndices.remove(Integer.valueOf(index));
            notifySelection(index);
        }
    }

    /**
     * Activates a segment action by index and updates the selection according to the mode.
     *
     * @param index segment index
     * @throws IllegalArgumentException if the index is out of range
     */
    public final void clickSegment(int index) {
        if (index < 0 || index >= segments.size()) {
            throw new IllegalArgumentException("Segment index is out of range.");
        }
        Segment segment = segments.get(index);
        if (!segment.enabled()) {
            return;
        }
        Runnable sound = segment.soundPlayer();
        if (sound != null) {
            sound.run();
        } else {
            playSound();
        }
        switch (selectionMode) {
            case SINGLE -> setSelected(index, true);
            case MULTIPLE -> setSelected(index, !isSelected(index));
            default -> {
            }
        }
        segment.activate();
    }

    /**
     * Returns how clicking a segment changes the selection.
     *
     * @return the selection mode
     */
    public final SelectionMode selectionMode() {
        return selectionMode;
    }

    /**
     * Sets how clicking a segment changes the selection.
     * Switching to {@link SelectionMode#NONE} clears the selection, and switching to
     * {@link SelectionMode#SINGLE} keeps the first selected segment.
     *
     * @param selectionMode selection mode
     * @throws NullPointerException if {@code selectionMode} is null
     */
    public final void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
        if (selectionMode == SelectionMode.NONE) {
            for (int index : List.copyOf(selectedIndices)) {
                selectedIndices.remove(Integer.valueOf(index));
                notifySelection(index);
            }
        } else if (selectionMode == SelectionMode.SINGLE && selectedIndices.size() > 1) {
            int keeper = selectedIndices.getFirst();
            for (int index : List.copyOf(selectedIndices)) {
                if (index != keeper) {
                    selectedIndices.remove(Integer.valueOf(index));
                    notifySelection(index);
                }
            }
        }
    }

    /**
     * Returns the selected segment indices in selection order.
     *
     * @return an immutable snapshot of the selected indices
     */
    public final List<Integer> selectedIndices() {
        return List.copyOf(selectedIndices);
    }

    /**
     * Returns the first selected segment index.
     *
     * @return the selected index, or -1 when nothing is selected
     */
    public final int selectedIndex() {
        return selectedIndices.isEmpty() ? -1 : selectedIndices.getFirst();
    }

    /**
     * Reports whether a segment is selected.
     *
     * @param index segment index
     * @return true when the segment is selected
     * @throws IllegalArgumentException if the index is out of range
     */
    public final boolean isSelected(int index) {
        if (index < 0 || index >= segments.size()) {
            throw new IllegalArgumentException("Segment index is out of range.");
        }
        return selectedIndices.contains(index);
    }

    /**
     * Selects or clears a segment.
     * In {@link SelectionMode#SINGLE} mode selecting a segment clears the previous one.
     *
     * @param index segment index
     * @param selected true to select the segment, false to clear it
     * @throws IllegalArgumentException if the index is out of range
     */
    public final void setSelected(int index, boolean selected) {
        if (index < 0 || index >= segments.size()) {
            throw new IllegalArgumentException("Segment index is out of range.");
        }
        if (selected) {
            if (selectedIndices.contains(index)) {
                return;
            }
            if (selectionMode == SelectionMode.SINGLE) {
                for (int other : List.copyOf(selectedIndices)) {
                    selectedIndices.remove(Integer.valueOf(other));
                    notifySelection(other);
                }
            }
            selectedIndices.add(index);
            notifySelection(index);
        } else if (selectedIndices.remove(Integer.valueOf(index))) {
            notifySelection(index);
        }
    }

    /**
     * Selects a single segment or clears the selection.
     *
     * @param index segment index, or -1 to clear the selection
     * @throws IllegalArgumentException if the index is out of range
     */
    public final void setSelectedIndex(int index) {
        if (index < -1 || index >= segments.size()) {
            throw new IllegalArgumentException("Segment index is out of range.");
        }
        for (int other : List.copyOf(selectedIndices)) {
            selectedIndices.remove(Integer.valueOf(other));
            notifySelection(other);
        }
        if (index >= 0) {
            selectedIndices.add(index);
            notifySelection(index);
        }
    }

    /**
     * Returns the callback invoked when a segment selection changes.
     *
     * @return the selection callback, or null when none is assigned
     */
    public final IntConsumer onSelectionChange() {
        return onSelectionChange;
    }

    /**
     * Assigns a callback invoked for each segment whose selection changes.
     *
     * @param onSelectionChange selection callback, or null to clear it
     */
    public final void setOnSelectionChange(IntConsumer onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    private void notifySelection(int index) {
        if (onSelectionChange != null) {
            onSelectionChange.accept(index);
        }
    }

    /**
     * Returns the horizontal padding inside a segment.
     *
     * @return the padding in logical UI units
     */
    public final float segmentPadding() {
        return segmentPadding;
    }

    /**
     * Sets the horizontal padding inside a segment.
     *
     * @param segmentPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public final void setSegmentPadding(float segmentPadding) {
        if (!Float.isFinite(segmentPadding) || segmentPadding < 0) {
            throw new IllegalArgumentException("Segment padding must be finite and non-negative.");
        }
        this.segmentPadding = segmentPadding;
    }

    /**
     * Returns the segment icon edge length.
     *
     * @return the icon size in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the segment icon edge length.
     *
     * @param iconSize positive size in logical UI units
     * @throws IllegalArgumentException if the size is not positive or non-finite
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the gap between a segment icon and its label.
     *
     * @return the gap in logical UI units
     */
    public final float iconGap() {
        return iconGap;
    }

    /**
     * Sets the gap between a segment icon and its label.
     *
     * @param iconGap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public final void setIconGap(float iconGap) {
        if (!Float.isFinite(iconGap) || iconGap < 0) {
            throw new IllegalArgumentException("Icon gap must be finite and non-negative.");
        }
        this.iconGap = iconGap;
    }

    /**
     * Reports whether segment icons are drawn.
     *
     * @return true when icons are shown
     */
    public final boolean showIcons() {
        return showIcons;
    }

    /**
     * Shows or hides every segment icon without removing it.
     * Hidden icons take no space in the measurement.
     *
     * @param showIcons true to draw segment icons
     */
    public final void setShowIcons(boolean showIcons) {
        this.showIcons = showIcons;
    }

    private boolean hasIcon(Segment segment) {
        return showIcons && segment.icon != null;
    }

    private WidgetStyle resolvedStyle() {
        if (hasLocalStyle()) {
            return style();
        }
        return context() == null ? null : context().theme().controlStyle();
    }

    /**
     * Returns the control style so the segmented button looks like a button.
     *
     * @return the context control style, or null when the widget is not attached
     */
    @Override
    protected WidgetStyle defaultStyle() {
        return context() == null ? null : context().theme().controlStyle();
    }

    /**
     * Measures the segments and the outer style padding.
     *
     * @param constraints available and required dimensions
     * @return the natural segmented button size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        WidgetStyle style = resolvedStyle();
        if (style == null) {
            return super.onMeasure(constraints);
        }
        Font font = font();
        float contentWidth = 0;
        float contentHeight = 0;
        for (Segment segment : segments) {
            Size size = segmentContentSize(segment, font);
            contentWidth += size.width() + segmentPadding * 2;
            contentHeight = Math.max(contentHeight, size.height());
        }
        return new Size(contentWidth, contentHeight + style.padding() * 2);
    }

    /**
     * Computes segment boundaries for hit testing and drawing.
     */
    @Override
    protected void onLayout() {
        Font font = font();
        float width = localBounds().width();
        float available = width;
        float total = 0;
        for (Segment segment : segments) {
            total += segmentContentSize(segment, font).width() + segmentPadding * 2;
        }
        float scale = total > 0 && total < available ? available / total : 1;
        segmentStarts = new float[segments.size()];
        segmentWidths = new float[segments.size()];
        float position = 0;
        for (int index = 0; index < segments.size(); index++) {
            float segmentWidth = (segmentContentSize(segments.get(index), font).width()
                    + segmentPadding * 2) * scale;
            segmentStarts[index] = position;
            segmentWidths[index] = segmentWidth;
            position += segmentWidth;
        }
    }

    /**
     * Paints the button surface, dividers and segment contents.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        WidgetStyle style = resolvedStyle();
        if (style == null || localBounds().isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        drawSurface(renderer, style);
        if (segments.isEmpty()) {
            return;
        }
        Font font = font();
        float height = localBounds().height();
        drawSelection(renderer, theme, height);
        drawSegmentHighlight(renderer, style, height);
        float dividerInset = height * 0.2f;
        for (int index = 1; index < segments.size(); index++) {
            renderer.rect(new Rect(segmentStarts[index], dividerInset, 1,
                    height - dividerInset * 2), style.border(WidgetState.NORMAL));
        }
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            float segmentWidth = segmentWidths[index];
            float centerX = segmentStarts[index] + segmentWidth / 2;
            float centerY = height / 2;
            Size content = segmentContentSize(segment, font);
            float startX = centerX - content.width() / 2;
            boolean hasText = font != null && !segment.text.isEmpty();
            boolean hasIcon = hasIcon(segment);
            float textX = hasIcon && hasText ? startX + iconSize + iconGap : startX;
            Color color = segmentColor(segment, style, index);
            if (hasIcon) {
                renderer.image(segment.icon,
                        new Rect(startX, centerY - iconSize / 2, iconSize, iconSize),
                        0, FULL_UV, color);
            }
            if (hasText) {
                renderer.text(font, segment.text, textX, centerY, color,
                        HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            }
        }
    }

    private void drawSurface(RenderManager renderer, WidgetStyle style) {
        WidgetState widgetState = isEnabled() ? WidgetState.NORMAL : WidgetState.DISABLED;
        float radius = style.cornerRadius();
        float borderWidth = style.borderWidth();
        if (borderWidth <= 0) {
            renderer.roundedRect(localBounds(), radius, style.background(widgetState));
            return;
        }
        renderer.roundedRect(localBounds(), radius, style.border(widgetState));
        Rect inner = new Rect(localBounds().x() + borderWidth, localBounds().y() + borderWidth,
                Math.max(0, localBounds().width() - borderWidth * 2),
                Math.max(0, localBounds().height() - borderWidth * 2));
        if (!inner.isEmpty()) {
            renderer.roundedRect(inner, Math.max(0, radius - borderWidth),
                    style.background(widgetState));
        }
    }

    private void drawSelection(RenderManager renderer, Theme theme, float height) {
        if (!isEnabled()) {
            return;
        }
        float radius = Math.min(context().theme().controlStyle().cornerRadius(), height / 2);
        for (int index : selectedIndices) {
            if (index < 0 || index >= segments.size()) {
                continue;
            }
            Rect highlight = new Rect(segmentStarts[index] + 2, 2,
                    Math.max(0, segmentWidths[index] - 4), Math.max(0, height - 4));
            if (highlight.isEmpty()) {
                continue;
            }
            renderer.roundedRect(highlight,
                    Math.min(radius, Math.min(highlight.width(), highlight.height()) / 2),
                    theme.primary().withAlpha(0.22f));
        }
    }

    private void drawSegmentHighlight(RenderManager renderer, WidgetStyle style, float height) {
        if (!isEnabled()) {
            return;
        }
        int index = pressedIndex >= 0 ? pressedIndex : hoveredIndex;
        if (index < 0 || index >= segments.size() || !segments.get(index).enabled()) {
            return;
        }
        float width = segmentWidths[index];
        Rect highlight = new Rect(segmentStarts[index] + 2, 2,
                Math.max(0, width - 4), Math.max(0, height - 4));
        if (highlight.isEmpty()) {
            return;
        }
        WidgetState state = pressedIndex == index ? WidgetState.PRESSED : WidgetState.HOVERED;
        float radius = Math.min(style.cornerRadius(),
                Math.min(highlight.width(), highlight.height()) / 2);
        renderer.roundedRect(highlight, radius, style.background(state));
    }

    /**
     * Returns the cursor for segments that react to input.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Updates the hovered segment.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        hoveredIndex = segmentAt(x);
        return false;
    }

    /**
     * Clears the hovered segment.
     */
    @Override
    protected void onPointerExit() {
        hoveredIndex = -1;
        pressedIndex = -1;
    }

    /**
     * Remembers the pressed segment for the pressed highlight.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press is consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT && isEnabled()) {
            pressedIndex = segmentAt(x);
        }
        return true;
    }

    /**
     * Clears the pressed segment highlight.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button released button
     * @param modifiers active modifiers
     * @return true when the release is consumed
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
        pressedIndex = -1;
        return true;
    }

    /**
     * Runs the action of the clicked segment.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click is consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        int index = segmentAt(x);
        if (index >= 0) {
            clickSegment(index);
        }
        return true;
    }

    /**
     * Routes a double click through the same segment activation as a single click.
     *
     * @param x horizontal pointer position
     * @param y vertical pointer position
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click is consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return onClick(x, y, button, modifiers);
    }

    private int segmentAt(float x) {
        for (int index = 0; index < segmentStarts.length; index++) {
            if (x >= segmentStarts[index]
                    && x < segmentStarts[index] + segmentWidths[index]) {
                return index;
            }
        }
        return -1;
    }

    private Color segmentColor(Segment segment, WidgetStyle style, int index) {
        if (!segment.enabled || !isEnabled()) {
            return style.text(WidgetState.DISABLED);
        }
        if (selectedIndices.contains(index)) {
            return context().theme().primary();
        }
        if (index == hoveredIndex && isHovered()) {
            return style.text(WidgetState.NORMAL);
        }
        return context().theme().textMuted();
    }

    private Size segmentContentSize(Segment segment, Font font) {
        boolean hasText = font != null && !segment.text.isEmpty();
        float textWidth = hasText ? font.measure(segment.text).width() : 0;
        float textHeight = hasText ? font.measure(segment.text).height() : 0;
        float width = textWidth;
        boolean hasIcon = hasIcon(segment);
        if (hasIcon) {
            width += (hasText ? iconGap : 0) + iconSize;
        }
        return new Size(width, Math.max(textHeight, hasIcon ? iconSize : 0));
    }

    /**
     * A single clickable region of the segmented button.
     */
    public static final class Segment {
        private String text;
        private Texture icon;
        private Runnable action;
        private Runnable soundPlayer;
        private boolean enabled = true;

        private Segment(String text, Texture icon) {
            this.text = Objects.requireNonNull(text, "text");
            this.icon = icon;
        }

        /**
         * Returns the segment label.
         *
         * @return the label text
         */
        public String text() {
            return text;
        }

        /**
         * Replaces the segment label.
         *
         * @param text new label
         * @throws NullPointerException if {@code text} is null
         */
        public void setText(String text) {
            this.text = Objects.requireNonNull(text, "text");
        }

        /**
         * Returns the segment icon.
         *
         * @return the icon texture, or null when the segment has no icon
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Assigns a segment icon.
         *
         * @param icon icon texture, or null to remove it
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Returns the action invoked by the segment.
         *
         * @return the action, or null when none is assigned
         */
        public Runnable action() {
            return action;
        }

        /**
         * Assigns the action invoked by the segment.
         *
         * @param action segment action, or null to clear it
         */
        public void setOnAction(Runnable action) {
            this.action = action;
        }

        /**
         * Returns the sound callback invoked by this segment.
         *
         * @return the sound callback, or null when the widget default is used
         */
        public Runnable soundPlayer() {
            return soundPlayer;
        }

        /**
         * Assigns a sound callback invoked before this segment action.
         * Null falls back to the widget-level callback.
         *
         * @param soundPlayer sound callback, or null to inherit the widget callback
         */
        public void setSoundPlayer(Runnable soundPlayer) {
            this.soundPlayer = soundPlayer;
        }

        /**
         * Reports whether the segment reacts to input.
         *
         * @return true when the segment is enabled
         */
        public boolean enabled() {
            return enabled;
        }

        /**
         * Enables or disables the segment.
         *
         * @param enabled true to allow activation
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        private void activate() {
            if (action != null) {
                action.run();
            }
        }
    }
}
