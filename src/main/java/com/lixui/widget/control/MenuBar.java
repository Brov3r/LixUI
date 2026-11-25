package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.RenderManager;
import com.lixui.text.Font;
import com.lixui.theme.Theme;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.overlay.ContextMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A permanent row of menu titles that opens dropdown menus on click or hover.
 * Titles are flat text labels with a subtle hover highlight and an accented open state, so the bar
 * reads as a menu strip rather than a row of buttons.
 * Each dropdown reuses {@link ContextMenu}, so it supports shortcuts, disabled rows and nested
 * submenus. Item icons and check marks are disabled for bar dropdowns. Moving the pointer to
 * another title switches the open menu; an outside press or Escape closes it and returns
 * keyboard focus to the title when appropriate.
 */
public class MenuBar extends Panel {
    private final HBox row = new HBox();
    private final List<Menu> menus = new ArrayList<>();

    private int openIndex = -1;

    /**
     * Creates an empty menu bar.
     */
    public MenuBar() {
        row.setGap(2);
        add(row);
    }

    /**
     * Appends a menu title with an empty dropdown.
     *
     * @param label title shown in the bar
     * @return the added menu
     * @throws NullPointerException if {@code label} is null
     */
    public final Menu addMenu(String label) {
        Menu menu = new Menu(label);
        menus.add(menu);
        row.add(menu.title);
        return menu;
    }

    /**
     * Returns the menus in bar order.
     *
     * @return an immutable snapshot of the menus
     */
    public final List<Menu> menus() {
        return List.copyOf(menus);
    }

    /**
     * Returns the number of menus.
     *
     * @return the menu count
     */
    public final int menuCount() {
        return menus.size();
    }

    /**
     * Removes every menu and closes an open dropdown.
     */
    public final void clearMenus() {
        closeMenus();
        for (Menu menu : menus) {
            row.remove(menu.title);
        }
        menus.clear();
    }

    /**
     * Measures the title row.
     *
     * @param constraints available and required dimensions
     * @return the natural bar size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return row.measure(constraints);
    }

    /**
     * Stretches the title row over the whole bar.
     */
    @Override
    protected void onLayout() {
        row.layout(localBounds());
    }

    /**
     * Opens the hovered menu when no dropdown is currently open.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so pointer movement can bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        Point origin = absolutePosition();
        int index = menuIndexAt(origin.x() + x, origin.y() + y);
        if (index >= 0 && index != openIndex) {
            openMenu(index);
        }
        return false;
    }

    /**
     * Closes open dropdowns when the bar leaves the tree.
     */
    @Override
    protected void onDetached() {
        closeMenus();
        super.onDetached();
    }

    private void openMenu(int index) {
        if (index < 0 || index >= menus.size() || context() == null) {
            return;
        }
        Menu next = menus.get(index);
        if (!next.isEnabled()) {
            return;
        }
        if (openIndex >= 0 && openIndex != index) {
            menus.get(openIndex).menu.close();
        }
        if (next.menu.isOpen()) {
            openIndex = index;
            syncOpenTitles();
            return;
        }
        next.menu.setFocusReturn(next.title);
        Point origin = next.title.absolutePosition();
        Rect bounds = next.title.bounds();
        next.menu.open(context(), origin.x(), origin.y() + bounds.height());
        openIndex = next.menu.isOpen() ? index : -1;
        syncOpenTitles();
    }

    private void toggleMenu(Menu menu) {
        int index = menus.indexOf(menu);
        if (index < 0 || !menu.isEnabled()) {
            return;
        }
        if (openIndex == index && menu.menu.isOpen()) {
            focus(menu.title);
            menu.menu.close();
            return;
        }
        openMenu(index);
    }

    private void pressMenu(int index) {
        if (index < 0 || index >= menus.size()) {
            return;
        }
        Menu menu = menus.get(index);
        if (!menu.isEnabled()) {
            return;
        }
        if (openIndex == index && menu.menu.isOpen()) {
            focus(menu.title);
            menu.menu.close();
            return;
        }
        openMenu(index);
    }

    private void focus(MenuTitle title) {
        if (context() == null || title.context() != context()) {
            return;
        }
        try {
            context().requestFocus(title);
        } catch (IllegalArgumentException ignored) {
            // The bar may have left the tree during the same event.
        }
    }

    private void closeMenus() {
        for (Menu menu : menus) {
            if (menu.menu.isOpen()) {
                menu.menu.close();
            }
        }
        openIndex = -1;
        syncOpenTitles();
    }

    private void menuClosed(ContextMenu closed) {
        for (int index = 0; index < menus.size(); index++) {
            if (menus.get(index).menu == closed) {
                if (openIndex == index) {
                    openIndex = -1;
                }
                menus.get(index).title.setOpen(false);
                return;
            }
        }
    }

    private void syncOpenTitles() {
        for (int index = 0; index < menus.size(); index++) {
            Menu menu = menus.get(index);
            menu.title.setOpen(index == openIndex && menu.menu.isOpen());
        }
    }

    private int menuIndexAt(float viewportX, float viewportY) {
        for (int index = 0; index < menus.size(); index++) {
            MenuTitle title = menus.get(index).title;
            Point origin = title.absolutePosition();
            Rect bounds = title.bounds();
            if (new Rect(origin.x(), origin.y(), bounds.width(), bounds.height())
                    .contains(viewportX, viewportY)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * A menu title and its dropdown items.
     */
    public final class Menu {
        private final BarMenu menu;
        private final MenuTitle title;

        private Menu(String label) {
            menu = new BarMenu();
            menu.setCloseListener(() -> menuClosed(menu));
            title = new MenuTitle(Objects.requireNonNull(label, "label"));
            title.setOnAction(() -> toggleMenu(this));
        }

        /**
         * Reports whether the title accepts pointer and keyboard input.
         *
         * @return true when the title can be activated
         */
        public boolean isEnabled() {
            return title.isEnabled();
        }

        /**
         * Enables or disables the title and its dropdown.
         * A disabled title is drawn muted, ignores hover and clicks, and closes its dropdown when
         * it becomes disabled.
         *
         * @param enabled true to accept input
         */
        public void setEnabled(boolean enabled) {
            if (title.isEnabled() && !enabled && menu.isOpen()) {
                menu.close();
            }
            title.setEnabled(enabled);
        }

        /**
         * Appends an item with an action to the dropdown.
         *
         * @param label item label
         * @param action action run on activation, or null for a submenu placeholder
         * @return the added item
         * @throws NullPointerException if {@code label} is null
         */
        public ContextMenu.Item addItem(String label, Runnable action) {
            return menu.addItem(label, action);
        }

        /**
         * Appends an existing item to the dropdown.
         *
         * @param item item to append
         * @return the added item
         * @throws NullPointerException if {@code item} is null
         */
        public ContextMenu.Item addItem(ContextMenu.Item item) {
            return menu.addItem(item);
        }

        /**
         * Appends a separator to the dropdown.
         *
         * @return the added separator item
         */
        public ContextMenu.Item addSeparator() {
            return menu.addSeparator();
        }

        /**
         * Returns the dropdown items.
         *
         * @return an immutable snapshot of the root items
         */
        public List<ContextMenu.Item> items() {
            return menu.items();
        }

        ContextMenu contextMenu() {
            return menu;
        }
    }

    /**
     * A flat menu title that paints text plus a menu-style hover or open highlight.
     */
    private static final class MenuTitle extends Button {
        private static final float HORIZONTAL_PADDING = 10;
        private static final float VERTICAL_PADDING = 8;
        private static final float HIGHLIGHT_RADIUS = 3;
        private static final float HOVER_ALPHA = 0.12f;
        private static final float PRESSED_ALPHA = 0.18f;
        private static final float OPEN_ALPHA = 0.16f;

        private boolean open;

        private MenuTitle(String label) {
            super(label);
            setFocusOutlineEnabled(false);
        }

        private void setOpen(boolean open) {
            this.open = open;
        }

        /**
         * Uses a transparent, borderless style so the title never looks like a button.
         *
         * @return the flat title style, or null when the title is not attached
         */
        @Override
        protected WidgetStyle defaultStyle() {
            UiContext context = context();
            if (context == null) {
                return null;
            }
            Theme theme = context.theme();
            return new WidgetStyle(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                    Color.TRANSPARENT, theme.text(), theme.textMuted(), Color.TRANSPARENT,
                    Color.TRANSPARENT, 0, 0, 0);
        }

        /**
         * Measures the label with compact horizontal menu padding.
         *
         * @param constraints available and required dimensions
         * @return the natural title size
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            Font font = font();
            float contentWidth = 0;
            float contentHeight = 0;
            if (font != null && !text().isEmpty()) {
                Size textSize = font.measure(text());
                contentWidth = textSize.width();
                contentHeight = textSize.height();
            }
            return new Size(contentWidth + HORIZONTAL_PADDING * 2,
                    contentHeight + VERTICAL_PADDING * 2);
        }

        /**
         * Paints the flat highlight and the label without the button surface.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            if (localBounds().isEmpty()) {
                return;
            }
            drawHighlight(renderer);
            drawForeground(renderer, frame);
        }

        private void drawHighlight(RenderManager renderer) {
            UiContext context = context();
            if (!isEnabled() || context == null) {
                return;
            }
            Theme theme = context.theme();
            WidgetState state = state();
            if (open) {
                renderer.roundedRect(localBounds(), HIGHLIGHT_RADIUS,
                        theme.primary().withAlpha(OPEN_ALPHA));
            } else if (state == WidgetState.PRESSED) {
                renderer.roundedRect(localBounds(), HIGHLIGHT_RADIUS,
                        theme.primary().withAlpha(PRESSED_ALPHA));
            } else if (state == WidgetState.HOVERED) {
                renderer.roundedRect(localBounds(), HIGHLIGHT_RADIUS,
                        theme.primary().withAlpha(HOVER_ALPHA));
            }
        }
    }

    private final class BarMenu extends ContextMenu {
        private BarMenu() {
            setIconsVisible(false);
        }

        /**
         * Switches to the menu title under the pointer.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @return true when the context menu consumed the movement
         */
        @Override
        protected boolean onPointerMove(float x, float y) {
            boolean consumed = super.onPointerMove(x, y);
            int index = menuIndexAt(x, y);
            if (index >= 0 && index != openIndex) {
                openMenu(index);
            }
            return consumed;
        }

        /**
         * Opens or toggles a menu title pressed while the dropdown is open.
         *
         * @param x horizontal pointer position relative to the widget
         * @param y vertical pointer position relative to the widget
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the press was consumed
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            int index = menuIndexAt(x, y);
            if (index >= 0 && button == MouseButton.LEFT) {
                pressMenu(index);
                return true;
            }
            return super.onPointerPress(x, y, button, modifiers);
        }
    }
}
