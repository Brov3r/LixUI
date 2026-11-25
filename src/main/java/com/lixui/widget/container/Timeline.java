package com.lixui.widget.container;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A virtualized vertical timeline with variable-height entries.
 * Every entry has a title, wrapped text, a timestamp and a marker color. A shared rail and round
 * markers connect the entries, and only the rows inside the viewport are drawn and hit-tested.
 * Clicking a row selects it and invokes the selection callback.
 */
public class Timeline extends Widget {
    /**
     * A timeline entry.
     *
     * @param title row title
     * @param text wrapped body text; an empty string hides the body
     * @param time right-aligned timestamp
     * @param color marker color, or null for the theme primary color
     */
    public record Entry(String title, String text, String time, Color color) {
        /**
         * Creates a timeline entry.
         *
         * @param title row title
         * @param text wrapped body text; an empty string hides the body
         * @param time right-aligned timestamp
         * @param color marker color, or null for the theme primary color
         * @throws NullPointerException if the title, text or timestamp is null
         */
        public Entry {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(time, "time");
        }
    }

    private static final float PADDING = 10;
    private static final float RAIL_WIDTH = 26;
    private static final float MARKER_RADIUS = 5;
    private static final float MARKER_INNER_RADIUS = 2;
    private static final float TITLE_GAP = 4;
    private static final float MIN_ROW_HEIGHT = 46;
    private static final float SCROLLBAR_RESERVE = 14;
    private static final float TIME_GAP = 8;
    private static final float FALLBACK_TITLE_HEIGHT = 18;

    private final List<Entry> entries = new ArrayList<>();
    private final VirtualList list = new VirtualList();

    private Consumer<Entry> selectionListener;
    private Entry selectedEntry;
    private float measuredWidth = -1;

    /**
     * Creates an empty timeline with scrolling and single-row selection enabled.
     */
    public Timeline() {
        setClipToBounds(true);
        list.setSelectionMode(VirtualList.SelectionMode.SINGLE);
        list.setRowHeightProvider(this::entryHeight);
        list.setRowRenderer(this::renderEntry);
        list.setSelectionListener(this::onListSelection);
        add(list);
    }

    /**
     * Appends an entry.
     *
     * @param entry entry to append
     * @return the appended entry
     * @throws NullPointerException if the entry is null
     */
    public Entry addEntry(Entry entry) {
        entries.add(Objects.requireNonNull(entry, "entry"));
        list.setItemCount(entries.size());
        list.invalidateRowHeights();
        return entry;
    }

    /**
     * Appends an entry with the supplied fields.
     *
     * @param title row title
     * @param text wrapped body text; an empty string hides the body
     * @param time right-aligned timestamp
     * @param color marker color, or null for the theme primary color
     * @return the appended entry
     * @throws NullPointerException if the title, text or timestamp is null
     */
    public Entry addEntry(String title, String text, String time, Color color) {
        return addEntry(new Entry(title, text, time, color));
    }

    /**
     * Returns the entries in display order.
     *
     * @return an immutable snapshot of the entries
     */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Removes every entry and clears the selection.
     */
    public void clearEntries() {
        entries.clear();
        list.setItemCount(0);
        list.invalidateRowHeights();
    }

    /**
     * Returns the selected entry.
     *
     * @return the selected entry, or null when the selection is empty
     */
    public Entry selectedEntry() {
        return selectedEntry;
    }

    /**
     * Selects an entry and scrolls it into view.
     *
     * @param entry entry to select
     * @throws NullPointerException if the entry is null
     * @throws IllegalArgumentException if the entry does not belong to this timeline
     */
    public void selectEntry(Entry entry) {
        Objects.requireNonNull(entry, "entry");
        int index = indexOf(entry);
        if (index < 0) {
            throw new IllegalArgumentException("Entry does not belong to this timeline.");
        }
        list.setSelectedIndices(List.of(index));
        list.scrollToRow(index);
    }

    /**
     * Scrolls the timeline so the supplied entry is visible.
     *
     * @param entry entry to reveal
     * @throws NullPointerException if the entry is null
     * @throws IllegalArgumentException if the entry does not belong to this timeline
     */
    public void scrollToEntry(Entry entry) {
        Objects.requireNonNull(entry, "entry");
        int index = indexOf(entry);
        if (index < 0) {
            throw new IllegalArgumentException("Entry does not belong to this timeline.");
        }
        list.scrollToRow(index);
    }

    /**
     * Returns the callback invoked after the selected entry changes.
     *
     * @return the selection listener, or null when none is assigned
     */
    public Consumer<Entry> selectionListener() {
        return selectionListener;
    }

    /**
     * Assigns a callback invoked after the selected entry changes.
     *
     * @param selectionListener listener receiving the selected entry or null, or null to clear it
     */
    public void setSelectionListener(Consumer<Entry> selectionListener) {
        this.selectionListener = selectionListener;
    }

    /**
     * Returns the entry under a local vertical position.
     *
     * @param localY vertical position relative to the timeline top-left corner
     * @return the entry under the position, or null outside the viewport rows
     */
    public Entry entryAt(float localY) {
        int index = list.rowAt(localY);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    /**
     * Returns the vertical scroll offset.
     *
     * @return the offset in logical UI units
     */
    public float scrollY() {
        return list.scrollY();
    }

    /**
     * Returns the total height of all entries.
     *
     * @return the content height in logical UI units
     */
    public float contentHeight() {
        return list.contentHeight();
    }

    /**
     * Lays the internal list over the whole timeline bounds and reflows text after width changes.
     */
    @Override
    protected void onLayout() {
        float width = bounds().width();
        if (width != measuredWidth) {
            measuredWidth = width;
            list.invalidateRowHeights();
        }
        list.layout(new Rect(0, 0, width, bounds().height()));
    }

    /**
     * Reflows wrapped text after a font, theme or display scale change.
     */
    @Override
    protected void onContextChanged() {
        list.invalidateRowHeights();
    }

    private float entryHeight(int index) {
        Font font = font();
        if (font == null) {
            return MIN_ROW_HEIGHT;
        }
        Entry entry = entries.get(index);
        float width = textWidth(entry, font);
        float titleHeight = Math.max(1, font.measure(entry.title(),
                new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                        HorizontalAlignment.LEFT, VerticalAlignment.TOP)).height());
        float textHeight = entry.text().isEmpty() ? 0
                : font.measure(entry.text(), new TextStyle(width, 0, 0, TextOverflow.CLIP,
                        HorizontalAlignment.LEFT, VerticalAlignment.TOP)).height();
        float height = PADDING * 2 + titleHeight;
        if (textHeight > 0) {
            height += TITLE_GAP + textHeight;
        }
        return Math.max(MIN_ROW_HEIGHT, height);
    }

    private void renderEntry(int index, Rect bounds, VirtualList.RowState state,
                             RenderManager renderer) {
        Entry entry = entries.get(index);
        Theme theme = context().theme();
        float railX = bounds.x() + PADDING + RAIL_WIDTH / 2;
        renderer.rect(new Rect(railX - 0.5f, bounds.y(), 1, bounds.height()),
                theme.border().withAlpha(0.55f));
        Font font = font();
        float top = bounds.y() + PADDING;
        float titleHeight = font == null ? FALLBACK_TITLE_HEIGHT
                : Math.max(1, font.measure(entry.title()).height());
        float markerY = top + titleHeight / 2;
        Color marker = entry.color() == null ? theme.primary() : entry.color();
        renderer.fillCircle(new Point(railX, markerY), MARKER_RADIUS, Paint.solid(marker));
        renderer.fillCircle(new Point(railX, markerY), MARKER_INNER_RADIUS,
                Paint.solid(theme.surface()));
        if (font == null) {
            return;
        }
        float right = bounds.right() - PADDING;
        renderer.text(font, entry.time(), right, top, theme.textMuted(),
                HorizontalAlignment.RIGHT, VerticalAlignment.TOP);
        float timeWidth = font.measure(entry.time()).width();
        float contentX = bounds.x() + PADDING + RAIL_WIDTH;
        float titleWidth = Math.max(0, right - timeWidth - TIME_GAP - contentX);
        if (titleWidth > 0) {
            renderer.text(font, entry.title(), contentX, top, theme.text(),
                    new TextStyle(titleWidth, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        }
        if (!entry.text().isEmpty() && titleWidth > 0) {
            renderer.text(font, entry.text(), contentX, top + titleHeight + TITLE_GAP,
                    theme.textMuted(),
                    new TextStyle(titleWidth, 0, 0, TextOverflow.CLIP,
                            HorizontalAlignment.LEFT, VerticalAlignment.TOP));
        }
    }

    private float textWidth(Entry entry, Font font) {
        return Math.max(1, textWidth() - TIME_GAP - font.measure(entry.time()).width());
    }

    private float textWidth() {
        return Math.max(40, list.bounds().width() - SCROLLBAR_RESERVE
                - PADDING * 2 - RAIL_WIDTH);
    }

    private void onListSelection(List<Integer> indices) {
        Entry next = indices.isEmpty() ? null : entries.get(indices.get(0));
        if (next == selectedEntry) {
            return;
        }
        selectedEntry = next;
        if (selectionListener != null) {
            selectionListener.accept(next);
        }
    }

    private int indexOf(Entry entry) {
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index) == entry) {
                return index;
            }
        }
        return -1;
    }
}
