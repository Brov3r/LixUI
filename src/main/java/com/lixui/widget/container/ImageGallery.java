package com.lixui.widget.container;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.ImageFit;
import com.lixui.render.RenderManager;
import com.lixui.theme.Theme;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.VerticalAlignment;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Image;
import com.lixui.widget.control.Label;
import com.lixui.widget.image.ImageLoader;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.Grid;
import com.lixui.widget.layout.GridTrack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A scrollable grid of image thumbnails with captions and single or multiple selection.
 * The gallery owns a {@link ScrollPanel}, a {@link Grid} content layer and one {@link Image} widget
 * per item. Cells highlight on hover and selection, clicking selects a cell, and Enter activates the
 * selected items. Clearing items or detaching the gallery releases image icon references and
 * cancels remote requests through the normal widget lifecycle.
 */
public class ImageGallery extends Widget {
    /**
     * The item selection behavior of a gallery.
     */
    public enum SelectionMode {
        /** At most one item is selected at a time. */
        SINGLE,
        /** Multiple items can be selected with Ctrl or Shift. */
        MULTIPLE
    }

    /**
     * A gallery item.
     *
     * @param source thumbnail image source
     * @param caption caption drawn below the thumbnail
     */
    public record Item(ImageSource source, String caption) {
        /**
         * Creates a gallery item.
         *
         * @param source thumbnail image source
         * @param caption caption drawn below the thumbnail
         * @throws NullPointerException if the source or caption is null
         */
        public Item {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(caption, "caption");
        }
    }

    private static final int DEFAULT_COLUMNS = 3;
    private static final float DEFAULT_THUMBNAIL_SIZE = 96;
    private static final float DEFAULT_GAP = 8;
    private static final float DEFAULT_PADDING = 8;
    private static final float SCROLLBAR_RESERVE = 12;
    private static final float CAPTION_HEIGHT = 18;
    private static final float CELL_INSET = 4;
    private static final float CELL_RADIUS = 6;
    private static final float IMAGE_RADIUS = 5;

    private final List<Item> items = new ArrayList<>();
    private final List<GalleryCell> cells = new ArrayList<>();
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final ScrollPanel scrollPanel = new ScrollPanel();
    private final Grid grid = new Grid();

    private SelectionMode selectionMode = SelectionMode.SINGLE;
    private Consumer<List<Integer>> selectionListener;
    private Consumer<List<Item>> activationListener;
    private ImageLoader imageLoader;
    private int columns = DEFAULT_COLUMNS;
    private float thumbnailSize = DEFAULT_THUMBNAIL_SIZE;
    private float gap = DEFAULT_GAP;
    private float padding = DEFAULT_PADDING;
    private int activeIndex = -1;
    private int anchorIndex = -1;

    /**
     * Creates an empty gallery with scrolling and single selection enabled.
     */
    public ImageGallery() {
        setClipToBounds(true);
        setFocusable(true);
        scrollPanel.setContentInsets(0, SCROLLBAR_RESERVE, 0, 0);
        scrollPanel.setScrollBarMargin(4);
        scrollPanel.setContent(grid);
        add(scrollPanel);
        applyGridConfiguration();
    }

    /**
     * Returns the number of columns.
     *
     * @return the positive column count
     */
    public int columns() {
        return columns;
    }

    /**
     * Sets the number of columns.
     *
     * @param columns positive column count
     * @throws IllegalArgumentException if the count is not positive
     */
    public void setColumns(int columns) {
        if (columns <= 0) {
            throw new IllegalArgumentException("Column count must be positive.");
        }
        this.columns = columns;
        applyGridConfiguration();
        refreshContentSize();
    }

    /**
     * Returns the preferred thumbnail height.
     *
     * @return the thumbnail size in logical UI units
     */
    public float thumbnailSize() {
        return thumbnailSize;
    }

    /**
     * Sets the preferred thumbnail height.
     *
     * @param thumbnailSize positive thumbnail size in logical UI units
     * @throws IllegalArgumentException if the size is not finite or not positive
     */
    public void setThumbnailSize(float thumbnailSize) {
        if (!Float.isFinite(thumbnailSize) || thumbnailSize <= 0) {
            throw new IllegalArgumentException("Thumbnail size must be finite and positive.");
        }
        this.thumbnailSize = thumbnailSize;
        refreshContentSize();
    }

    /**
     * Returns the gap between cells.
     *
     * @return the gap in logical UI units
     */
    public float gap() {
        return gap;
    }

    /**
     * Sets the gap between cells.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is negative or non-finite
     */
    public void setGap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Gap must be finite and non-negative.");
        }
        this.gap = gap;
        applyGridConfiguration();
        refreshContentSize();
    }

    /**
     * Returns the padding inside the scroll content.
     *
     * @return the padding in logical UI units
     */
    public float padding() {
        return padding;
    }

    /**
     * Sets the padding inside the scroll content.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setPadding(float padding) {
        if (!Float.isFinite(padding) || padding < 0) {
            throw new IllegalArgumentException("Padding must be finite and non-negative.");
        }
        this.padding = padding;
        applyGridConfiguration();
        refreshContentSize();
    }

    /**
     * Returns the selection behavior.
     *
     * @return the selection mode, never null
     */
    public SelectionMode selectionMode() {
        return selectionMode;
    }

    /**
     * Sets the selection behavior.
     * Switching to {@link SelectionMode#SINGLE} keeps at most the most recently selected item.
     *
     * @param selectionMode selection mode
     * @throws NullPointerException if the mode is null
     */
    public void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
        if (selectionMode == SelectionMode.SINGLE && selected.size() > 1) {
            Integer last = null;
            for (Integer index : selected) {
                last = index;
            }
            LinkedHashSet<Integer> next = new LinkedHashSet<>();
            if (last != null) {
                next.add(last);
            }
            boolean changed = updateSelection(next);
            anchorIndex = last == null ? -1 : last;
            if (!next.contains(activeIndex)) {
                activeIndex = anchorIndex;
            }
            if (changed) {
                notifySelectionChanged();
            }
        }
    }

    /**
     * Returns the items in display order.
     *
     * @return an immutable snapshot of the items
     */
    public List<Item> items() {
        return List.copyOf(items);
    }

    /**
     * Appends an item.
     *
     * @param item item to append
     * @return the appended item
     * @throws NullPointerException if the item is null
     */
    public Item addItem(Item item) {
        Objects.requireNonNull(item, "item");
        items.add(item);
        GalleryCell cell = new GalleryCell(items.size() - 1, item);
        cells.add(cell);
        grid.add(cell);
        refreshContentSize();
        return item;
    }

    /**
     * Appends an item with the supplied thumbnail and caption.
     *
     * @param source thumbnail image source
     * @param caption caption drawn below the thumbnail
     * @return the appended item
     * @throws NullPointerException if the source or caption is null
     */
    public Item addItem(ImageSource source, String caption) {
        return addItem(new Item(source, caption));
    }

    /**
     * Removes every item and clears the selection.
     */
    public void clearItems() {
        grid.removeAll();
        cells.clear();
        items.clear();
        activeIndex = -1;
        anchorIndex = -1;
        boolean changed = updateSelection(new LinkedHashSet<>());
        refreshContentSize();
        if (changed) {
            notifySelectionChanged();
        }
    }

    /**
     * Returns the selected item indices in selection order.
     *
     * @return an immutable snapshot of the selected indices
     */
    public List<Integer> selectedIndices() {
        return List.copyOf(selected);
    }

    /**
     * Reports whether an item is selected.
     *
     * @param index zero-based item index
     * @return true when the item is selected
     */
    public boolean isSelected(int index) {
        return selected.contains(index);
    }

    /**
     * Replaces the selection with the supplied item indices.
     * Out-of-range indices are ignored. In {@link SelectionMode#SINGLE} only the first valid index
     * is kept.
     *
     * @param indices item indices to select
     * @throws NullPointerException if the collection or one of its entries is null
     */
    public void setSelectedIndices(Collection<Integer> indices) {
        Objects.requireNonNull(indices, "indices");
        LinkedHashSet<Integer> next = new LinkedHashSet<>();
        for (Integer index : indices) {
            Objects.requireNonNull(index, "index");
            if (index < 0 || index >= items.size()) {
                continue;
            }
            next.add(index);
            if (selectionMode == SelectionMode.SINGLE) {
                break;
            }
        }
        boolean changed = updateSelection(next);
        anchorIndex = next.isEmpty() ? -1 : next.iterator().next();
        if (!next.contains(activeIndex)) {
            activeIndex = anchorIndex;
        }
        if (changed) {
            notifySelectionChanged();
        }
    }

    /**
     * Clears the selection and the anchor used by Shift selection.
     */
    public void clearSelection() {
        boolean changed = updateSelection(new LinkedHashSet<>());
        anchorIndex = -1;
        if (changed) {
            notifySelectionChanged();
        }
    }

    /**
     * Returns the selected items in selection order.
     *
     * @return an immutable snapshot of the selected items
     */
    public List<Item> selectedItems() {
        List<Item> result = new ArrayList<>(selected.size());
        for (Integer index : selected) {
            result.add(items.get(index));
        }
        return List.copyOf(result);
    }

    /**
     * Returns the callback invoked after the selected items change.
     *
     * @return the selection listener, or null when none is assigned
     */
    public Consumer<List<Integer>> selectionListener() {
        return selectionListener;
    }

    /**
     * Assigns a callback invoked after the selected items change.
     *
     * @param selectionListener listener receiving an immutable index snapshot, or null to clear it
     */
    public void setSelectionListener(Consumer<List<Integer>> selectionListener) {
        this.selectionListener = selectionListener;
    }

    /**
     * Returns the callback invoked when Enter activates the current selection.
     *
     * @return the activation listener, or null when none is assigned
     */
    public Consumer<List<Item>> activationListener() {
        return activationListener;
    }

    /**
     * Assigns a callback invoked when Enter activates the current selection.
     *
     * @param activationListener listener receiving the selected items, or null to clear it
     */
    public void setActivationListener(Consumer<List<Item>> activationListener) {
        this.activationListener = activationListener;
    }

    /**
     * Returns the loader used for remote item sources.
     *
     * @return the assigned loader, or null when remote sources are disabled
     */
    public ImageLoader imageLoader() {
        return imageLoader;
    }

    /**
     * Assigns the loader used for remote item sources.
     * The gallery does not close the loader; its owner controls the loader lifetime.
     *
     * @param imageLoader image loader, or null to stop resolving remote sources
     */
    public void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
        for (GalleryCell cell : cells) {
            cell.image.setImageLoader(imageLoader);
        }
    }

    /**
     * Returns the vertical scroll offset.
     *
     * @return the offset in logical UI units
     */
    public float scrollY() {
        return scrollPanel.scrollY();
    }

    /**
     * Sets the vertical scroll offset, clamped to the available content range.
     *
     * @param scrollY vertical offset in logical UI units
     * @throws IllegalArgumentException if the offset is not finite
     */
    public void setScrollY(float scrollY) {
        scrollPanel.setScroll(scrollPanel.scrollX(), scrollY);
    }

    /**
     * Returns the largest valid vertical scroll offset.
     *
     * @return the maximum offset in logical UI units
     */
    public float maxScrollY() {
        return Math.max(0, grid.bounds().height() - scrollPanel.bounds().height());
    }

    /**
     * Returns the total height of the grid content.
     *
     * @return the content height in logical UI units
     */
    public float contentHeight() {
        return grid.bounds().height();
    }

    /**
     * Scrolls the gallery so the supplied item is visible.
     *
     * @param index zero-based item index
     * @throws IllegalArgumentException if the index is out of range
     */
    public void scrollToItem(int index) {
        requireIndex(index);
        int row = index / columns;
        float rowTop = padding + row * (cellHeight() + gap);
        float top = row == 0 ? 0 : rowTop;
        float bottom = rowTop + cellHeight();
        float viewport = scrollPanel.bounds().height();
        if (top < scrollY()) {
            setScrollY(top);
        } else if (bottom > scrollY() + viewport) {
            setScrollY(bottom - viewport);
        }
    }

    /**
     * Selects a single item and clears the previous selection.
     *
     * @param index zero-based item index
     * @throws IllegalArgumentException if the index is out of range
     */
    public void select(int index) {
        requireIndex(index);
        LinkedHashSet<Integer> next = new LinkedHashSet<>();
        next.add(index);
        activeIndex = index;
        anchorIndex = index;
        boolean changed = updateSelection(next);
        scrollToItem(index);
        if (changed) {
            notifySelectionChanged();
        }
    }

    /**
     * Lays the scroll panel and grid content out inside the gallery bounds.
     */
    @Override
    protected void onLayout() {
        float width = bounds().width();
        grid.setSize(contentWidth(), contentHeightForRows());
        scrollPanel.layout(new Rect(0, 0, width, bounds().height()));
    }

    /**
     * Activates the selected items with Enter.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ENTER && !repeat && !selected.isEmpty() && activationListener != null) {
            activationListener.accept(selectedItems());
            return true;
        }
        return false;
    }

    private void selectFromClick(int index, Modifiers modifiers) {
        activeIndex = index;
        boolean changed = false;
        switch (selectionMode) {
            case SINGLE -> {
                LinkedHashSet<Integer> next = new LinkedHashSet<>();
                next.add(index);
                anchorIndex = index;
                changed = updateSelection(next);
            }
            case MULTIPLE -> {
                if (modifiers.control()) {
                    LinkedHashSet<Integer> next = new LinkedHashSet<>(selected);
                    if (!next.remove(index)) {
                        next.add(index);
                    }
                    anchorIndex = index;
                    changed = updateSelection(next);
                } else if (modifiers.shift()) {
                    changed = extendSelectionTo(index);
                } else {
                    LinkedHashSet<Integer> next = new LinkedHashSet<>();
                    next.add(index);
                    anchorIndex = index;
                    changed = updateSelection(next);
                }
            }
            default -> {
            }
        }
        if (changed) {
            notifySelectionChanged();
        }
    }

    private boolean extendSelectionTo(int index) {
        if (anchorIndex < 0 || anchorIndex >= items.size()) {
            anchorIndex = index;
        }
        int from = Math.min(anchorIndex, index);
        int to = Math.max(anchorIndex, index);
        LinkedHashSet<Integer> next = new LinkedHashSet<>();
        for (int current = from; current <= to; current++) {
            next.add(current);
        }
        activeIndex = index;
        return updateSelection(next);
    }

    private boolean updateSelection(LinkedHashSet<Integer> next) {
        if (selected.equals(next)) {
            return false;
        }
        selected.clear();
        selected.addAll(next);
        return true;
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.accept(List.copyOf(selected));
        }
    }

    private void applyGridConfiguration() {
        GridTrack[] tracks = new GridTrack[columns];
        for (int index = 0; index < columns; index++) {
            tracks[index] = GridTrack.proportional(1);
        }
        grid.setColumns(tracks);
        grid.setGap(gap);
        grid.setPadding(padding);
    }

    private void refreshContentSize() {
        if (bounds().width() <= 0) {
            return;
        }
        grid.setSize(contentWidth(), contentHeightForRows());
        scrollPanel.setScroll(scrollPanel.scrollX(), scrollPanel.scrollY());
    }

    private float contentWidth() {
        return Math.max(0, bounds().width() - SCROLLBAR_RESERVE);
    }

    private float contentHeightForRows() {
        int rows = rowCount();
        if (rows == 0) {
            return 0;
        }
        return padding * 2 + rows * cellHeight() + (rows - 1) * gap;
    }

    private int rowCount() {
        return items.isEmpty() ? 0 : (items.size() + columns - 1) / columns;
    }

    private float cellHeight() {
        return CELL_INSET * 2 + thumbnailSize + CAPTION_HEIGHT;
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= items.size()) {
            throw new IllegalArgumentException("Item index is out of range.");
        }
    }

    private final class GalleryCell extends Widget {
        private final int index;
        private final Image image;
        private final Label caption;

        private GalleryCell(int index, Item item) {
            this.index = index;
            this.image = new Image();
            this.image.setSource(item.source());
            this.image.setFit(ImageFit.COVER);
            this.image.setCornerRadius(IMAGE_RADIUS);
            this.image.setImageLoader(imageLoader);
            this.caption = new Label(item.caption());
            this.caption.setAutoSize(false);
            this.caption.setWrap(false);
            this.caption.setMaxLines(1);
            this.caption.setOverflow(TextOverflow.ELLIPSIS);
            this.caption.setAlignment(HorizontalAlignment.CENTER);
            this.caption.setVerticalAlignment(VerticalAlignment.CENTER);
            add(image);
            add(caption);
        }

        /**
         * Measures the fixed thumbnail and caption slot.
         *
         * @param constraints available and required dimensions
         * @return the preferred cell size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            return new Size(thumbnailSize, cellHeight());
        }

        /**
         * Places the thumbnail above the caption.
         */
        @Override
        protected void onLayout() {
            float width = Math.max(0, bounds().width() - CELL_INSET * 2);
            image.layout(new Rect(CELL_INSET, CELL_INSET, width, thumbnailSize));
            caption.layout(new Rect(CELL_INSET, CELL_INSET + thumbnailSize,
                    width, CAPTION_HEIGHT));
        }

        /**
         * Paints hover and selection feedback behind the thumbnail.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Theme theme = context().theme();
            boolean selectedCell = isSelected(index);
            boolean hoveredCell = isHovered();
            if (!selectedCell && !hoveredCell) {
                return;
            }
            Color fill = selectedCell
                    ? Color.mix(theme.surface(), theme.primary(), 0.3f)
                    : Color.mix(theme.surface(), theme.primary(), 0.16f);
            renderer.roundedRect(localBounds(), CELL_RADIUS, fill);
            if (selectedCell || (ImageGallery.this.isFocused() && activeIndex == index)) {
                renderer.strokeRoundedRect(new Rect(0.5f, 0.5f,
                                Math.max(0, bounds().width() - 1), Math.max(0, bounds().height() - 1)),
                        CELL_RADIUS, 1, Paint.solid(theme.primary()));
            }
        }

        /**
         * Keeps the whole cell interactive even when a thumbnail or caption fills it.
         *
         * @param x horizontal coordinate relative to the cell
         * @param y vertical coordinate relative to the cell
         * @return this cell when the point is inside, otherwise null
         */
        @Override
        public Widget hitTest(float x, float y) {
            return contains(x, y) ? this : null;
        }

        /**
         * Selects the item on a left press.
         *
         * @param x horizontal pointer position relative to the cell
         * @param y vertical pointer position relative to the cell
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press was consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            selectFromClick(index, modifiers);
            return true;
        }

        /**
         * Requests the pointer cursor over the cell.
         *
         * @return the pointer cursor
         */
        @Override
        public Cursor cursor() {
            return Cursor.POINTER;
        }
    }
}
