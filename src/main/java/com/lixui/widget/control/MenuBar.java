package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.overlay.ContextMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A permanent row of menu titles that opens dropdown menus on click or hover.
 * Each dropdown reuses {@link ContextMenu}, so it supports icons, shortcuts, check marks, disabled
 * rows and nested submenus. Moving the pointer to another title switches the open menu; an outside
 * press or Escape closes it and returns keyboard focus to the title when appropriate.
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
        row.add(menu.button);
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
            row.remove(menu.button);
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
        if (openIndex >= 0 && openIndex != index) {
            menus.get(openIndex).menu.close();
        }
        if (next.menu.isOpen()) {
            openIndex = index;
            return;
        }
        next.menu.setFocusReturn(next.button);
        Point origin = next.button.absolutePosition();
        Rect bounds = next.button.bounds();
        next.menu.open(context(), origin.x(), origin.y() + bounds.height());
        openIndex = next.menu.isOpen() ? index : -1;
    }

    private void toggleMenu(Menu menu) {
        int index = menus.indexOf(menu);
        if (index < 0) {
            return;
        }
        if (openIndex == index && menu.menu.isOpen()) {
            focus(menu.button);
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
        if (openIndex == index && menu.menu.isOpen()) {
            focus(menu.button);
            menu.menu.close();
            return;
        }
        openMenu(index);
    }

    private void focus(Button button) {
        if (context() == null || button.context() != context()) {
            return;
        }
        try {
            context().requestFocus(button);
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
    }

    private void menuClosed(ContextMenu closed) {
        for (int index = 0; index < menus.size(); index++) {
            if (menus.get(index).menu == closed) {
                if (openIndex == index) {
                    openIndex = -1;
                }
                return;
            }
        }
    }

    private int menuIndexAt(float viewportX, float viewportY) {
        for (int index = 0; index < menus.size(); index++) {
            Button button = menus.get(index).button;
            Point origin = button.absolutePosition();
            Rect bounds = button.bounds();
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
        private final Button button;

        private Menu(String label) {
            menu = new BarMenu();
            menu.setCloseListener(() -> menuClosed(menu));
            button = new Button(Objects.requireNonNull(label, "label"));
            button.setOnAction(() -> toggleMenu(this));
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

        /**
         * Returns the icon edge length used by dropdown items.
         *
         * @return the icon size in logical UI units
         */
        public float iconSize() {
            return menu.iconSize();
        }

        ContextMenu contextMenu() {
            return menu;
        }
    }

    private final class BarMenu extends ContextMenu {
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
