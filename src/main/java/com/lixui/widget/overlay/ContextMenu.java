package com.lixui.widget.overlay;

import com.lixui.geometry.Rect;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.text.Font;
import com.lixui.text.HorizontalAlignment;
import com.lixui.text.TextOverflow;
import com.lixui.text.TextStyle;
import com.lixui.text.VerticalAlignment;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.ContextMenuHandle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A viewport-level context menu with icons, shortcuts, disabled rows and nested submenus.
 * Submenus open on hover with a configurable gap to their parent level. The whole menu is one
 * overlay, so moving the pointer across the gap does not reset the hovered path. Levels are clamped
 * to the viewport and scroll independently when they are taller than the available space.
 */
public class ContextMenu extends Widget implements ContextMenuHandle {
    /**
     * Semantic text colors for menu items.
     */
    public enum Tone {
        /** Normal surface text. */
        DEFAULT,
        /** Secondary muted text. */
        MUTED,
        /** Accent text used for highlighted commands. */
        ACCENT,
        /** Destructive action color. */
        DANGER
    }

    private static final float DEFAULT_ITEM_HEIGHT = 28;
    private static final float DEFAULT_SEPARATOR_HEIGHT = 9;
    private static final float DEFAULT_PADDING = 4;
    private static final float DEFAULT_MIN_WIDTH = 160;
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float DEFAULT_ARROW_SIZE = 12;
    private static final float DEFAULT_SUBMENU_GAP = 5;
    private static final float DEFAULT_VIEWPORT_MARGIN = 4;
    private static final float DEFAULT_CORNER_RADIUS = 6;
    private static final float ICON_GAP = 8;
    private static final float SHORTCUT_GAP = 18;
    static final float LEFT_INSET = 4;
    static final float RIGHT_PADDING = 10;
    private static final float SCROLLBAR_THICKNESS = 4;
    private static final float SCROLLBAR_GAP = 4;

    private final List<Item> items = new ArrayList<>();
    private final List<Level> levels = new ArrayList<>();
    private final IconSet icons = new IconSet("arrow-right-04.svg", "tick.svg");

    private float itemHeight = DEFAULT_ITEM_HEIGHT;
    private float separatorHeight = DEFAULT_SEPARATOR_HEIGHT;
    private float padding = DEFAULT_PADDING;
    private float minimumMenuWidth = DEFAULT_MIN_WIDTH;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float arrowSize = DEFAULT_ARROW_SIZE;
    private float submenuGap = DEFAULT_SUBMENU_GAP;
    private float viewportMargin = DEFAULT_VIEWPORT_MARGIN;
    private float cornerRadius = DEFAULT_CORNER_RADIUS;
    private Color dangerColor = Color.hex("#E06C75");
    private boolean closeOnOutsideClick = true;
    private Runnable closeListener;
    private Widget focusReturn;

    private boolean open;
    private float openX;
    private float openY;
    private float pointerX;
    private float pointerY;
    private int hoveredLevel = -1;
    private int hoveredItem = -1;

    /**
     * Creates an empty context menu.
     */
    public ContextMenu() {
        setFillViewport(true);
        setAlwaysOnTop(true);
        setClipToBounds(true);
        setFocusable(true);
        setFocusOutlineEnabled(false);
    }

    /**
     * Returns the root items.
     *
     * @return an immutable snapshot of the root items
     */
    public final List<Item> items() {
        return List.copyOf(items);
    }

    /**
     * Appends an item to the root level.
     *
     * @param item item to append
     * @return the added item
     * @throws NullPointerException if {@code item} is null
     */
    public final Item addItem(Item item) {
        items.add(Objects.requireNonNull(item, "item"));
        return item;
    }

    /**
     * Appends a text item to the root level.
     *
     * @param label item label
     * @param action action run when the item is activated, or null for a submenu placeholder
     * @return the added item
     * @throws NullPointerException if {@code label} is null
     */
    public final Item addItem(String label, Runnable action) {
        return addItem(new Item(label, action));
    }

    /**
     * Appends a separator to the root level.
     *
     * @return the added separator item
     */
    public final Item addSeparator() {
        return addItem(Item.separator());
    }

    /**
     * Removes every root item.
     */
    public final void clearItems() {
        items.clear();
        levels.clear();
    }

    /**
     * Returns the item height.
     *
     * @return the row height in logical UI units
     */
    public final float itemHeight() {
        return itemHeight;
    }

    /**
     * Sets the item height.
     *
     * @param itemHeight positive row height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public final void setItemHeight(float itemHeight) {
        if (!Float.isFinite(itemHeight) || itemHeight <= 0) {
            throw new IllegalArgumentException("Item height must be finite and positive.");
        }
        this.itemHeight = itemHeight;
    }

    /**
     * Returns the separator height.
     *
     * @return the separator height in logical UI units
     */
    public final float separatorHeight() {
        return separatorHeight;
    }

    /**
     * Sets the separator height.
     *
     * @param separatorHeight positive separator height in logical UI units
     * @throws IllegalArgumentException if the height is non-finite or not positive
     */
    public final void setSeparatorHeight(float separatorHeight) {
        if (!Float.isFinite(separatorHeight) || separatorHeight <= 0) {
            throw new IllegalArgumentException("Separator height must be finite and positive.");
        }
        this.separatorHeight = separatorHeight;
    }

    /**
     * Returns the menu padding.
     *
     * @return the padding in logical UI units
     */
    public final float padding() {
        return padding;
    }

    /**
     * Sets the menu padding.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is non-finite or negative
     */
    public final void setPadding(float padding) {
        if (!Float.isFinite(padding) || padding < 0) {
            throw new IllegalArgumentException("Padding must be finite and non-negative.");
        }
        this.padding = padding;
    }

    /**
     * Returns the minimum menu width.
     *
     * @return the minimum width in logical UI units
     */
    public final float minimumMenuWidth() {
        return minimumMenuWidth;
    }

    /**
     * Sets the minimum menu width.
     *
     * @param minimumMenuWidth non-negative minimum width in logical UI units
     * @throws IllegalArgumentException if the width is non-finite or negative
     */
    public final void setMinimumMenuWidth(float minimumMenuWidth) {
        if (!Float.isFinite(minimumMenuWidth) || minimumMenuWidth < 0) {
            throw new IllegalArgumentException("Minimum menu width must be finite and non-negative.");
        }
        this.minimumMenuWidth = minimumMenuWidth;
    }

    /**
     * Returns the icon edge length.
     *
     * @return the icon size in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the icon edge length.
     *
     * @param iconSize positive icon size in logical UI units
     * @throws IllegalArgumentException if the size is non-finite or not positive
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the distance between a parent level and its submenu.
     *
     * @return the submenu gap in logical UI units
     */
    public final float submenuGap() {
        return submenuGap;
    }

    /**
     * Sets the distance between a parent level and its submenu.
     * The gap is used on both the horizontal and vertical placement calculations.
     *
     * @param submenuGap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is non-finite or negative
     */
    public final void setSubmenuGap(float submenuGap) {
        if (!Float.isFinite(submenuGap) || submenuGap < 0) {
            throw new IllegalArgumentException("Submenu gap must be finite and non-negative.");
        }
        this.submenuGap = submenuGap;
    }

    /**
     * Returns the margin kept between menus and the viewport edges.
     *
     * @return the viewport margin in logical UI units
     */
    public final float viewportMargin() {
        return viewportMargin;
    }

    /**
     * Sets the margin kept between menus and the viewport edges.
     *
     * @param viewportMargin non-negative margin in logical UI units
     * @throws IllegalArgumentException if the margin is non-finite or negative
     */
    public final void setViewportMargin(float viewportMargin) {
        if (!Float.isFinite(viewportMargin) || viewportMargin < 0) {
            throw new IllegalArgumentException("Viewport margin must be finite and non-negative.");
        }
        this.viewportMargin = viewportMargin;
    }

    /**
     * Returns the menu surface corner radius.
     *
     * @return the corner radius in logical UI units
     */
    public final float cornerRadius() {
        return cornerRadius;
    }

    /**
     * Sets the menu surface corner radius.
     *
     * @param cornerRadius non-negative corner radius in logical UI units
     * @throws IllegalArgumentException if the radius is non-finite or negative
     */
    public final void setCornerRadius(float cornerRadius) {
        if (!Float.isFinite(cornerRadius) || cornerRadius < 0) {
            throw new IllegalArgumentException("Corner radius must be finite and non-negative.");
        }
        this.cornerRadius = cornerRadius;
    }

    /**
     * Returns the color used by {@link Tone#DANGER} items.
     *
     * @return the danger color, never null
     */
    public final Color dangerColor() {
        return dangerColor;
    }

    /**
     * Sets the color used by {@link Tone#DANGER} items.
     *
     * @param dangerColor danger color
     * @throws NullPointerException if {@code dangerColor} is null
     */
    public final void setDangerColor(Color dangerColor) {
        this.dangerColor = Objects.requireNonNull(dangerColor, "dangerColor");
    }

    /**
     * Reports whether an outside press closes the menu.
     *
     * @return true when outside presses dismiss the menu
     */
    public final boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    /**
     * Enables or disables closing the menu when the user presses outside its levels.
     *
     * @param closeOnOutsideClick true to dismiss on an outside press
     */
    public final void setCloseOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
    }

    /**
     * Returns the callback invoked after the menu closes.
     *
     * @return the close listener, or null when none is assigned
     */
    public final Runnable closeListener() {
        return closeListener;
    }

    /**
     * Assigns a callback invoked after the menu closes.
     *
     * @param closeListener listener to run, or null to clear it
     */
    public final void setCloseListener(Runnable closeListener) {
        this.closeListener = closeListener;
    }

    /**
     * Returns the widget that receives keyboard focus when the menu is dismissed with Escape or an
     * item activation.
     *
     * @return the focus target, or null when focus is left to the manager
     */
    public final Widget focusReturn() {
        return focusReturn;
    }

    /**
     * Sets the widget that receives keyboard focus when the menu is dismissed with Escape or an
     * item activation. Outside presses keep their own focus target.
     *
     * @param focusReturn attached widget to focus, or null to leave focus unchanged
     */
    public final void setFocusReturn(Widget focusReturn) {
        this.focusReturn = focusReturn;
    }

    /**
     * Reports whether the context menu is open.
     *
     * @return true while the menu is visible
     */
    public final boolean isOpen() {
        return open;
    }

    int levelCount() {
        return levels.size();
    }

    Rect levelBounds(int index) {
        return index >= 0 && index < levels.size() ? levels.get(index).bounds : Rect.EMPTY;
    }

    /**
     * Opens the menu at a viewport position.
     * The root level is clamped to the viewport; submenus are opened on hover.
     *
     * @param context context that owns the widget manager
     * @param x horizontal viewport position
     * @param y vertical viewport position
     * @throws NullPointerException if {@code context} is null
     * @throws IllegalArgumentException if a coordinate is non-finite
     * @throws IllegalStateException if the context has no manager
     */
    public final void open(UiContext context, float x, float y) {
        Objects.requireNonNull(context, "context");
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Context menu position must be finite.");
        }
        if (items.isEmpty()) {
            return;
        }
        if (open) {
            close();
        }
        openX = x;
        openY = y;
        pointerX = x;
        pointerY = y;
        hoveredLevel = -1;
        hoveredItem = -1;
        levels.clear();
        open = true;
        context.addTopLevel(this);
        context.requestFocus(this);
    }

    /**
     * Closes the menu and removes it from the widget manager.
     */
    public final void close() {
        if (!open) {
            return;
        }
        open = false;
        levels.clear();
        hoveredLevel = -1;
        hoveredItem = -1;
        UiContext owner = context();
        if (owner != null) {
            try {
                owner.removeTopLevel(this);
            } catch (IllegalStateException ignored) {
                // The manager may already be closing and detach every top-level widget.
            }
        }
        if (closeListener != null) {
            closeListener.run();
        }
    }

    /**
     * Lays out the root level and every open submenu.
     */
    @Override
    protected void onLayout() {
        if (!open || localBounds().isEmpty()) {
            return;
        }
        if (levels.isEmpty()) {
            levels.add(new Level(items, -1, -1));
        }
        for (int index = 0; index < levels.size(); index++) {
            Level level = levels.get(index);
            if (index == 0) {
                layoutRoot(level);
            } else {
                layoutSubmenu(level, levels.get(level.parentLevel), level.parentItem);
            }
        }
    }

    /**
     * Paints every open menu level.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (!open || levels.isEmpty()) {
            return;
        }
        Theme theme = context().theme();
        WidgetStyle style = context().theme().panelStyle();
        ensureIcons();
        Font font = font();
        for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
            Level level = levels.get(levelIndex);
            if (level.bounds.isEmpty()) {
                continue;
            }
            drawSurface(renderer, level, style);
            final int currentLevel = levelIndex;
            renderer.withClip(level.bounds, () -> {
                drawItems(renderer, theme, font, level, currentLevel);
                drawScrollbar(renderer, theme, level);
            });
        }
    }

    private void drawSurface(RenderManager renderer, Level level, WidgetStyle style) {
        renderer.roundedRect(level.bounds, cornerRadius, style.background(WidgetState.NORMAL));
        float borderWidth = style.borderWidth();
        if (borderWidth <= 0) {
            return;
        }
        float inset = borderWidth / 2;
        Rect border = new Rect(level.bounds.x() + inset, level.bounds.y() + inset,
                Math.max(0, level.bounds.width() - borderWidth),
                Math.max(0, level.bounds.height() - borderWidth));
        if (!border.isEmpty()) {
            renderer.strokeRoundedRect(border, Math.max(0, cornerRadius - inset),
                    borderWidth, Paint.solid(style.border(WidgetState.NORMAL)));
        }
    }

    private void drawItems(RenderManager renderer, Theme theme, Font font,
                           Level level, int levelIndex) {
        float contentX = level.bounds.x() + padding;
        float scrollbarSpace = level.maximumScroll > 0
                ? SCROLLBAR_THICKNESS + SCROLLBAR_GAP : 0;
        float contentRight = level.bounds.right() - padding - scrollbarSpace;
        float y = level.bounds.y() + padding - level.scrollY;
        boolean checkSlot = hasChecked(level);
        for (int index = 0; index < level.items.size(); index++) {
            Item item = level.items.get(index);
            float height = rowHeight(item);
            if (y + height > level.bounds.y() + padding
                    && y < level.bounds.bottom() - padding) {
                drawItem(renderer, theme, font, level, levelIndex, item, index,
                        contentX, contentRight, y, height, checkSlot);
            }
            y += height;
        }
    }

    private void drawItem(RenderManager renderer, Theme theme, Font font,
                          Level level, int levelIndex, Item item, int itemIndex,
                          float contentX, float contentRight, float y, float height,
                          boolean checkSlot) {
        if (item.isSeparator()) {
            renderer.rect(new Rect(contentX, y + height / 2,
                    Math.max(0, contentRight - contentX), 1), theme.border().withAlpha(0.7f));
            return;
        }
        boolean highlighted = levelIndex == hoveredLevel && itemIndex == hoveredItem
                && item.isEnabled();
        if (highlighted) {
            renderer.roundedRect(new Rect(contentX, y + 1,
                    Math.max(0, contentRight - contentX), height - 2),
                    3, theme.primary().withAlpha(0.16f));
        }
        Color color = itemColor(theme, item);
        if (!item.isEnabled()) {
            color = theme.textMuted().withAlpha(0.55f);
        }
        float x = contentX + LEFT_INSET;
        if (checkSlot) {
            if (item.isChecked()) {
                renderer.icon(icons.get(1), new Rect(x, y + (height - iconSize) / 2,
                        iconSize, iconSize), ImageStyle.ICON, color);
            }
            x += iconSize + ICON_GAP;
        }
        if (item.icon() != null) {
            renderer.icon(item.icon(), new Rect(x, y + (height - iconSize) / 2,
                    iconSize, iconSize), ImageStyle.ICON, color);
            x += iconSize + ICON_GAP;
        }
        float right = contentRight - RIGHT_PADDING;
        float arrowSlot = item.hasChildren() ? arrowSize + ICON_GAP : 0;
        float shortcutWidth = font == null || item.shortcut().isEmpty()
                ? 0 : font.measure(item.shortcut()).width();
        float shortcutSlot = shortcutWidth <= 0 ? 0 : shortcutWidth + SHORTCUT_GAP;
        float textWidth = Math.max(0, right - x - arrowSlot - shortcutSlot);
        if (font != null) {
            renderer.text(font, item.label(), x, y + height / 2, color,
                    new TextStyle(textWidth, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
            if (shortcutWidth > 0) {
                renderer.text(font, item.shortcut(),
                        right - arrowSlot - shortcutWidth,
                        y + height / 2, theme.textMuted(),
                        new TextStyle(shortcutWidth, 0, 1, TextOverflow.ELLIPSIS,
                                HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
            }
        }
        if (item.hasChildren()) {
            Texture arrow = icons.get(0);
            if (arrow != null) {
                renderer.icon(arrow, new Rect(right - arrowSize,
                        y + (height - arrowSize) / 2, arrowSize, arrowSize),
                        ImageStyle.ICON, color);
            }
        }
    }

    private void drawScrollbar(RenderManager renderer, Theme theme, Level level) {
        if (level.maximumScroll <= 0) {
            return;
        }
        float trackX = level.bounds.right() - padding - SCROLLBAR_THICKNESS;
        float trackY = level.bounds.y() + padding;
        float trackHeight = Math.max(0, level.bounds.height() - padding * 2);
        if (trackHeight <= 0) {
            return;
        }
        float visible = Math.min(1, level.bounds.height() / level.naturalHeight);
        float thumbHeight = Math.max(18, trackHeight * visible);
        float travel = Math.max(0, trackHeight - thumbHeight);
        float ratio = level.maximumScroll > 0 ? level.scrollY / level.maximumScroll : 0;
        renderer.roundedRect(new Rect(trackX, trackY, SCROLLBAR_THICKNESS, trackHeight),
                SCROLLBAR_THICKNESS / 2, theme.border().withAlpha(0.35f));
        renderer.roundedRect(new Rect(trackX, trackY + travel * ratio,
                SCROLLBAR_THICKNESS, thumbHeight), SCROLLBAR_THICKNESS / 2,
                theme.textMuted().withAlpha(0.65f));
    }

    private float rowHeight(Item item) {
        return item.isSeparator() ? separatorHeight : itemHeight;
    }

    private boolean hasChecked(Level level) {
        for (Item item : level.items) {
            if (!item.isSeparator() && item.isChecked()) {
                return true;
            }
        }
        return false;
    }

    private void layoutRoot(Level level) {
        float width = measureWidth(level);
        float height = measureHeight(level);
        placeLevel(level, openX, openY, width, height);
    }

    private void layoutSubmenu(Level level, Level parent, int parentItem) {
        float width = measureWidth(level);
        float height = measureHeight(level);
        float itemTop = parent.bounds.y() + padding + offsetOf(parent, parentItem)
                - parent.scrollY;
        float rightX = parent.bounds.right() + submenuGap;
        float leftX = parent.bounds.x() - width - submenuGap;
        float viewportWidth = localBounds().width();
        float viewportHeight = localBounds().height();
        float y = Math.clamp(itemTop, viewportMargin,
                Math.max(viewportMargin, viewportHeight - height - viewportMargin));
        float x = rightX;
        if (rightX + width > viewportWidth - viewportMargin
                || intersectsAncestor(level, rightX, y, width, height)) {
            x = leftX;
        }
        placeLevel(level, x, itemTop, width, height);
    }

    private boolean intersectsAncestor(Level self, float x, float y, float width, float height) {
        Rect candidate = new Rect(x, y, width, height);
        for (Level level : levels) {
            if (level == self) {
                break;
            }
            if (!level.bounds.intersect(candidate).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void placeLevel(Level level, float preferredX, float preferredY,
                            float naturalWidth, float naturalHeight) {
        float viewportWidth = localBounds().width();
        float viewportHeight = localBounds().height();
        float width = Math.min(naturalWidth,
                Math.max(0, viewportWidth - viewportMargin * 2));
        float height = Math.min(naturalHeight,
                Math.max(0, viewportHeight - viewportMargin * 2));
        float x = Math.clamp(preferredX, viewportMargin,
                Math.max(viewportMargin, viewportWidth - width - viewportMargin));
        float y = Math.clamp(preferredY, viewportMargin,
                Math.max(viewportMargin, viewportHeight - height - viewportMargin));
        level.bounds = new Rect(x, y, width, height);
        level.naturalHeight = naturalHeight;
        level.maximumScroll = Math.max(0, naturalHeight - height);
        level.scrollY = Math.clamp(level.scrollY, 0, level.maximumScroll);
    }

    private float measureWidth(Level level) {
        Font font = font();
        float content = 0;
        boolean checkSlot = hasChecked(level);
        for (Item item : level.items) {
            if (item.isSeparator()) {
                continue;
            }
            float width = itemWidth(item, font, checkSlot);
            content = Math.max(content, width);
        }
        return Math.max(minimumMenuWidth, content + padding * 2);
    }

    private float itemWidth(Item item, Font font, boolean checkSlot) {
        float width = LEFT_INSET;
        if (checkSlot) {
            width += iconSize + ICON_GAP;
        }
        if (item.icon() != null) {
            width += iconSize + ICON_GAP;
        }
        if (font != null && !item.label().isEmpty()) {
            width += font.measure(item.label()).width();
        }
        if (!item.shortcut().isEmpty() && font != null) {
            width += SHORTCUT_GAP + font.measure(item.shortcut()).width();
        }
        if (item.hasChildren()) {
            width += ICON_GAP + arrowSize;
        }
        return width + RIGHT_PADDING;
    }

    private float measureHeight(Level level) {
        float height = padding * 2;
        for (Item item : level.items) {
            height += rowHeight(item);
        }
        return height;
    }

    private float offsetOf(Level level, int itemIndex) {
        float offset = 0;
        for (int index = 0; index < itemIndex && index < level.items.size(); index++) {
            offset += rowHeight(level.items.get(index));
        }
        return offset;
    }

    private int itemAt(Level level, float x, float y) {
        if (!level.bounds.contains(x, y)) {
            return -1;
        }
        float contentY = y - level.bounds.y() - padding + level.scrollY;
        float cursor = 0;
        for (int index = 0; index < level.items.size(); index++) {
            float height = rowHeight(level.items.get(index));
            if (contentY >= cursor && contentY < cursor + height) {
                return index;
            }
            cursor += height;
        }
        return -1;
    }

    private int levelAt(float x, float y) {
        for (int index = levels.size() - 1; index >= 0; index--) {
            if (levels.get(index).bounds.contains(x, y)) {
                return index;
            }
        }
        return -1;
    }

    private void openSubmenu(int levelIndex, int itemIndex) {
        Level parent = levels.get(levelIndex);
        if (itemIndex < 0 || itemIndex >= parent.items.size()) {
            return;
        }
        Item item = parent.items.get(itemIndex);
        if (!item.isEnabled() || !item.hasChildren()) {
            return;
        }
        while (levels.size() > levelIndex + 1) {
            levels.removeLast();
        }
        Level submenu = new Level(item.children(), levelIndex, itemIndex);
        levels.add(submenu);
        layoutSubmenu(submenu, parent, itemIndex);
    }

    private void updateHover() {
        int levelIndex = levelAt(pointerX, pointerY);
        if (levelIndex < 0) {
            hoveredLevel = -1;
            hoveredItem = -1;
            return;
        }
        Level level = levels.get(levelIndex);
        int itemIndex = itemAt(level, pointerX, pointerY);
        hoveredLevel = levelIndex;
        hoveredItem = itemIndex;
        boolean keepsSubmenu = itemIndex >= 0
                && level.items.get(itemIndex).isEnabled()
                && level.items.get(itemIndex).hasChildren()
                && levels.size() > levelIndex + 1
                && levels.get(levelIndex + 1).parentItem == itemIndex;
        if (!keepsSubmenu) {
            while (levels.size() > levelIndex + 1) {
                levels.removeLast();
            }
        }
        if (itemIndex >= 0 && !keepsSubmenu) {
            Item item = level.items.get(itemIndex);
            if (item.isEnabled() && item.hasChildren()) {
                openSubmenu(levelIndex, itemIndex);
            }
        }
    }

    private Color itemColor(Theme theme, Item item) {
        if (item.textColor() != null) {
            return item.textColor();
        }
        return switch (item.tone()) {
            case DEFAULT -> theme.text();
            case MUTED -> theme.textMuted();
            case ACCENT -> theme.primary();
            case DANGER -> dangerColor;
        };
    }

    private void ensureIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(arrowSize * context.density()));
        if (!icons.matches(context, size)) {
            icons.reload(context, size);
        }
    }

    private void restoreFocus() {
        Widget target = focusReturn;
        if (target == null || context() == null || target.context() != context()
                || !target.isVisible() || !target.isEnabled() || !target.isFocusable()) {
            return;
        }
        try {
            context().requestFocus(target);
        } catch (IllegalArgumentException ignored) {
            // The return target may have left the tree while the menu was open.
        }
    }

    /**
     * Tracks the pointer and opens submenus on hover.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so pointer movement can bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        pointerX = x;
        pointerY = y;
        updateHover();
        return false;
    }

    /**
     * Returns a pointer cursor over enabled items.
     *
     * @return the pointer cursor over an enabled row, otherwise the default cursor
     */
    @Override
    public Cursor cursor() {
        if (hoveredLevel < 0 || hoveredLevel >= levels.size()) {
            return Cursor.DEFAULT;
        }
        Level level = levels.get(hoveredLevel);
        if (hoveredItem < 0 || hoveredItem >= level.items.size()) {
            return Cursor.DEFAULT;
        }
        Item item = level.items.get(hoveredItem);
        return item.isEnabled() && !item.isSeparator() ? Cursor.POINTER : Cursor.DEFAULT;
    }

    /**
     * Activates a menu item or closes the menu on an outside press.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press was consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        pointerX = x;
        pointerY = y;
        int levelIndex = levelAt(x, y);
        if (levelIndex < 0) {
            if (closeOnOutsideClick) {
                close();
            }
            return true;
        }
        if (button != MouseButton.LEFT) {
            return true;
        }
        Level level = levels.get(levelIndex);
        int itemIndex = itemAt(level, x, y);
        if (itemIndex < 0) {
            return true;
        }
        Item item = level.items.get(itemIndex);
        if (!item.isEnabled() || item.isSeparator()) {
            return true;
        }
        if (item.hasChildren()) {
            openSubmenu(levelIndex, itemIndex);
            return true;
        }
        Runnable action = item.action();
        restoreFocus();
        close();
        if (action != null) {
            action.run();
        }
        return true;
    }

    /**
     * Scrolls the menu level under the pointer.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        int levelIndex = levelAt(x, y);
        if (levelIndex < 0 || deltaY == 0) {
            return false;
        }
        Level level = levels.get(levelIndex);
        if (level.maximumScroll <= 0) {
            return false;
        }
        float next = Math.clamp(level.scrollY - deltaY * itemHeight, 0, level.maximumScroll);
        if (next == level.scrollY) {
            return false;
        }
        level.scrollY = next;
        return true;
    }

    /**
     * Closes the menu on Escape.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ESCAPE) {
            restoreFocus();
            close();
            return true;
        }
        return false;
    }

    /**
     * Releases cached icons when the menu leaves the tree.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        if (open) {
            open = false;
            levels.clear();
            if (closeListener != null) {
                closeListener.run();
            }
        }
        icons.release(context());
    }

    /**
     * Drops cached icons so they are reloaded at the new density.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        icons.release(context());
    }

    private static final class Level {
        private final List<Item> items;
        private final int parentLevel;
        private final int parentItem;
        private Rect bounds = Rect.EMPTY;
        private float naturalHeight;
        private float scrollY;
        private float maximumScroll;

        private Level(List<Item> items, int parentLevel, int parentItem) {
            this.items = items;
            this.parentLevel = parentLevel;
            this.parentItem = parentItem;
        }
    }

    /**
     * A context menu row, separator or submenu container.
     */
    public static final class Item {
        private String label;
        private Texture icon;
        private String shortcut = "";
        private Runnable action;
        private boolean enabled = true;
        private boolean checked;
        private boolean separator;
        private Tone tone = Tone.DEFAULT;
        private Color textColor;
        private final List<Item> children = new ArrayList<>();

        /**
         * Creates an item without an action.
         *
         * @param label item label
         * @throws NullPointerException if {@code label} is null
         */
        public Item(String label) {
            this(label, null);
        }

        /**
         * Creates an item with an action.
         *
         * @param label item label
         * @param action action run on activation, or null when the item does nothing
         * @throws NullPointerException if {@code label} is null
         */
        public Item(String label, Runnable action) {
            this.label = Objects.requireNonNull(label, "label");
            this.action = action;
        }

        /**
         * Creates a separator row.
         *
         * @return a separator item
         */
        public static Item separator() {
            Item item = new Item("");
            item.separator = true;
            item.enabled = false;
            return item;
        }

        /**
         * Returns the item label.
         *
         * @return the label, never null
         */
        public String label() {
            return label;
        }

        /**
         * Sets the item label.
         *
         * @param label item label
         * @throws NullPointerException if {@code label} is null
         */
        public void setLabel(String label) {
            this.label = Objects.requireNonNull(label, "label");
        }

        /**
         * Returns the item icon.
         *
         * @return the caller-owned icon, or null when the item has no icon
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Sets the item icon.
         *
         * @param icon caller-owned icon, or null to clear it
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Returns the shortcut hint.
         *
         * @return the shortcut text, never null
         */
        public String shortcut() {
            return shortcut;
        }

        /**
         * Sets the shortcut hint shown at the right edge of the row.
         *
         * @param shortcut shortcut text, or null to clear it
         */
        public void setShortcut(String shortcut) {
            this.shortcut = shortcut == null ? "" : shortcut;
        }

        /**
         * Returns the item action.
         *
         * @return the action, or null when the item does nothing
         */
        public Runnable action() {
            return action;
        }

        /**
         * Sets the item action.
         *
         * @param action action run on activation, or null to clear it
         */
        public void setAction(Runnable action) {
            this.action = action;
        }

        /**
         * Reports whether the item is enabled.
         *
         * @return true when the item can be activated
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the item.
         *
         * @param enabled true to allow activation
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Reports whether the item is marked as checked.
         *
         * @return true when a check mark is drawn beside the item
         */
        public boolean isChecked() {
            return checked;
        }

        /**
         * Marks the item as checked or unchecked.
         *
         * @param checked true to draw a check mark beside the item
         */
        public void setChecked(boolean checked) {
            this.checked = checked;
        }

        /**
         * Reports whether the item is a separator.
         *
         * @return true for a separator row
         */
        public boolean isSeparator() {
            return separator;
        }

        /**
         * Returns the semantic text tone.
         *
         * @return the tone, never null
         */
        public Tone tone() {
            return tone;
        }

        /**
         * Sets the semantic text tone.
         *
         * @param tone item tone
         * @throws NullPointerException if {@code tone} is null
         */
        public void setTone(Tone tone) {
            this.tone = Objects.requireNonNull(tone, "tone");
        }

        /**
         * Returns the explicit text color override.
         *
         * @return the override color, or null when the tone is used
         */
        public Color textColor() {
            return textColor;
        }

        /**
         * Overrides the resolved text color.
         *
         * @param textColor color override, or null to use the tone
         */
        public void setTextColor(Color textColor) {
            this.textColor = textColor;
        }

        /**
         * Returns the submenu items.
         *
         * @return an immutable snapshot of the submenu items
         */
        public List<Item> children() {
            return List.copyOf(children);
        }

        /**
         * Appends a submenu item.
         *
         * @param child submenu item
         * @return the added item
         * @throws NullPointerException if {@code child} is null
         */
        public Item addChild(Item child) {
            children.add(Objects.requireNonNull(child, "child"));
            return child;
        }

        /**
         * Reports whether the item opens a submenu.
         *
         * @return true when the item has children
         */
        public boolean hasChildren() {
            return !children.isEmpty();
        }
    }
}
