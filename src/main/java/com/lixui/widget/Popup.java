package com.lixui.widget;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.platform.Cursor;
import com.lixui.render.RenderManager;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;

import java.util.Objects;

/**
 * A floating overlay that hosts a single content widget and positions it next to an anchor.
 * The popup fills the viewport while open, is drawn above regular widgets and closes when the user
 * presses outside its surface or presses Escape. It is generic: dropdowns, menus and other
 * transient panels provide their own content.
 */
public class Popup extends Panel {
    private static final float DEFAULT_PADDING = 4;
    private static final float DEFAULT_GAP = 2;
    private static final float DEFAULT_VIEWPORT_MARGIN = 4;

    private Widget content;
    private Rect anchor = Rect.EMPTY;
    private Rect popupBounds = Rect.EMPTY;
    private float preferredWidth;
    private float preferredHeight;
    private float padding = DEFAULT_PADDING;
    private float gap = DEFAULT_GAP;
    private float viewportMargin = DEFAULT_VIEWPORT_MARGIN;
    private boolean open;
    private boolean closeOnOutsideClick = true;
    private Runnable closeListener;

    /**
     * Creates a closed popup with no content.
     */
    public Popup() {
        setFillViewport(true);
        setAlwaysOnTop(true);
        setFocusable(true);
        setFocusOutlineEnabled(false);
    }

    /**
     * Returns the content widget shown inside the popup.
     *
     * @return the content widget, or null when the popup is empty
     */
    public final Widget content() {
        return content;
    }

    /**
     * Replaces the content widget.
     *
     * @param content content widget without a parent, or null to clear the content
     * @throws IllegalArgumentException if the widget is this popup or already has a parent
     */
    public final void setContent(Widget content) {
        if (content == this.content) {
            return;
        }
        if (content != null) {
            add(content);
        }
        Widget previous = this.content;
        this.content = content;
        if (previous != null) {
            remove(previous);
        }
    }

    /**
     * Reports whether the popup is currently shown.
     *
     * @return true while the popup is open
     */
    public final boolean isOpen() {
        return open;
    }

    /**
     * Returns the rectangle occupied by the popup surface in viewport coordinates.
     *
     * @return the popup bounds, or an empty rectangle while closed
     */
    public final Rect popupBounds() {
        return popupBounds;
    }

    /**
     * Returns the distance between the popup content and its surface edge.
     *
     * @return the padding in logical UI units
     */
    public final float padding() {
        return padding;
    }

    /**
     * Sets the distance between the popup content and its surface edge.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is non-finite or negative
     */
    public final void setPadding(float padding) {
        if (!Float.isFinite(padding) || padding < 0) {
            throw new IllegalArgumentException("Popup padding must be finite and non-negative.");
        }
        this.padding = padding;
    }

    /**
     * Returns the gap between the anchor and the popup surface.
     *
     * @return the gap in logical UI units
     */
    public final float gap() {
        return gap;
    }

    /**
     * Sets the gap between the anchor and the popup surface.
     *
     * @param gap non-negative gap in logical UI units
     * @throws IllegalArgumentException if the gap is non-finite or negative
     */
    public final void setGap(float gap) {
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException("Popup gap must be finite and non-negative.");
        }
        this.gap = gap;
    }

    /**
     * Returns the minimum distance between the popup and the viewport edges.
     *
     * @return the viewport margin in logical UI units
     */
    public final float viewportMargin() {
        return viewportMargin;
    }

    /**
     * Sets the minimum distance between the popup and the viewport edges.
     *
     * @param viewportMargin non-negative margin in logical UI units
     * @throws IllegalArgumentException if the margin is non-finite or negative
     */
    public final void setViewportMargin(float viewportMargin) {
        if (!Float.isFinite(viewportMargin) || viewportMargin < 0) {
            throw new IllegalArgumentException("Popup viewport margin must be finite and non-negative.");
        }
        this.viewportMargin = viewportMargin;
    }

    /**
     * Reports whether a press outside the popup closes it.
     *
     * @return true when outside presses dismiss the popup
     */
    public final boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    /**
     * Enables or disables closing the popup when the user presses outside its surface.
     *
     * @param closeOnOutsideClick true to dismiss the popup on an outside press
     */
    public final void setCloseOnOutsideClick(boolean closeOnOutsideClick) {
        this.closeOnOutsideClick = closeOnOutsideClick;
    }

    /**
     * Returns the callback invoked after the popup closes.
     *
     * @return the close listener, or null when none is assigned
     */
    public final Runnable closeListener() {
        return closeListener;
    }

    /**
     * Assigns a callback invoked after the popup closes.
     *
     * @param closeListener listener to run, or null to clear it
     */
    public final void setCloseListener(Runnable closeListener) {
        this.closeListener = closeListener;
    }

    /**
     * Opens the popup next to an anchor.
     * The popup prefers the space below the anchor and flips above when it does not fit.
     *
     * @param context context that owns the widget manager
     * @param anchor anchor rectangle in viewport coordinates
     * @param width preferred surface width in logical UI units
     * @param height preferred surface height in logical UI units
     * @throws NullPointerException if {@code context} or {@code anchor} is null
     * @throws IllegalArgumentException if a dimension is non-finite or negative
     * @throws IllegalStateException if the context has no manager
     */
    public final void open(UiContext context, Rect anchor, float width, float height) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(anchor, "anchor");
        if (!Float.isFinite(width) || width < 0 || !Float.isFinite(height) || height < 0) {
            throw new IllegalArgumentException("Popup size must be finite and non-negative.");
        }
        this.anchor = anchor;
        this.preferredWidth = width;
        this.preferredHeight = height;
        if (open) {
            return;
        }
        open = true;
        context.addTopLevel(this);
        context.requestFocus(this);
    }

    /**
     * Closes the popup and removes it from the widget manager.
     */
    public final void close() {
        if (!open) {
            return;
        }
        open = false;
        popupBounds = Rect.EMPTY;
        UiContext owner = context();
        if (owner != null) {
            try {
                owner.removeTopLevel(this);
            } catch (IllegalStateException ignored) {
                // The manager may already be closing and detach every top-level widget.
            }
        }
        notifyClosed();
    }

    /**
     * Returns the popup surface height required for the configured content size.
     *
     * @param constraints available and required dimensions
     * @return the popup surface size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        return new Size(preferredWidth + padding * 2, preferredHeight + padding * 2);
    }

    /**
     * Positions the popup surface and lays out its content.
     */
    @Override
    protected void onLayout() {
        float viewportWidth = localBounds().width();
        float viewportHeight = localBounds().height();
        if (!open || viewportWidth <= 0 || viewportHeight <= 0) {
            popupBounds = Rect.EMPTY;
            return;
        }
        float width = Math.min(preferredWidth + padding * 2,
                Math.max(0, viewportWidth - viewportMargin * 2));
        float height = Math.min(preferredHeight + padding * 2,
                Math.max(0, viewportHeight - viewportMargin * 2));
        float x = Math.clamp(anchor.x(), viewportMargin,
                Math.max(viewportMargin, viewportWidth - width - viewportMargin));
        float below = anchor.bottom() + gap;
        boolean fitsBelow = below + height <= viewportHeight - viewportMargin;
        float y = fitsBelow ? below : anchor.y() - gap - height;
        y = Math.clamp(y, viewportMargin,
                Math.max(viewportMargin, viewportHeight - height - viewportMargin));
        popupBounds = new Rect(x, y, width, height);
        if (content != null) {
            content.layout(new Rect(popupBounds.x() + padding, popupBounds.y() + padding,
                    Math.max(0, width - padding * 2),
                    Math.max(0, height - padding * 2)));
        }
    }

    /**
     * Paints the popup surface.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (!open || popupBounds.isEmpty()) {
            return;
        }
        WidgetStyle style = style();
        if (style == null) {
            return;
        }
        WidgetState state = isEnabled() ? WidgetState.NORMAL : WidgetState.DISABLED;
        renderer.roundedRect(popupBounds, style.cornerRadius(), style.background(state));
        float borderWidth = style.borderWidth();
        if (borderWidth <= 0) {
            return;
        }
        float inset = borderWidth / 2;
        Rect border = new Rect(popupBounds.x() + inset, popupBounds.y() + inset,
                Math.max(0, popupBounds.width() - borderWidth),
                Math.max(0, popupBounds.height() - borderWidth));
        if (border.isEmpty()) {
            return;
        }
        renderer.strokeRoundedRect(border, Math.max(0, style.cornerRadius() - inset),
                borderWidth, Paint.solid(style.border(state)));
    }

    /**
     * Clips the content to the popup surface.
     *
     * @param child direct child being drawn or hit-tested
     * @return the content rectangle
     */
    @Override
    protected Rect childClip(Widget child) {
        if (child != content) {
            return null;
        }
        return new Rect(popupBounds.x() + padding, popupBounds.y() + padding,
                Math.max(0, popupBounds.width() - padding * 2),
                Math.max(0, popupBounds.height() - padding * 2));
    }

    /**
     * Closes the popup when the user presses outside its surface.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press was consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (button == MouseButton.LEFT && closeOnOutsideClick
                && !popupBounds.contains(x, y)) {
            close();
            return true;
        }
        return false;
    }

    /**
     * Closes the popup on Escape.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    /**
     * Requests the pointer cursor for the popup surface.
     *
     * @return the default cursor
     */
    @Override
    public Cursor cursor() {
        return Cursor.DEFAULT;
    }

    /**
     * Marks the popup as closed when it is removed externally.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        if (open) {
            open = false;
            popupBounds = Rect.EMPTY;
            notifyClosed();
        }
    }

    private void notifyClosed() {
        if (closeListener != null) {
            closeListener.run();
        }
    }
}
