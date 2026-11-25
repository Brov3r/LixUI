package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A compact page navigator with previous and next arrows and page number slots.
 * Page slots collapse into ellipses when they do not fit the configured maximum. Slots are drawn
 * and hit-tested directly, so the widget owns a single focus and pointer target. The page index is
 * zero-based; the user sees {@code page + 1}. Selecting a different page invokes the change callback.
 */
public class Pagination extends Widget {
    private static final int ELLIPSIS = -1;
    private static final int MIN_MAX_BUTTONS = 5;
    private static final int DEFAULT_MAX_BUTTONS = 7;
    private static final float DEFAULT_CELL_HEIGHT = 28;
    private static final float GAP = 4;
    private static final float TEXT_PADDING = 16;
    private static final float MIN_CELL_HEIGHT = 24;
    private static final float VERTICAL_TEXT_PADDING = 8;
    private static final float MAX_CORNER_RADIUS = 6;
    private static final float ARROW_ICON_RATIO = 0.55f;
    private static final float DISABLED_ALPHA = 0.4f;
    private static final float HOVER_MIX = 0.18f;
    private static final TextStyle CENTERED = TextStyle.of(
            HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

    private final List<Integer> slots = new ArrayList<>();
    private final IconSet arrows = new IconSet("arrow-left-04.svg", "arrow-right-04.svg");
    private int page;
    private int pageCount;
    private int maxButtons = DEFAULT_MAX_BUTTONS;
    private boolean slotsValid;
    private int hoveredIndex = -1;
    private int pressedIndex = -1;
    private IntConsumer onChange;

    /**
     * Creates an empty pagination without pages.
     */
    public Pagination() {
    }

    /**
     * Returns the selected zero-based page index.
     * The value is zero when there are no pages.
     *
     * @return the selected page index
     */
    public final int page() {
        return page;
    }

    /**
     * Selects a page, clamping the index into the available range.
     * The change callback runs when the page actually changes.
     *
     * @param page zero-based page index
     */
    public final void setPage(int page) {
        int target = clampPage(page, pageCount);
        if (target == this.page) {
            return;
        }
        this.page = target;
        invalidateSlots();
        notifyChange();
    }

    /**
     * Returns the number of pages.
     *
     * @return the page count, or zero when the widget is empty
     */
    public final int pageCount() {
        return pageCount;
    }

    /**
     * Sets the number of pages.
     * The selected page is clamped into the new range, and the change callback runs when the clamp
     * changes it.
     *
     * @param pageCount non-negative page count
     * @throws IllegalArgumentException if the count is negative
     */
    public final void setPageCount(int pageCount) {
        if (pageCount < 0) {
            throw new IllegalArgumentException("Page count must be non-negative.");
        }
        int previous = page;
        this.pageCount = pageCount;
        this.page = clampPage(this.page, pageCount);
        invalidateSlots();
        if (page != previous) {
            notifyChange();
        }
    }

    /**
     * Returns the maximum number of page slots, including ellipses.
     *
     * @return the slot limit
     */
    public final int maxButtons() {
        return maxButtons;
    }

    /**
     * Sets the maximum number of page slots, including ellipses.
     * Values below five cannot keep the first page, the current page and the last page visible.
     *
     * @param maxButtons slot limit of at least five
     * @throws IllegalArgumentException if the limit is below five
     */
    public final void setMaxButtons(int maxButtons) {
        if (maxButtons < MIN_MAX_BUTTONS) {
            throw new IllegalArgumentException("Maximum button count must be at least five.");
        }
        if (this.maxButtons == maxButtons) {
            return;
        }
        this.maxButtons = maxButtons;
        invalidateSlots();
    }

    /**
     * Returns the callback invoked when the selected page changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public final IntConsumer onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked with the zero-based page index when the selection changes.
     *
     * @param onChange change callback, or null to clear it
     */
    public final void setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
    }

    /**
     * Measures the arrows, page slots and gaps.
     *
     * @param constraints available and required dimensions
     * @return the natural pagination size, or an empty size without pages
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        ensureSlots();
        if (slots.isEmpty()) {
            return new Size(0, 0);
        }
        Font font = font();
        float height = cellHeight(font);
        float pageWidth = pageWidth(font, height);
        float width = height * 2 + pageWidth * slots.size() + GAP * (slots.size() + 1);
        return new Size(width, height);
    }

    /**
     * Paints the arrows, page slots and ellipses.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty()) {
            return;
        }
        ensureSlots();
        List<Rect> items = itemBounds(font());
        if (items.isEmpty()) {
            return;
        }
        ensureArrows(localBounds().height());
        Theme theme = context().theme();
        Font font = font();
        int last = items.size() - 1;
        drawArrow(renderer, theme, items.getFirst(), -1, page > 0, hoveredIndex == 0,
                pressedIndex == 0);
        for (int index = 0; index < slots.size(); index++) {
            drawPageCell(renderer, theme, font, items.get(index + 1), index + 1,
                    slots.get(index));
        }
        drawArrow(renderer, theme, items.get(last), 1, page < pageCount - 1,
                hoveredIndex == last, pressedIndex == last);
    }

    /**
     * Updates the hovered slot under the pointer.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        hoveredIndex = indexAt(x, y);
        return false;
    }

    /**
     * Clears the hovered and pressed slots when the pointer leaves.
     */
    @Override
    protected void onPointerExit() {
        hoveredIndex = -1;
        pressedIndex = -1;
    }

    /**
     * Remembers the slot pressed by the left pointer button.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when a slot was pressed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        pressedIndex = indexAt(x, y);
        hoveredIndex = pressedIndex;
        return pressedIndex >= 0;
    }

    /**
     * Clears the pressed slot after the pointer button is released.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return true when a slot was pressed
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
        boolean pressed = pressedIndex >= 0;
        pressedIndex = -1;
        return pressed;
    }

    /**
     * Moves to the page selected by a completed left click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click landed on a slot
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        List<Rect> items = itemBounds(font());
        int index = indexAt(x, y);
        if (index < 0) {
            return false;
        }
        pressedIndex = -1;
        int target;
        if (index == 0) {
            target = page - 1;
        } else if (index == items.size() - 1) {
            target = page + 1;
        } else {
            int slot = slots.get(index - 1);
            if (slot == ELLIPSIS) {
                return true;
            }
            target = slot;
        }
        setPage(target);
        return true;
    }

    private void drawPageCell(RenderManager renderer, Theme theme, Font font, Rect cell,
                              int index, int slot) {
        boolean current = slot == page;
        boolean ellipsis = slot == ELLIPSIS;
        boolean hovered = index == hoveredIndex;
        boolean pressed = index == pressedIndex;
        Color background = cellBackground(theme, current, ellipsis, hovered, pressed);
        if (background.a() > 0) {
            renderer.roundedRect(cell, Math.min(MAX_CORNER_RADIUS, cell.height() / 2), background);
        }
        if (font == null) {
            return;
        }
        Color textColor = current ? theme.onPrimary() : ellipsis ? theme.textMuted() : theme.text();
        String label = ellipsis ? "..." : Integer.toString(slot + 1);
        renderer.text(font, label, cell.x() + cell.width() / 2, cell.y() + cell.height() / 2,
                textColor, CENTERED);
    }

    private Color cellBackground(Theme theme, boolean current, boolean ellipsis,
                                 boolean hovered, boolean pressed) {
        if (current) {
            return hovered ? Color.mix(theme.primary(), theme.surfaceHover(), HOVER_MIX)
                    : theme.primary();
        }
        if (ellipsis) {
            return Color.TRANSPARENT;
        }
        if (pressed) {
            return theme.surfacePressed();
        }
        return hovered ? theme.surfaceHover() : Color.TRANSPARENT;
    }

    private void drawArrow(RenderManager renderer, Theme theme, Rect cell, int direction,
                           boolean enabled, boolean hovered, boolean pressed) {
        if (enabled && (hovered || pressed)) {
            Color background = pressed ? theme.surfacePressed() : theme.surfaceHover();
            renderer.roundedRect(cell, Math.min(MAX_CORNER_RADIUS, cell.height() / 2),
                    background);
        }
        Color color = enabled ? theme.text() : theme.textMuted().withAlpha(DISABLED_ALPHA);
        Texture icon = arrows.get(direction < 0 ? 0 : 1);
        if (icon == null) {
            return;
        }
        float size = Math.min(cell.width(), cell.height()) * ARROW_ICON_RATIO;
        float centerX = cell.x() + cell.width() / 2;
        float centerY = cell.y() + cell.height() / 2;
        renderer.icon(icon, new Rect(centerX - size / 2, centerY - size / 2, size, size),
                ImageStyle.FILL, color);
    }

    /**
     * Releases the shared arrow icons when the pagination detaches.
     */
    @Override
    protected void onDetached() {
        arrows.release(context());
    }

    /**
     * Drops cached arrow icons so they are reloaded at the new density or smoothing.
     */
    @Override
    protected void onContextChanged() {
        arrows.release(context());
    }

    private List<Rect> itemBounds(Font font) {
        List<Rect> items = new ArrayList<>(slots.size() + 2);
        float height = localBounds().height();
        if (slots.isEmpty() || height <= 0) {
            return items;
        }
        float pageWidth = pageWidth(font, height);
        float total = height * 2 + pageWidth * slots.size() + GAP * (slots.size() + 1);
        float x = (localBounds().width() - total) / 2;
        items.add(new Rect(x, 0, height, height));
        x += height + GAP;
        for (int index = 0; index < slots.size(); index++) {
            items.add(new Rect(x, 0, pageWidth, height));
            x += pageWidth + GAP;
        }
        items.add(new Rect(x, 0, height, height));
        return items;
    }

    private int indexAt(float x, float y) {
        List<Rect> items = itemBounds(font());
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    private float cellHeight(Font font) {
        return font == null ? DEFAULT_CELL_HEIGHT
                : Math.max(MIN_CELL_HEIGHT, font.lineHeight() + VERTICAL_TEXT_PADDING);
    }

    private float pageWidth(Font font, float height) {
        float textWidth = 0;
        if (font != null) {
            for (int slot : slots) {
                String label = slot == ELLIPSIS ? "..." : Integer.toString(slot + 1);
                textWidth = Math.max(textWidth, font.measure(label).width());
            }
        }
        return Math.max(height, textWidth + TEXT_PADDING);
    }

    private void ensureArrows(float cellHeight) {
        UiContext uiContext = context();
        if (uiContext == null) {
            return;
        }
        int size = Math.max(1,
                Math.round(cellHeight * ARROW_ICON_RATIO * uiContext.density()));
        if (!arrows.matches(uiContext, size)) {
            arrows.reload(uiContext, size);
        }
    }

    private void ensureSlots() {
        if (slotsValid) {
            return;
        }
        rebuildSlots();
    }

    private void invalidateSlots() {
        slotsValid = false;
        hoveredIndex = -1;
        pressedIndex = -1;
    }

    private void rebuildSlots() {
        slots.clear();
        if (pageCount > 0) {
            if (pageCount <= maxButtons) {
                for (int index = 0; index < pageCount; index++) {
                    slots.add(index);
                }
            } else {
                int inner = maxButtons - 2;
                int window = Math.max(1, maxButtons - 4);
                if (page <= inner - 2) {
                    for (int index = 0; index < inner; index++) {
                        slots.add(index);
                    }
                    slots.add(ELLIPSIS);
                    slots.add(pageCount - 1);
                } else if (page >= pageCount - inner + 1) {
                    slots.add(0);
                    slots.add(ELLIPSIS);
                    for (int index = pageCount - inner; index < pageCount; index++) {
                        slots.add(index);
                    }
                } else {
                    int start = Math.clamp(page - window / 2, 1, pageCount - window - 1);
                    slots.add(0);
                    slots.add(ELLIPSIS);
                    for (int index = start; index < start + window; index++) {
                        slots.add(index);
                    }
                    slots.add(ELLIPSIS);
                    slots.add(pageCount - 1);
                }
            }
        }
        slotsValid = true;
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(page);
        }
    }

    private static int clampPage(int page, int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }
        return Math.clamp(page, 0, pageCount - 1);
    }
}
