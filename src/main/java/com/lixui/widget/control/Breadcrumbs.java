package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
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
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A horizontal breadcrumb trail with clickable segments and icon separators.
 * When the available width is too small, middle segments collapse into an ellipsis while the first
 * and last segments remain visible. Segments without an action are shown as the current location.
 */
public class Breadcrumbs extends Widget {
    private static final float DEFAULT_SEPARATOR_SIZE = 12;
    private static final float SEPARATOR_GAP = 6;
    private static final float CRUMB_PADDING = 4;
    private static final float VERTICAL_PADDING = 4;

    private final List<Crumb> crumbs = new ArrayList<>();
    private final List<Segment> segments = new ArrayList<>();
    private final IconSet icons = new IconSet("arrow-right-04.svg");

    private float separatorSize = DEFAULT_SEPARATOR_SIZE;
    private int hovered = -1;
    private Consumer<Crumb> onSelect;

    /**
     * Creates an empty breadcrumb trail.
     */
    public Breadcrumbs() {
        setClipToBounds(true);
    }

    /**
     * Returns the crumbs in trail order.
     *
     * @return an immutable snapshot of the crumbs
     */
    public final List<Crumb> crumbs() {
        return List.copyOf(crumbs);
    }

    /**
     * Appends a crumb with an action.
     *
     * @param label crumb label
     * @param action action run on activation, or null for the current location
     * @return the added crumb
     * @throws NullPointerException if {@code label} is null
     */
    public final Crumb addCrumb(String label, Runnable action) {
        return addCrumb(new Crumb(label, action));
    }

    /**
     * Appends a non-clickable crumb.
     *
     * @param label crumb label
     * @return the added crumb
     * @throws NullPointerException if {@code label} is null
     */
    public final Crumb addCrumb(String label) {
        return addCrumb(label, null);
    }

    /**
     * Appends an existing crumb.
     *
     * @param crumb crumb to append
     * @return the added crumb
     * @throws NullPointerException if {@code crumb} is null
     */
    public final Crumb addCrumb(Crumb crumb) {
        crumbs.add(Objects.requireNonNull(crumb, "crumb"));
        return crumb;
    }

    /**
     * Removes a crumb by reference.
     *
     * @param crumb crumb to remove
     * @return true when the crumb was present
     * @throws NullPointerException if {@code crumb} is null
     */
    public final boolean removeCrumb(Crumb crumb) {
        return crumbs.remove(Objects.requireNonNull(crumb, "crumb"));
    }

    /**
     * Removes every crumb.
     */
    public final void clearCrumbs() {
        crumbs.clear();
        segments.clear();
        hovered = -1;
    }

    /**
     * Returns the separator icon edge length.
     *
     * @return the separator size in logical UI units
     */
    public final float separatorSize() {
        return separatorSize;
    }

    /**
     * Sets the separator icon edge length.
     *
     * @param separatorSize positive separator size in logical UI units
     * @throws IllegalArgumentException if the size is non-finite or not positive
     */
    public final void setSeparatorSize(float separatorSize) {
        if (!Float.isFinite(separatorSize) || separatorSize <= 0) {
            throw new IllegalArgumentException("Separator size must be finite and positive.");
        }
        this.separatorSize = separatorSize;
    }

    /**
     * Returns the callback invoked for every activated crumb.
     *
     * @return the selection callback, or null when none is assigned
     */
    public final Consumer<Crumb> onSelect() {
        return onSelect;
    }

    /**
     * Assigns a callback invoked before the activated crumb action runs.
     *
     * @param onSelect selection callback, or null to clear it
     */
    public final void setOnSelect(Consumer<Crumb> onSelect) {
        this.onSelect = onSelect;
    }

    /**
     * Measures the complete trail and the text line height.
     *
     * @param constraints available and required dimensions
     * @return the natural breadcrumb size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        Font font = font();
        float width = separatorSlot() * Math.max(0, crumbs.size() - 1);
        for (Crumb crumb : crumbs) {
            width += crumbWidth(crumb, font);
        }
        float height = (font == null ? 0 : font.lineHeight()) + VERTICAL_PADDING * 2;
        return new Size(width, height);
    }

    /**
     * Lays out the visible crumbs and separator gaps for the current width.
     */
    @Override
    protected void onLayout() {
        buildSegments();
    }

    /**
     * Paints separators, hover highlights and crumb labels.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (localBounds().isEmpty() || segments.isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        Font font = font();
        ensureIcons();
        for (int index = 0; index < segments.size(); index++) {
            Segment segment = segments.get(index);
            if (index > 0) {
                drawSeparator(renderer, theme, segment.bounds, segments.get(index - 1).bounds);
            }
            drawSegment(renderer, theme, font, segment);
        }
    }

    /**
     * Tracks the hovered clickable crumb.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so pointer movement can bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        Segment segment = segmentAt(x, y);
        hovered = segment != null && isClickable(segment.crumbIndex) ? segment.crumbIndex : -1;
        return false;
    }

    /**
     * Clears the crumb hover.
     */
    @Override
    protected void onPointerExit() {
        hovered = -1;
    }

    /**
     * Returns the pointer cursor over clickable crumbs.
     *
     * @return the pointer cursor over a clickable crumb, otherwise the default cursor
     */
    @Override
    public Cursor cursor() {
        return hovered >= 0 ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /**
     * Activates the crumb under a completed left click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when a clickable crumb was activated
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (!isEnabled() || button != MouseButton.LEFT) {
            return false;
        }
        Segment segment = segmentAt(x, y);
        if (segment == null || !isClickable(segment.crumbIndex)) {
            return false;
        }
        Crumb crumb = crumbs.get(segment.crumbIndex);
        if (onSelect != null) {
            onSelect.accept(crumb);
        }
        if (crumb.action() != null) {
            crumb.action().run();
        }
        return true;
    }

    /**
     * Releases separator icons when the trail leaves the tree.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        icons.release(context());
    }

    /**
     * Drops separator icons after a density or theme change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        icons.release(context());
    }

    Rect crumbBounds(int index) {
        for (Segment segment : segments) {
            if (segment.crumbIndex == index) {
                return segment.bounds;
            }
        }
        return Rect.EMPTY;
    }

    private void buildSegments() {
        segments.clear();
        if (crumbs.isEmpty() || localBounds().isEmpty()) {
            hovered = -1;
            return;
        }
        if (!isClickable(hovered)) {
            hovered = -1;
        }
        float available = localBounds().width();
        Font font = font();
        int count = crumbs.size();
        if (count == 1) {
            segments.add(new Segment(0, new Rect(0, 0, Math.min(crumbWidth(crumbs.get(0), font),
                    available), localBounds().height())));
            return;
        }
        float separator = separatorSlot();
        float natural = separator * (count - 1);
        for (Crumb crumb : crumbs) {
            natural += crumbWidth(crumb, font);
        }
        float y = 0;
        float height = localBounds().height();
        if (natural <= available) {
            float x = 0;
            for (int index = 0; index < count; index++) {
                float width = crumbWidth(crumbs.get(index), font);
                segments.add(new Segment(index, new Rect(x, y, width, height)));
                x += width + separator;
            }
            return;
        }
        if (count >= 3) {
            float firstWidth = crumbWidth(crumbs.get(0), font);
            float lastWidth = crumbWidth(crumbs.get(count - 1), font);
            float ellipsisWidth = textWidth("…", font) + CRUMB_PADDING * 2;
            float fixed = firstWidth + lastWidth + ellipsisWidth + separator * 2;
            if (fixed > available) {
                float remaining = Math.max(0, available - ellipsisWidth - separator * 2);
                firstWidth = remaining / 2;
                lastWidth = remaining - firstWidth;
            }
            float x = 0;
            segments.add(new Segment(0, new Rect(x, y, firstWidth, height)));
            x += firstWidth + separator;
            segments.add(new Segment(-1, new Rect(x, y, ellipsisWidth, height)));
            x += ellipsisWidth + separator;
            segments.add(new Segment(count - 1, new Rect(x, y, lastWidth, height)));
            return;
        }
        float remaining = Math.max(0, available - separator);
        float firstWidth = remaining / 2;
        float secondWidth = remaining - firstWidth;
        segments.add(new Segment(0, new Rect(0, y, firstWidth, height)));
        segments.add(new Segment(1, new Rect(firstWidth + separator, y, secondWidth, height)));
    }

    private void drawSegment(RenderManager renderer, Theme theme, Font font, Segment segment) {
        if (segment.bounds.width() <= 0 || font == null) {
            return;
        }
        boolean ellipsis = segment.crumbIndex < 0;
        boolean clickable = isClickable(segment.crumbIndex);
        boolean active = clickable && hovered == segment.crumbIndex;
        if (active) {
            renderer.roundedRect(new Rect(segment.bounds.x(), segment.bounds.y() + 2,
                    segment.bounds.width(), Math.max(0, segment.bounds.height() - 4)), 4,
                    theme.primary().withAlpha(0.14f));
        }
        Color color;
        if (!isEnabled()) {
            color = theme.textMuted();
        } else if (ellipsis || !clickable) {
            color = theme.textMuted();
        } else {
            color = active ? theme.text() : theme.primary();
        }
        String label = ellipsis ? "…" : crumbs.get(segment.crumbIndex).label();
        float width = Math.max(0, segment.bounds.width() - CRUMB_PADDING * 2);
        if (width > 0) {
            renderer.text(font, label, segment.bounds.x() + CRUMB_PADDING,
                    segment.bounds.y() + segment.bounds.height() / 2, color,
                    new TextStyle(width, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
    }

    private void drawSeparator(RenderManager renderer, Theme theme, Rect bounds, Rect previous) {
        float centerX = (previous.right() + bounds.x()) / 2;
        float side = Math.min(separatorSize, bounds.height());
        if (side <= 0) {
            return;
        }
        Color color = isEnabled() ? theme.textMuted().withAlpha(0.7f) : theme.textMuted();
        renderer.icon(icons.get(0), new Rect(centerX - side / 2, (bounds.height() - side) / 2,
                side, side), ImageStyle.ICON, color);
    }

    private Segment segmentAt(float x, float y) {
        for (Segment segment : segments) {
            if (segment.bounds.contains(x, y)) {
                return segment;
            }
        }
        return null;
    }

    private boolean isClickable(int index) {
        return index >= 0 && index < crumbs.size() && crumbs.get(index).action() != null;
    }

    private float crumbWidth(Crumb crumb, Font font) {
        return textWidth(crumb.label(), font) + CRUMB_PADDING * 2;
    }

    private float textWidth(String text, Font font) {
        return font == null ? 0 : font.measure(text).width();
    }

    private float separatorSlot() {
        return separatorSize + SEPARATOR_GAP * 2;
    }

    private void ensureIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(separatorSize * context.density()));
        if (!icons.matches(context, size)) {
            icons.reload(context, size);
        }
    }

    private record Segment(int crumbIndex, Rect bounds) {
    }

    /**
     * A single breadcrumb label and its optional action.
     */
    public static final class Crumb {
        private String label;
        private Runnable action;

        /**
         * Creates a crumb.
         *
         * @param label crumb label
         * @param action action run on activation, or null for the current location
         * @throws NullPointerException if {@code label} is null
         */
        public Crumb(String label, Runnable action) {
            this.label = Objects.requireNonNull(label, "label");
            this.action = action;
        }

        /**
         * Returns the crumb label.
         *
         * @return the label, never null
         */
        public String label() {
            return label;
        }

        /**
         * Sets the crumb label.
         *
         * @param label crumb label
         * @throws NullPointerException if {@code label} is null
         */
        public void setLabel(String label) {
            this.label = Objects.requireNonNull(label, "label");
        }

        /**
         * Returns the crumb action.
         *
         * @return the action, or null for a non-clickable crumb
         */
        public Runnable action() {
            return action;
        }

        /**
         * Sets the crumb action.
         *
         * @param action action run on activation, or null to make the crumb non-clickable
         */
        public void setAction(Runnable action) {
            this.action = action;
        }
    }
}
