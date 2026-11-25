package com.lixui.widget.control;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
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
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.IconSet;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.Panel;
import com.lixui.widget.UiContext;
import com.lixui.widget.overlay.Popup;

/**
 * Shared field for controls that open a popup picker.
 * The field paints a rounded surface with the value text and a trailing icon. A completed click,
 * Enter, Space or an arrow key opens the popup, while Escape closes it. Subclasses provide the
 * displayed text, the field icon and the popup content. Closing the popup returns focus to the
 * field when the field is still available.
 */
abstract class PickerField extends Panel {
    private static final float DEFAULT_ICON_SIZE = 16;
    private static final float ICON_GAP = 6;

    private final Popup popup = new Popup();
    private final IconSet fieldIcons;
    private float iconSize = DEFAULT_ICON_SIZE;

    /**
     * Creates a field with a trailing bundled icon.
     *
     * @param iconName bundled icon resource name
     */
    protected PickerField(String iconName) {
        fieldIcons = new IconSet(iconName);
        setFocusable(true);
        popup.setFocusScope(true);
        popup.setCloseListener(this::restoreFieldFocus);
    }

    /**
     * Returns the popup hosted by this picker.
     *
     * @return the picker popup
     */
    protected final Popup popup() {
        return popup;
    }

    /**
     * Returns the field icon edge length.
     *
     * @return the icon size in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the field icon edge length.
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
     * Reports whether the picker popup is open.
     *
     * @return true while the popup is visible
     */
    public final boolean isPopupOpen() {
        return popup.isOpen();
    }

    /**
     * Returns the text painted inside the field.
     *
     * @return the field text, never null
     */
    protected abstract String fieldText();

    /**
     * Returns the horizontal space reserved before the text for custom leading content.
     * The default reserves no space.
     *
     * @return the leading slot width in logical UI units
     */
    protected float leadingWidth() {
        return 0;
    }

    /**
     * Paints custom leading content inside the reserved slot.
     * The default draws nothing.
     *
     * @param renderer drawing manager for the current frame
     * @param bounds leading slot in field-local coordinates
     * @param state current widget state
     * @param style resolved field style
     */
    protected void drawLeading(RenderManager renderer, Rect bounds, WidgetState state,
                               WidgetStyle style) {
    }

    /**
     * Opens the popup for the current value.
     */
    protected abstract void openPopup();

    /**
     * Closes the picker popup.
     */
    protected final void closePopup() {
        popup.close();
    }

    /**
     * Measures the value text and the trailing icon.
     *
     * @param constraints available and required dimensions
     * @return the natural field size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        WidgetStyle style = resolvedStyle();
        if (style == null) {
            return super.onMeasure(constraints);
        }
        Font font = font();
        Size textSize = font == null ? new Size(0, 0) : font.measure(fieldText());
        float leading = leadingWidth();
        float contentWidth = leading + textSize.width() + iconSize + ICON_GAP;
        float contentHeight = Math.max(textSize.height(), iconSize);
        float padding = style.padding();
        return new Size(contentWidth + padding * 2, contentHeight + padding * 2);
    }

    /**
     * Paints the field surface, the value text and the trailing icon.
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
        WidgetState state = state();
        renderer.roundedRect(localBounds(), style.cornerRadius(), style.background(state));
        drawBorder(renderer, style, state);
        Font font = font();
        float padding = style.padding();
        float leading = leadingWidth();
        float textX = padding + leading;
        float iconX = localBounds().width() - padding - iconSize;
        float contentWidth = Math.max(0, iconX - ICON_GAP - textX);
        drawLeading(renderer, new Rect(padding, padding, leading,
                Math.max(0, localBounds().height() - padding * 2)), state, style);
        if (font != null) {
            renderer.text(font, fieldText(), textX, localBounds().height() / 2,
                    style.text(state), new TextStyle(contentWidth, 0, 1, TextOverflow.ELLIPSIS,
                            HorizontalAlignment.LEFT, VerticalAlignment.CENTER));
        }
        ensureFieldIcon();
        Texture icon = fieldIcons.get(0);
        if (icon != null) {
            renderer.icon(icon, new Rect(iconX, (localBounds().height() - iconSize) / 2,
                    iconSize, iconSize), ImageStyle.ICON, style.text(state));
        }
    }

    private void drawBorder(RenderManager renderer, WidgetStyle style, WidgetState state) {
        float width = style.borderWidth();
        if (width <= 0) {
            return;
        }
        float inset = width / 2;
        Rect bounds = localBounds();
        Rect border = new Rect(inset, inset,
                Math.max(0, bounds.width() - width), Math.max(0, bounds.height() - width));
        if (border.isEmpty()) {
            return;
        }
        renderer.strokeRoundedRect(border, Math.max(0, style.cornerRadius() - inset),
                width, Paint.solid(style.border(state)));
    }

    /**
     * Returns the pointer cursor for the field.
     *
     * @return the pointer cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.POINTER;
    }

    /**
     * Opens the popup on a completed left click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click was consumed
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button != MouseButton.LEFT || !isEnabled()) {
            return false;
        }
        openPopup();
        return true;
    }

    /**
     * Opens the popup on a double click.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button double-clicked button
     * @param modifiers active modifiers
     * @return true when the double click was consumed
     */
    @Override
    protected boolean onDoubleClick(float x, float y, MouseButton button, Modifiers modifiers) {
        return onClick(x, y, button, modifiers);
    }

    /**
     * Opens the popup with Enter, Space or an arrow key and closes it on Escape.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ESCAPE && popup.isOpen()) {
            closePopup();
            return true;
        }
        if (key == Key.ENTER || key == Key.SPACE || key == Key.UP || key == Key.DOWN) {
            openPopup();
            return true;
        }
        return false;
    }

    /**
     * Closes the popup and releases the field icon when the picker leaves the tree.
     */
    @Override
    protected void onDetached() {
        closePopup();
        super.onDetached();
        fieldIcons.release(context());
    }

    /**
     * Closes the popup and drops the cached field icon after a context change.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        closePopup();
        fieldIcons.release(context());
    }

    private void ensureFieldIcon() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(iconSize * context.density()));
        if (!fieldIcons.matches(context, size)) {
            fieldIcons.reload(context, size);
        }
    }

    private void restoreFieldFocus() {
        if (context() == null || !isEnabled()) {
            return;
        }
        try {
            requestFocus();
        } catch (IllegalArgumentException ignored) {
            // The picker may be detaching while its popup is being closed.
        }
    }

    private WidgetStyle resolvedStyle() {
        if (hasLocalStyle()) {
            return style();
        }
        return context() == null ? null : context().theme().controlStyle();
    }
}
