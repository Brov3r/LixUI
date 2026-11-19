package com.lixui.widget;

import com.lixui.animation.Animation;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
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
import com.lixui.widget.tooltip.TextTooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A panel with a row of tabs above a content area.
 * The strip scrolls smoothly with arrow buttons when tabs do not fit, tabs can be reordered by
 * dragging and closed with a cross where allowed, and locked tabs cannot be selected. Each tab can
 * show an icon next to its title, and per-tab flags override the panel defaults.
 */
public class TabPanel extends Panel {
    private static final float DEFAULT_TAB_HEIGHT = 30;
    private static final float DEFAULT_MIN_TAB_WIDTH = 44;
    private static final float DEFAULT_MAX_TAB_WIDTH = 210;
    private static final float DEFAULT_TAB_PADDING = 14;
    private static final float DEFAULT_ICON_SIZE = 14;
    private static final float ICON_GAP = 4;
    private static final float CLOSE_SIZE = 18;
    private static final float CLOSE_INSET = 2;
    private static final float CLOSE_MARK = 3;
    private static final float ARROW_WIDTH = 20;
    private static final float ARROW_RADIUS = 9;
    private static final float ARROW_ICON = 12;
    private static final float TAB_RADIUS = 4;
    private static final float TAB_INSET = 2;
    private static final float TITLE_SLACK = 2;
    private static final float HOVER_FILL_ALPHA = 0.1f;
    private static final float ACTIVE_FILL_ALPHA = 0.22f;
    private static final float ACTIVE_OUTLINE_ALPHA = 0.45f;
    private static final float SCROLL_SECONDS = 0.15f;
    private static final float WHEEL_STEP = 48;
    private static final float DRAG_EDGE = 18;
    private static final float DRAG_SCROLL_SPEED = 320;
    private static final float TAB_ANIMATION_RATE = 14;
    private static final Rect FULL_UV = new Rect(0, 0, 1, 1);

    private final List<Tab> tabs = new ArrayList<>();
    private final TabStrip strip = new TabStrip();
    private Tab selected;
    private boolean tabsMovable = true;
    private boolean tabsClosable;
    private float tabHeight = DEFAULT_TAB_HEIGHT;
    private float tabMinWidth = DEFAULT_MIN_TAB_WIDTH;
    private float tabMaxWidth = DEFAULT_MAX_TAB_WIDTH;
    private float tabPadding = DEFAULT_TAB_PADDING;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float contentPadding;
    private Consumer<Tab> onChange;
    private Consumer<Tab> onClose;
    private Predicate<Tab> closeRequest;
    private Texture arrowLeft;
    private Texture arrowRight;
    private int arrowPixelSize;

    /**
     * Creates an empty tab panel.
     */
    public TabPanel() {
        add(strip);
        strip.setClipToBounds(true);
        strip.setTooltip(strip.titleTooltip);
    }

    /**
     * Appends a tab without an icon and selects it when it is the first tab.
     *
     * @param title tab title, possibly empty for an icon-only tab
     * @param content page shown while the tab is selected, or null for an empty page
     * @return the created tab
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the content already belongs to a widget tree
     */
    public Tab addTab(String title, Widget content) {
        return addTab(title, null, content);
    }

    /**
     * Appends a tab with an icon and selects it when it is the first tab.
     *
     * @param title tab title, possibly empty for an icon-only tab
     * @param icon tab icon, or null for a text-only tab
     * @param content page shown while the tab is selected, or null for an empty page
     * @return the created tab
     * @throws NullPointerException if {@code title} is null
     * @throws IllegalArgumentException if the content already belongs to a widget tree
     */
    public Tab addTab(String title, Texture icon, Widget content) {
        Objects.requireNonNull(title, "title");
        Tab tab = new Tab(title, icon, content);
        if (content != null) {
            add(content);
        }
        tabs.add(tab);
        if (selected == null) {
            selected = tab;
            tab.activeAmount = 1;
        }
        updateContentVisibility();
        strip.relayout();
        return tab;
    }

    /**
     * Removes a tab without invoking the close callback.
     * When the selected tab is removed, the nearest remaining tab becomes selected.
     *
     * @param tab tab to remove
     * @return true when the tab belonged to this panel
     * @throws NullPointerException if {@code tab} is null
     */
    public boolean removeTab(Tab tab) {
        Objects.requireNonNull(tab, "tab");
        int index = indexOf(tab);
        if (index < 0) {
            return false;
        }
        tabs.remove(index);
        if (tab.content() != null) {
            remove(tab.content());
        }
        if (selected == tab) {
            selected = firstSelectableFrom(Math.min(index, tabs.size() - 1));
            updateContentVisibility();
            if (onChange != null) {
                onChange.accept(selected);
            }
        }
        strip.relayout();
        return true;
    }

    /**
     * Closes a tab, honoring the close request filter and invoking the close callback.
     *
     * @param tab tab to close
     * @return true when the tab was closed
     * @throws NullPointerException if {@code tab} is null
     */
    public boolean closeTab(Tab tab) {
        Objects.requireNonNull(tab, "tab");
        if (indexOf(tab) < 0 || (closeRequest != null && !closeRequest.test(tab))) {
            return false;
        }
        removeTab(tab);
        if (onClose != null) {
            onClose.accept(tab);
        }
        return true;
    }

    /**
     * Moves a tab to another position.
     *
     * @param from current tab index
     * @param to target tab index
     * @throws IllegalArgumentException if an index is out of range
     */
    public void moveTab(int from, int to) {
        moveTab(from, to, true);
    }

    /**
     * Moves a tab to another position.
     *
     * @param tab tab to move
     * @param to target tab index
     * @throws IllegalArgumentException if the tab is not in this panel or the index is out of range
     * @throws NullPointerException if {@code tab} is null
     */
    public void moveTab(Tab tab, int to) {
        Objects.requireNonNull(tab, "tab");
        moveTab(requireIndex(tab), to, true);
    }

    /**
     * Returns the tabs in display order.
     *
     * @return an immutable snapshot of the tabs
     */
    public List<Tab> tabs() {
        return List.copyOf(tabs);
    }

    /**
     * Returns the selected tab.
     *
     * @return the selected tab, or null when the panel has no tabs
     */
    public Tab selected() {
        return selected;
    }

    /**
     * Selects a tab unless it is locked.
     *
     * @param tab tab to select
     * @return true when the selection changed
     * @throws NullPointerException if {@code tab} is null
     * @throws IllegalArgumentException if the tab is not in this panel
     */
    public boolean select(Tab tab) {
        Objects.requireNonNull(tab, "tab");
        requireIndex(tab);
        if (tab.isLocked() || tab == selected) {
            return false;
        }
        selected = tab;
        updateContentVisibility();
        strip.ensureVisible(tab);
        if (onChange != null) {
            onChange.accept(tab);
        }
        return true;
    }

    /**
     * Selects a tab by index unless it is locked.
     *
     * @param index tab index
     * @return true when the selection changed
     * @throws IllegalArgumentException if the index is out of range
     */
    public boolean select(int index) {
        if (index < 0 || index >= tabs.size()) {
            throw new IllegalArgumentException("Tab index is out of range.");
        }
        return select(tabs.get(index));
    }

    /**
     * Returns the index of a tab.
     *
     * @param tab tab to look up
     * @return the index, or -1 when the tab is not in this panel
     * @throws NullPointerException if {@code tab} is null
     */
    public int indexOf(Tab tab) {
        Objects.requireNonNull(tab, "tab");
        for (int index = 0; index < tabs.size(); index++) {
            if (tabs.get(index) == tab) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Reports whether the panel shows a close cross on tabs without their own override.
     *
     * @return true when tabs are closable by default
     */
    public boolean areTabsClosable() {
        return tabsClosable;
    }

    /**
     * Sets the default closable state for tabs without an override.
     *
     * @param tabsClosable true to show close crosses by default
     */
    public void setTabsClosable(boolean tabsClosable) {
        this.tabsClosable = tabsClosable;
    }

    /**
     * Reports whether tabs are movable by default.
     *
     * @return true when tabs can be reordered by default
     */
    public boolean areTabsMovable() {
        return tabsMovable;
    }

    /**
     * Sets the default movable state for tabs without an override.
     *
     * @param tabsMovable true to allow reordering by default
     */
    public void setTabsMovable(boolean tabsMovable) {
        this.tabsMovable = tabsMovable;
    }

    /**
     * Reports whether a tab currently shows a close cross.
     *
     * @param tab tab to query
     * @return true when the tab is closable
     * @throws NullPointerException if {@code tab} is null
     */
    public boolean isTabClosable(Tab tab) {
        Objects.requireNonNull(tab, "tab");
        return tab.closable() != null ? tab.closable() : tabsClosable;
    }

    /**
     * Reports whether a tab can currently be reordered.
     * Locked tabs are never movable.
     *
     * @param tab tab to query
     * @return true when the tab can be moved
     * @throws NullPointerException if {@code tab} is null
     */
    public boolean isTabMovable(Tab tab) {
        Objects.requireNonNull(tab, "tab");
        return !tab.isLocked() && (tab.movable() != null ? tab.movable() : tabsMovable);
    }

    /**
     * Sets an explicit closable state for a tab.
     *
     * @param tab tab to configure
     * @param closable true to show a close cross
     * @throws IllegalArgumentException if the tab is not in this panel
     * @throws NullPointerException if {@code tab} is null
     */
    public void setTabClosable(Tab tab, boolean closable) {
        requireIndex(tab);
        tab.setClosable(closable);
    }

    /**
     * Restores the panel closable default for a tab.
     *
     * @param tab tab to configure
     * @throws IllegalArgumentException if the tab is not in this panel
     * @throws NullPointerException if {@code tab} is null
     */
    public void clearTabClosable(Tab tab) {
        requireIndex(tab);
        tab.setClosable(null);
    }

    /**
     * Sets an explicit movable state for a tab.
     *
     * @param tab tab to configure
     * @param movable true to allow reordering
     * @throws IllegalArgumentException if the tab is not in this panel
     * @throws NullPointerException if {@code tab} is null
     */
    public void setTabMovable(Tab tab, boolean movable) {
        requireIndex(tab);
        tab.setMovable(movable);
    }

    /**
     * Restores the panel movable default for a tab.
     *
     * @param tab tab to configure
     * @throws IllegalArgumentException if the tab is not in this panel
     * @throws NullPointerException if {@code tab} is null
     */
    public void clearTabMovable(Tab tab) {
        requireIndex(tab);
        tab.setMovable(null);
    }

    /**
     * Returns the tab strip height.
     *
     * @return the height in logical UI units
     */
    public float tabHeight() {
        return tabHeight;
    }

    /**
     * Sets the tab strip height.
     *
     * @param tabHeight positive height in logical UI units
     * @throws IllegalArgumentException if the height is not positive or non-finite
     */
    public void setTabHeight(float tabHeight) {
        if (!Float.isFinite(tabHeight) || tabHeight <= 0) {
            throw new IllegalArgumentException("Tab height must be finite and positive.");
        }
        this.tabHeight = tabHeight;
    }

    /**
     * Returns the minimum tab width.
     *
     * @return the minimum width in logical UI units
     */
    public float tabMinWidth() {
        return tabMinWidth;
    }

    /**
     * Sets the minimum tab width.
     *
     * @param tabMinWidth non-negative minimum width in logical UI units
     * @throws IllegalArgumentException if the width is negative, non-finite or above the maximum
     */
    public void setTabMinWidth(float tabMinWidth) {
        validateTabWidth(tabMinWidth);
        if (tabMinWidth > tabMaxWidth) {
            throw new IllegalArgumentException("Minimum tab width cannot exceed the maximum.");
        }
        this.tabMinWidth = tabMinWidth;
    }

    /**
     * Returns the maximum tab width.
     *
     * @return the maximum width in logical UI units
     */
    public float tabMaxWidth() {
        return tabMaxWidth;
    }

    /**
     * Sets the maximum tab width.
     *
     * @param tabMaxWidth positive maximum width in logical UI units
     * @throws IllegalArgumentException if the width is not positive, non-finite or below the minimum
     */
    public void setTabMaxWidth(float tabMaxWidth) {
        validateTabWidth(tabMaxWidth);
        if (tabMaxWidth < tabMinWidth) {
            throw new IllegalArgumentException("Maximum tab width cannot be below the minimum.");
        }
        this.tabMaxWidth = tabMaxWidth;
    }

    /**
     * Returns the horizontal tab padding on each side.
     *
     * @return the padding in logical UI units
     */
    public float tabPadding() {
        return tabPadding;
    }

    /**
     * Sets the horizontal tab padding on each side.
     *
     * @param tabPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setTabPadding(float tabPadding) {
        if (!Float.isFinite(tabPadding) || tabPadding < 0) {
            throw new IllegalArgumentException("Tab padding must be finite and non-negative.");
        }
        this.tabPadding = tabPadding;
    }

    /**
     * Returns the tab icon edge length.
     *
     * @return the icon size in logical UI units
     */
    public float iconSize() {
        return iconSize;
    }

    /**
     * Sets the tab icon edge length.
     *
     * @param iconSize positive icon size in logical UI units
     * @throws IllegalArgumentException if the size is not positive or non-finite
     */
    public void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the inner padding around the selected page.
     *
     * @return the padding in logical UI units
     */
    public float contentPadding() {
        return contentPadding;
    }

    /**
     * Sets the inner padding around the selected page.
     *
     * @param contentPadding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setContentPadding(float contentPadding) {
        if (!Float.isFinite(contentPadding) || contentPadding < 0) {
            throw new IllegalArgumentException("Content padding must be finite and non-negative.");
        }
        this.contentPadding = contentPadding;
    }

    /**
     * Returns the callback invoked when the selected tab changes.
     *
     * @return the change callback, or null when none is assigned
     */
    public Consumer<Tab> onChange() {
        return onChange;
    }

    /**
     * Assigns a callback invoked when the selected tab changes.
     * The callback receives null when the last tab is removed.
     *
     * @param onChange change callback, or null to clear it
     */
    public void setOnChange(Consumer<Tab> onChange) {
        this.onChange = onChange;
    }

    /**
     * Returns the callback invoked after a tab is closed.
     *
     * @return the close callback, or null when none is assigned
     */
    public Consumer<Tab> onClose() {
        return onClose;
    }

    /**
     * Assigns a callback invoked after a tab is closed through {@link #closeTab(Tab)}.
     *
     * @param onClose close callback, or null to clear it
     */
    public void setOnClose(Consumer<Tab> onClose) {
        this.onClose = onClose;
    }

    /**
     * Returns the close request filter.
     *
     * @return the filter, or null when every tab may close
     */
    public Predicate<Tab> closeRequest() {
        return closeRequest;
    }

    /**
     * Assigns a filter consulted before a tab closes.
     * Returning false keeps the tab open.
     *
     * @param closeRequest close request filter, or null to allow every close
     */
    public void setCloseRequest(Predicate<Tab> closeRequest) {
        this.closeRequest = closeRequest;
    }

    /**
     * Returns the current horizontal tab strip scroll offset.
     *
     * @return the offset in logical UI units
     */
    public float scrollOffset() {
        return strip.scrollX;
    }

    /**
     * Scrolls the tab strip smoothly to an offset.
     *
     * @param offset target offset in logical UI units, clamped to the scroll range
     */
    public void scrollTo(float offset) {
        strip.scrollTo(offset);
    }

    Rect tabBounds(Tab tab) {
        return strip.tabBounds(requireIndex(tab));
    }

    Rect tabCloseBounds(Tab tab) {
        return strip.tabCloseBounds(requireIndex(tab));
    }

    /**
     * Measures the selected page and the tab strip.
     *
     * @param constraints available and required dimensions
     * @return the natural panel size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        float stripWidth = 0;
        for (Tab tab : tabs) {
            stripWidth += tabWidth(tab);
        }
        float contentWidth = 0;
        float contentHeight = 0;
        if (selected != null && selected.content() != null) {
            Size size = selected.content().measure(LayoutConstraints.loose(
                    Math.max(0, constraints.maxWidth() - contentPadding * 2),
                    Math.max(0, constraints.maxHeight() - tabHeight - contentPadding * 2)));
            contentWidth = size.width();
            contentHeight = size.height();
        }
        return new Size(Math.max(stripWidth, contentWidth) + contentPadding * 2,
                tabHeight + contentHeight + contentPadding * 2);
    }

    /**
     * Releases the shared scroll arrow icons when the panel leaves the tree.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        releaseArrows();
    }

    /**
     * Drops cached arrow icons so they are reloaded at the new density.
     */
    @Override
    protected void onContextChanged() {
        releaseArrows();
    }

    /**
     * Lays out the strip and every page below it.
     */
    @Override
    protected void onLayout() {
        strip.setBounds(new Rect(0, 0, bounds().width(), tabHeight));
        strip.layout(strip.bounds());
        strip.relayout();
        Rect contentBounds = contentBounds();
        for (Tab tab : tabs) {
            if (tab.content() != null) {
                tab.content().layout(contentBounds);
            }
        }
    }

    /**
     * Clips pages to the content area while leaving the strip unclipped by the panel.
     *
     * @param child direct child being drawn or hit-tested
     * @return the content rectangle for pages, or null for the strip
     */
    @Override
    protected Rect childClip(Widget child) {
        return child == strip ? null : contentBounds();
    }

    private Rect contentBounds() {
        return new Rect(contentPadding, tabHeight + contentPadding,
                Math.max(0, bounds().width() - contentPadding * 2),
                Math.max(0, bounds().height() - tabHeight - contentPadding * 2));
    }

    private float tabWidth(Tab tab) {
        Font font = font();
        float textWidth = font == null ? 0 : font.measure(tab.title()).width();
        float iconWidth = tab.icon() != null ? iconSize : 0;
        float gap = iconWidth > 0 && !tab.title().isEmpty() ? ICON_GAP : 0;
        float closeWidth = isTabClosable(tab) ? CLOSE_SIZE : 0;
        float width = textWidth + iconWidth + gap + closeWidth + tabPadding * 2
                + TAB_INSET * 2 + TITLE_SLACK;
        return clamp((float) Math.ceil(width), tabMinWidth, tabMaxWidth);
    }

    private void moveTab(int from, int to, boolean reveal) {
        if (from < 0 || from >= tabs.size() || to < 0 || to >= tabs.size()) {
            throw new IllegalArgumentException("Tab index is out of range.");
        }
        if (from == to) {
            return;
        }
        Tab tab = tabs.remove(from);
        tabs.add(to, tab);
        strip.relayout();
        if (reveal && selected != null) {
            strip.ensureVisible(selected);
        }
    }

    private Tab firstSelectableFrom(int index) {
        if (tabs.isEmpty()) {
            return null;
        }
        int start = Math.max(0, index);
        for (int current = start; current < tabs.size(); current++) {
            if (!tabs.get(current).isLocked()) {
                return tabs.get(current);
            }
        }
        for (int current = Math.min(start, tabs.size()) - 1; current >= 0; current--) {
            if (!tabs.get(current).isLocked()) {
                return tabs.get(current);
            }
        }
        return null;
    }

    private int requireIndex(Tab tab) {
        int index = indexOf(tab);
        if (index < 0) {
            throw new IllegalArgumentException("Tab does not belong to this panel.");
        }
        return index;
    }

    private void updateContentVisibility() {
        for (Tab tab : tabs) {
            if (tab.content() != null) {
                tab.content().setVisible(tab == selected);
            }
        }
    }

    private static void validateTabWidth(float width) {
        if (!Float.isFinite(width) || width < 0) {
            throw new IllegalArgumentException("Tab width must be finite and non-negative.");
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float approach(float from, float to, float blend) {
        float next = from + (to - from) * blend;
        return Math.abs(to - next) < 0.001f ? to : next;
    }

    private static Color blend(Color from, Color to, float amount) {
        if (amount <= 0) {
            return from;
        }
        if (amount >= 1) {
            return to;
        }
        return new Color(
                from.r() + (to.r() - from.r()) * amount,
                from.g() + (to.g() - from.g()) * amount,
                from.b() + (to.b() - from.b()) * amount,
                from.a() + (to.a() - from.a()) * amount);
    }

    private void ensureArrows() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.min(128, (int) Math.ceil(14 * context.density())));
        if (arrowLeft != null && arrowPixelSize == size) {
            return;
        }
        Texture left = null;
        Texture right = null;
        try {
            left = context.acquireIcon("assets/icons-128/arrow-left-04.png", size);
            right = context.acquireIcon("assets/icons-128/arrow-right-04.png", size);
        } catch (RuntimeException | Error exception) {
            releaseIcon(left);
            releaseIcon(right);
            throw exception;
        }
        Texture previousLeft = arrowLeft;
        Texture previousRight = arrowRight;
        arrowLeft = left;
        arrowRight = right;
        arrowPixelSize = size;
        releaseIcon(previousLeft);
        releaseIcon(previousRight);
    }

    private void releaseArrows() {
        Texture left = arrowLeft;
        Texture right = arrowRight;
        arrowLeft = null;
        arrowRight = null;
        arrowPixelSize = 0;
        releaseIcon(left);
        releaseIcon(right);
    }

    private void releaseIcon(Texture texture) {
        UiContext context = context();
        if (texture != null && context != null) {
            context.releaseIcon(texture);
        }
    }

    /**
     * A tab entry with a title, optional icon and a page widget.
     */
    public static final class Tab {
        private String title;
        private Texture icon;
        private IconSide iconSide = IconSide.LEFT;
        private final Widget content;
        private boolean locked;
        private Boolean closable;
        private Boolean movable;
        private float hoverAmount;
        private float activeAmount;

        private Tab(String title, Texture icon, Widget content) {
            this.title = title;
            this.icon = icon;
            this.content = content;
        }

        /**
         * Returns the tab title.
         *
         * @return the title
         */
        public String title() {
            return title;
        }

        /**
         * Replaces the tab title.
         *
         * @param title tab title
         * @throws NullPointerException if {@code title} is null
         */
        public void setTitle(String title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /**
         * Returns the tab icon.
         *
         * @return the icon, or null when the tab has no icon
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Assigns a tab icon.
         *
         * @param icon icon texture, or null to remove it
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Returns the icon side relative to the title.
         *
         * @return the icon side
         */
        public IconSide iconSide() {
            return iconSide;
        }

        /**
         * Sets the icon side relative to the title.
         *
         * @param iconSide icon side
         * @throws NullPointerException if {@code iconSide} is null
         */
        public void setIconSide(IconSide iconSide) {
            this.iconSide = Objects.requireNonNull(iconSide, "iconSide");
        }

        /**
         * Returns the page shown while this tab is selected.
         *
         * @return the content widget, or null for an empty page
         */
        public Widget content() {
            return content;
        }

        /**
         * Reports whether this tab is locked.
         *
         * @return true when the tab cannot be selected or moved
         */
        public boolean isLocked() {
            return locked;
        }

        /**
         * Locks or unlocks this tab.
         * Locked tabs cannot be selected by the user or reordered, and are drawn muted.
         *
         * @param locked true to lock the tab
         */
        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        /**
         * Returns the explicit closable override.
         *
         * @return true or false when overridden, or null to use the panel default
         */
        public Boolean closable() {
            return closable;
        }

        /**
         * Sets an explicit closable override.
         *
         * @param closable true or false to override, or null to inherit the panel default
         */
        public void setClosable(Boolean closable) {
            this.closable = closable;
        }

        /**
         * Returns the explicit movable override.
         *
         * @return true or false when overridden, or null to use the panel default
         */
        public Boolean movable() {
            return movable;
        }

        /**
         * Sets an explicit movable override.
         *
         * @param movable true or false to override, or null to inherit the panel default
         */
        public void setMovable(Boolean movable) {
            this.movable = movable;
        }
    }

    /**
     * Placement of a tab icon relative to the title.
     */
    public enum IconSide {
        /** Icon before the title. */
        LEFT,
        /** Icon after the title. */
        RIGHT
    }

    private final class TabStrip extends Widget {
        private float[] tabWidths = new float[0];
        private float[] tabStarts = new float[0];
        private float totalWidth;
        private float viewportWidth;
        private float maxScroll;
        private float scrollX;
        private boolean overflow;
        private float pointerX;
        private float pointerY;
        private boolean pointerInside;
        private Tab hoveredTab;
        private Tab hoveredClose;
        private int hoveredArrow;
        private final TextTooltip titleTooltip = new TextTooltip();
        private Tab dragTab;
        private boolean dragging;
        private float dragPointerX;
        private float dragPointerY;
        private int dragDirection;
        private Animation scrollAnimation;
        private Animator animationOwner;

        /**
         * Returns the pointer cursor for the strip.
         *
         * @return the pointer cursor
         */
        @Override
        public Cursor cursor() {
            return Cursor.POINTER;
        }

        /**
         * Animates tab highlights, hover state and drag edge scrolling.
         *
         * @param frame timing information for the current frame
         */
        @Override
        protected void onUpdate(FrameInfo frame) {
            animateTabs(frame.deltaSeconds());
            if (dragging && dragDirection != 0) {
                scrollImmediately(dragDirection * DRAG_SCROLL_SPEED * frame.deltaSeconds());
                updateDragTarget();
            }
            if (pointerInside) {
                updateHover(pointerX, pointerY);
            }
        }

        /**
         * Cancels smooth scrolling and drops drag state when the strip leaves the tree.
         */
        @Override
        protected void onDetached() {
            cancelScrollAnimation();
            clearDrag();
        }

        /**
         * Paints the tabs, the separator and the scroll arrows.
         *
         * @param renderer drawing manager for the current frame
         * @param frame timing information for the current frame
         */
        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Theme theme = context().theme();
            float width = bounds().width();
            float height = bounds().height();
            renderer.rect(new Rect(0, height - 1, width, 1), theme.border());
            float areaStart = overflow ? ARROW_WIDTH : 0;
            float areaWidth = Math.max(0, width - areaStart * 2);
            Point origin = absolutePosition();
            renderer.withClip(new Rect(origin.x() + areaStart, origin.y(), areaWidth, height),
                    () -> drawTabs(renderer, theme, areaStart, areaWidth, height));
            if (overflow) {
                ensureArrows();
                drawArrow(renderer, theme, -1, ARROW_WIDTH / 2, height / 2, scrollX > 0.5f, arrowLeft);
                drawArrow(renderer, theme, 1, width - ARROW_WIDTH / 2, height / 2,
                        scrollX < maxScroll - 0.5f, arrowRight);
            }
        }

        private void drawTabs(RenderManager renderer, Theme theme, float areaStart,
                              float areaWidth, float height) {
            Font font = font();
            Color backdrop = tabBackdrop(theme);
            float cellHeight = Math.max(0, height - TAB_INSET * 2);
            for (int index = 0; index < tabs.size(); index++) {
                Tab tab = tabs.get(index);
                float cellX = areaStart + tabStarts[index] - scrollX + TAB_INSET;
                float cellWidth = Math.max(0, tabWidths[index] - TAB_INSET * 2);
                if (cellX + cellWidth <= areaStart || cellX >= areaStart + areaWidth) {
                    continue;
                }
                Rect cell = new Rect(cellX, TAB_INSET, cellWidth, cellHeight);
                float hoverAmount = tab.hoverAmount;
                float activeAmount = tab.activeAmount;
                float fillAmount = HOVER_FILL_ALPHA * hoverAmount * (1 - activeAmount)
                        + ACTIVE_FILL_ALPHA * activeAmount;
                Color fill = blend(backdrop, theme.primary(), fillAmount);
                if (fillAmount > 0.005f) {
                    if (activeAmount > 0.01f) {
                        drawPill(renderer, cell, fill, blend(backdrop, theme.primary(),
                                ACTIVE_OUTLINE_ALPHA * activeAmount));
                    } else {
                        renderer.roundedRect(cell, TAB_RADIUS, fill);
                    }
                }
                if (dragging && tab == dragTab) {
                    drawPill(renderer, cell, blend(fill, theme.primary(), 0.18f),
                            theme.primary());
                }
                Color text = tab.isLocked() ? theme.textMuted().withAlpha(0.45f)
                        : blend(theme.textMuted(), theme.text(), activeAmount);
                drawTabContent(renderer, theme, tab, font, cellX, cellWidth, cellHeight, text,
                        isTabClosable(tab));
            }
        }

        private void drawPill(RenderManager renderer, Rect cell, Color fill, Color border) {
            renderer.roundedRect(cell, TAB_RADIUS, border);
            Rect inner = new Rect(cell.x() + 1, cell.y() + 1,
                    Math.max(0, cell.width() - 2), Math.max(0, cell.height() - 2));
            renderer.roundedRect(inner, Math.max(0, TAB_RADIUS - 1), fill);
        }

        private Color tabBackdrop(Theme theme) {
            WidgetStyle style = style();
            Color base = style == null ? theme.surface() : style.background(WidgetState.NORMAL);
            if (base.a() >= 1) {
                return base;
            }
            Color surface = theme.surface();
            float alpha = base.a();
            return new Color(surface.r() + (base.r() - surface.r()) * alpha,
                    surface.g() + (base.g() - surface.g()) * alpha,
                    surface.b() + (base.b() - surface.b()) * alpha, 1);
        }

        private void drawTabContent(RenderManager renderer, Theme theme, Tab tab, Font font,
                                    float cellX, float cellWidth, float cellHeight,
                                    Color textColor, boolean closable) {
            float iconWidth = tab.icon() != null ? iconSize : 0;
            boolean hasText = !tab.title().isEmpty();
            float gap = iconWidth > 0 && hasText ? ICON_GAP : 0;
            float closeWidth = closeReservation(cellWidth, closable);
            float available = Math.max(0, cellWidth - closeWidth);
            float maxText = textLimit(tab, cellWidth, closable);
            float textWidth = font == null || !hasText || maxText <= 0
                    ? 0 : Math.min(maxText, font.measure(tab.title()).width());
            float groupWidth = iconWidth + gap + textWidth;
            float startX = cellX + (available - groupWidth) / 2;
            float iconX = tab.iconSide() == IconSide.LEFT ? startX : startX + textWidth + gap;
            float textX = tab.iconSide() == IconSide.LEFT ? startX + iconWidth + gap : startX;
            if (iconWidth > 0) {
                float iconY = TAB_INSET + (cellHeight - iconSize) / 2;
                renderer.image(tab.icon(), new Rect(iconX, iconY, iconSize, iconSize),
                        0, FULL_UV, textColor);
            }
            if (textWidth > 0) {
                renderer.text(font, tab.title(), textX, cellHeight / 2 + TAB_INSET, textColor,
                        new TextStyle(maxText, 0, 1, TextOverflow.ELLIPSIS,
                                HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
            }
            if (closable) {
                Rect close = closeBounds(cellX, cellWidth, cellHeight);
                if (close != null) {
                    Color closeColor = tab == hoveredClose ? theme.primary() : textColor;
                    float cx = close.x() + close.width() / 2;
                    float cy = close.y() + close.height() / 2;
                    renderer.strokeLine(new Point(cx - CLOSE_MARK, cy - CLOSE_MARK),
                            new Point(cx + CLOSE_MARK, cy + CLOSE_MARK),
                            1, Paint.solid(closeColor));
                    renderer.strokeLine(new Point(cx - CLOSE_MARK, cy + CLOSE_MARK),
                            new Point(cx + CLOSE_MARK, cy - CLOSE_MARK),
                            1, Paint.solid(closeColor));
                }
            }
        }

        private void drawArrow(RenderManager renderer, Theme theme, int direction,
                               float centerX, float centerY, boolean enabled, Texture icon) {
            Color background = hoveredArrow == direction && enabled
                    ? theme.primary().withAlpha(0.3f)
                    : theme.surface().withAlpha(0.62f);
            renderer.roundedRect(new Rect(centerX - ARROW_RADIUS, centerY - ARROW_RADIUS,
                    ARROW_RADIUS * 2, ARROW_RADIUS * 2), ARROW_RADIUS, background);
            Color color = enabled ? theme.text() : theme.textMuted().withAlpha(0.55f);
            if (icon != null) {
                renderer.image(icon, new Rect(centerX - ARROW_ICON / 2, centerY - ARROW_ICON / 2,
                        ARROW_ICON, ARROW_ICON), 0, FULL_UV, color);
                return;
            }
            float tipX = centerX + (direction < 0 ? -3 : 3);
            float backX = centerX + (direction < 0 ? 3 : -3);
            renderer.strokeLine(new Point(backX, centerY - 4), new Point(tipX, centerY),
                    1.5f, Paint.solid(color));
            renderer.strokeLine(new Point(tipX, centerY), new Point(backX, centerY + 4),
                    1.5f, Paint.solid(color));
        }

        private void relayout() {
            tabWidths = new float[tabs.size()];
            tabStarts = new float[tabs.size()];
            float total = 0;
            for (int index = 0; index < tabs.size(); index++) {
                tabWidths[index] = tabWidth(tabs.get(index));
                tabStarts[index] = total;
                total += tabWidths[index];
            }
            totalWidth = total;
            float width = bounds().width();
            overflow = total > width;
            viewportWidth = Math.max(1, overflow ? width - ARROW_WIDTH * 2 : width);
            maxScroll = Math.max(0, total - viewportWidth);
            scrollX = clamp(scrollX, 0, maxScroll);
        }

        private Rect tabBounds(int index) {
            float areaStart = overflow ? ARROW_WIDTH : 0;
            return new Rect(areaStart + tabStarts[index] - scrollX, 0,
                    tabWidths[index], bounds().height());
        }

        private Rect tabCloseBounds(int index) {
            if (!isTabClosable(tabs.get(index))) {
                return null;
            }
            Rect cell = tabBounds(index);
            return closeBounds(cell.x() + TAB_INSET, cell.width() - TAB_INSET * 2,
                    bounds().height() - TAB_INSET * 2);
        }

        private Rect closeBounds(float cellX, float cellWidth, float cellHeight) {
            if (cellWidth < CLOSE_SIZE * 2) {
                return null;
            }
            return new Rect(cellX + cellWidth - CLOSE_SIZE - CLOSE_INSET,
                    TAB_INSET + (cellHeight - CLOSE_SIZE) / 2, CLOSE_SIZE, CLOSE_SIZE);
        }

        private float closeReservation(float cellWidth, boolean closable) {
            return closable && cellWidth >= CLOSE_SIZE * 2 ? CLOSE_SIZE + CLOSE_INSET : 0;
        }

        private float textLimit(Tab tab, float cellWidth, boolean closable) {
            float iconWidth = tab.icon() != null ? iconSize : 0;
            float gap = iconWidth > 0 && !tab.title().isEmpty() ? ICON_GAP : 0;
            return Math.max(0, cellWidth - closeReservation(cellWidth, closable)
                    - tabPadding * 2 - iconWidth - gap);
        }

        private boolean isTitleTruncated(Tab tab) {
            int index = indexOf(tab);
            Font font = font();
            if (index < 0 || index >= tabWidths.length || font == null || tab.title().isEmpty()) {
                return false;
            }
            float cellWidth = Math.max(0, tabWidths[index] - TAB_INSET * 2);
            float maxText = textLimit(tab, cellWidth, isTabClosable(tab));
            return font.measure(tab.title()).width() > maxText + 0.01f;
        }

        private void updateTitleTooltip() {
            boolean needed = !dragging && hoveredTab != null && isTitleTruncated(hoveredTab);
            if (!needed) {
                if (tooltip() != null) {
                    setTooltip(null);
                }
                return;
            }
            if (!titleTooltip.text().equals(hoveredTab.title())) {
                titleTooltip.setText(hoveredTab.title());
            }
            if (tooltip() != titleTooltip) {
                setTooltip(titleTooltip);
            }
        }

        private void animateTabs(float deltaSeconds) {
            if (deltaSeconds <= 0) {
                return;
            }
            float blendAmount = 1 - (float) Math.exp(-TAB_ANIMATION_RATE * deltaSeconds);
            for (Tab tab : tabs) {
                tab.hoverAmount = approach(tab.hoverAmount, tab == hoveredTab ? 1 : 0, blendAmount);
                tab.activeAmount = approach(tab.activeAmount, tab == selected ? 1 : 0, blendAmount);
            }
        }

        private Tab tabAt(float x, float y) {
            if (y < 0 || y >= bounds().height()) {
                return null;
            }
            float areaStart = overflow ? ARROW_WIDTH : 0;
            if (x < areaStart || x >= bounds().width() - areaStart) {
                return null;
            }
            float local = x - areaStart + scrollX;
            for (int index = 0; index < tabs.size(); index++) {
                if (local >= tabStarts[index] && local < tabStarts[index] + tabWidths[index]) {
                    return tabs.get(index);
                }
            }
            return null;
        }

        private int arrowAt(float x, float y) {
            if (!overflow || y < 0 || y >= bounds().height()) {
                return 0;
            }
            if (x < ARROW_WIDTH) {
                return -1;
            }
            if (x >= bounds().width() - ARROW_WIDTH) {
                return 1;
            }
            return 0;
        }

        private Tab closeAt(float x, float y) {
            Tab tab = tabAt(x, y);
            if (tab == null || !isTabClosable(tab)) {
                return null;
            }
            int index = indexOf(tab);
            float cellX = (overflow ? ARROW_WIDTH : 0) + tabStarts[index] - scrollX + TAB_INSET;
            float cellWidth = Math.max(0, tabWidths[index] - TAB_INSET * 2);
            Rect close = closeBounds(cellX, cellWidth, Math.max(0, bounds().height() - TAB_INSET * 2));
            return close != null && close.contains(x, y) ? tab : null;
        }

        private void updateHover(float x, float y) {
            pointerX = x;
            pointerY = y;
            hoveredTab = tabAt(x, y);
            hoveredClose = closeAt(x, y);
            hoveredArrow = arrowAt(x, y);
            updateTitleTooltip();
        }

        private void ensureVisible(Tab tab) {
            int index = indexOf(tab);
            if (index < 0 || index >= tabStarts.length) {
                return;
            }
            float start = tabStarts[index];
            float end = start + tabWidths[index];
            if (start < scrollX) {
                scrollTo(start);
            } else if (end > scrollX + viewportWidth) {
                scrollTo(end - viewportWidth);
            }
        }

        private void scrollBy(float delta) {
            scrollTo(scrollX + delta);
        }

        private void scrollImmediately(float delta) {
            cancelScrollAnimation();
            scrollX = clamp(scrollX + delta, 0, maxScroll);
        }

        private void scrollTo(float target) {
            float clamped = clamp(target, 0, maxScroll);
            if (Math.abs(clamped - scrollX) < 0.5f) {
                scrollX = clamped;
                return;
            }
            Animator animator = context() == null ? null : context().animator();
            if (animator == null) {
                scrollX = clamped;
                return;
            }
            cancelScrollAnimation();
            animationOwner = animator;
            scrollAnimation = animator.animate(scrollX, clamped, SCROLL_SECONDS, Easing.EASE_OUT,
                    value -> scrollX = value).setOnComplete(() -> scrollAnimation = null);
        }

        private void cancelScrollAnimation() {
            if (scrollAnimation != null && animationOwner != null) {
                animationOwner.cancel(scrollAnimation);
                scrollAnimation = null;
            }
            animationOwner = null;
        }

        /**
         * Updates hover on pointer movement.
         *
         * @param x horizontal pointer position
         * @param y vertical pointer position
         * @return false so the event can still bubble
         */
        @Override
        protected boolean onPointerMove(float x, float y) {
            updateHover(x, y);
            return false;
        }

        /**
         * Marks the pointer as inside the strip.
         */
        @Override
        protected void onPointerEnter() {
            pointerInside = true;
        }

        /**
         * Clears hover state when the pointer leaves.
         */
        @Override
        protected void onPointerExit() {
            pointerInside = false;
            hoveredTab = null;
            hoveredClose = null;
            hoveredArrow = 0;
            updateTitleTooltip();
        }

        /**
         * Selects, closes, scrolls or starts dragging a tab.
         *
         * @param x horizontal pointer position
         * @param y vertical pointer position
         * @param button pressed button
         * @param modifiers active modifiers
         * @return true when the strip consumed the press
         */
        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT) {
                return true;
            }
            updateHover(x, y);
            dragTab = null;
            dragging = false;
            dragDirection = 0;
            if (hoveredArrow != 0) {
                scrollBy(hoveredArrow * viewportWidth * 0.75f);
                return true;
            }
            if (hoveredClose != null) {
                closeTab(hoveredClose);
                return true;
            }
            Tab tab = tabAt(x, y);
            if (tab == null) {
                return true;
            }
            if (!tab.isLocked()) {
                select(tab);
                dragTab = isTabMovable(tab) ? tab : null;
            }
            return true;
        }

        /**
         * Starts reordering when the pressed tab is movable.
         *
         * @param x horizontal pointer position
         * @param y vertical pointer position
         * @param button pressed button
         * @param modifiers modifiers captured at press time
         * @return true when a drag started
         */
        @Override
        protected boolean onDragStart(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT || dragTab == null) {
                return false;
            }
            dragging = true;
            dragPointerX = x;
            dragPointerY = y;
            dragDirection = dragEdgeDirection(x);
            return true;
        }

        /**
         * Reorders the dragged tab when the pointer crosses a neighbour's midpoint.
         * Holding the pointer near a strip edge keeps scrolling and reordering.
         *
         * @param x horizontal pointer position
         * @param y vertical pointer position
         * @param button dragged button
         * @param modifiers modifiers captured at press time
         * @return true when a tab was being dragged
         */
        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            if (!dragging || dragTab == null) {
                return false;
            }
            dragPointerX = x;
            dragPointerY = y;
            dragDirection = dragEdgeDirection(x);
            updateDragTarget();
            return true;
        }

        /**
         * Ends a tab drag.
         *
         * @param x horizontal pointer position
         * @param y vertical pointer position
         * @param button released button
         * @param modifiers modifiers captured at press time
         * @return true when a drag was active
         */
        @Override
        protected boolean onDragEnd(float x, float y, MouseButton button, Modifiers modifiers) {
            boolean dragged = dragging;
            clearDrag();
            return dragged;
        }

        /**
         * Scrolls the strip with the mouse wheel.
         *
         * @param x horizontal pointer position
         * @param y vertical pointer position
         * @param deltaX horizontal wheel amount
         * @param deltaY vertical wheel amount
         * @return true when the strip consumed the wheel
         */
        @Override
        protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
            if (!overflow) {
                return false;
            }
            scrollBy((deltaX != 0 ? deltaX : -deltaY) * WHEEL_STEP);
            return true;
        }

        private int dragEdgeDirection(float x) {
            if (!overflow) {
                return 0;
            }
            if (x < ARROW_WIDTH + DRAG_EDGE) {
                return -1;
            }
            if (x > bounds().width() - ARROW_WIDTH - DRAG_EDGE) {
                return 1;
            }
            return 0;
        }

        private void updateDragTarget() {
            int from = indexOf(dragTab);
            if (from < 0) {
                return;
            }
            float areaStart = overflow ? ARROW_WIDTH : 0;
            float pointer = dragPointerX - areaStart + scrollX;
            while (from + 1 < tabs.size()
                    && pointer >= tabStarts[from + 1] + tabWidths[from + 1] / 2) {
                moveTab(from, from + 1, false);
                from++;
            }
            while (from > 0 && pointer < tabStarts[from - 1] + tabWidths[from - 1] / 2) {
                moveTab(from, from - 1, false);
                from--;
            }
        }

        private void clearDrag() {
            dragTab = null;
            dragging = false;
            dragDirection = 0;
        }
    }
}
