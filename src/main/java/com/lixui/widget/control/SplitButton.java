package com.lixui.widget.control;

import com.lixui.geometry.Point;
import com.lixui.geometry.Rect;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.ImageStyle;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.UiContext;
import com.lixui.widget.overlay.ContextMenu;

/**
 * A button split into a primary action area and an arrow area that opens a context menu.
 * The primary area displays a label and an optional icon and runs the assigned action. The arrow
 * area opens the configured {@link ContextMenu}, whose items may carry icons, shortcuts, submenus
 * and their own actions. The menu is empty until items are added through {@link #menu()}.
 */
public class SplitButton extends Button {
    static final float MENU_GAP = 6;

    private final IconSet icons = new IconSet("arrow-down-04.svg");
    private final ContextMenu menu = new ContextMenu();
    private boolean pointerFocus;

    /**
     * Creates a split button with an empty label.
     */
    public SplitButton() {
        this("");
    }

    /**
     * Creates a split button with a text label.
     *
     * @param text primary action label
     * @throws NullPointerException if {@code text} is null
     */
    public SplitButton(String text) {
        super(text);
    }

    /**
     * Returns the context menu opened by the arrow area.
     * Add items to configure the menu before or after attaching the button.
     *
     * @return the owned context menu, never null
     */
    public final ContextMenu menu() {
        return menu;
    }

    /**
     * Reports whether the context menu is currently open.
     *
     * @return true while the menu is visible
     */
    public final boolean isMenuOpen() {
        return menu.isOpen();
    }

    /**
     * Opens the context menu below the arrow area when the button is enabled and the menu has items.
     */
    public final void openMenu() {
        if (!isEnabled() || context() == null || menu.items().isEmpty()) {
            return;
        }
        Point origin = absolutePosition();
        float x = origin.x() + localBounds().width() - trailingSlotWidth();
        float y = origin.y() + localBounds().height() + MENU_GAP;
        menu.open(context(), x, y);
    }

    /**
     * Closes the context menu.
     */
    public final void closeMenu() {
        menu.close();
    }

    /**
     * Reserves the arrow area to the right of the label and icon.
     *
     * @return the arrow slot width in logical UI units
     */
    @Override
    protected float trailingSlotWidth() {
        WidgetStyle style = surfaceStyle();
        float padding = style == null ? 0 : style.padding();
        return iconSize() + padding + MENU_GAP;
    }

    /**
     * Paints the separator and the menu arrow after the regular button content.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        super.onDraw(renderer, frame);
        WidgetStyle style = surfaceStyle();
        float height = localBounds().height();
        if (style == null || localBounds().isEmpty()) {
            return;
        }
        float inset = height * 0.2f;
        if (height - inset * 2 > 0) {
            renderer.rect(new Rect(localBounds().width() - trailingSlotWidth(), inset, 1,
                    height - inset * 2), style.border(WidgetState.NORMAL));
        }
        ensureIcons();
        Texture arrow = icons.get(0);
        if (arrow == null) {
            return;
        }
        float arrowSize = iconSize();
        float padding = style.padding();
        renderer.icon(arrow, new Rect(localBounds().width() - padding - arrowSize,
                (height - arrowSize) / 2, arrowSize, arrowSize),
                ImageStyle.ICON, style.text(state()));
    }

    /**
     * Runs the primary action or opens the menu depending on the clicked area.
     * When the menu has no items, the whole button runs the primary action.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click is consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT || !isEnabled()) {
            return false;
        }
        if (isArrowPosition(x)) {
            openMenu();
            return true;
        }
        return super.onClick(x, y, button, modifiers);
    }

    /**
     * Routes a double click through the same area activation as a single click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the double click is consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT || !isEnabled()) {
            return false;
        }
        if (isArrowPosition(x)) {
            openMenu();
            return true;
        }
        return super.onDoubleClick(x, y, button, modifiers);
    }

    /**
     * Marks the focus as keyboard-driven so the focus outline is visible again.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key is consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        pointerFocus = false;
        if (key == Key.DOWN) {
            openMenu();
            return true;
        }
        return super.onKeyPressed(key, modifiers, repeat);
    }

    /**
     * Remembers that focus came from a pointer press so the focus outline stays hidden.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return false so the press can still bubble
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        pointerFocus = true;
        return false;
    }

    /**
     * Resets the pointer focus mode when focus changes.
     *
     * @param focused whether this widget gained focus
     */
    @Override
    protected void onFocusChanged(boolean focused) {
        super.onFocusChanged(focused);
        pointerFocus = false;
    }

    /**
     * Hides the focus outline while focus was obtained by a pointer press.
     *
     * @return the interaction state with pointer focus mapped to the normal state
     */
    @Override
    protected WidgetState state() {
        WidgetState state = super.state();
        return state == WidgetState.FOCUSED && pointerFocus ? WidgetState.NORMAL : state;
    }

    private boolean isArrowPosition(float x) {
        return !menu.items().isEmpty() && x >= localBounds().width() - trailingSlotWidth();
    }

    private void ensureIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(iconSize() * context.density()));
        if (!icons.matches(context, size)) {
            icons.reload(context, size);
        }
    }

    /**
     * Closes the menu and releases the arrow icon when the button leaves the tree.
     */
    @Override
    protected void onDetached() {
        menu.close();
        super.onDetached();
        icons.release(context());
    }

    /**
     * Closes the menu and drops the arrow icon after a context change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        menu.close();
        icons.release(context());
    }
}
