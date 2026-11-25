package com.lixui.widget.overlay;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.graphics.Paint;
import com.lixui.input.Key;
import com.lixui.input.Modifiers;
import com.lixui.input.MouseButton;
import com.lixui.render.RenderManager;
import com.lixui.render.Texture;
import com.lixui.theme.WidgetState;
import com.lixui.theme.WidgetStyle;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.control.Button;
import com.lixui.widget.control.Image;
import com.lixui.widget.control.Label;
import com.lixui.widget.control.Spacer;
import com.lixui.widget.image.ImageSource;
import com.lixui.widget.layout.HBox;
import com.lixui.widget.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A modal overlay that dims the viewport and centers a titled panel with a message, optional custom
 * content and action buttons.
 * The overlay fills the viewport, stays above regular widgets and confines Tab traversal to the
 * dialog. Pointer and keyboard input reach only the dialog while it is open. Escape activates the
 * negative action, and a press on the dimmed background can optionally do the same. The scrim can
 * be hidden without changing the dialog modality or the backdrop dismissal behavior.
 * The title can be prefixed with an icon that is scaled to the configured icon size and reloaded
 * when the display density changes.
 */
public class ModalDialog extends Popup {
    private static final float DEFAULT_WIDTH = 420;
    private static final float DEFAULT_PADDING = 20;
    private static final float DEFAULT_GAP = 14;
    private static final float DEFAULT_VIEWPORT_MARGIN = 24;
    private static final float ACTION_GAP = 8;
    private static final float DEFAULT_ICON_SIZE = 24;
    private static final float ICON_GAP = 10;
    private static final Color DEFAULT_SCRIM = new Color(0, 0, 0, 0.55f);

    private final VBox contentBox = new VBox();
    private final Label titleLabel = new Label();
    private final TitleRow titleRow = new TitleRow(titleLabel);
    private final Label messageLabel = new Label();
    private final BodySlot bodySlot = new BodySlot();
    private final HBox actionRow = new HBox();
    private final List<Button> primaryButtons = new ArrayList<>();

    private Rect panelBounds = Rect.EMPTY;
    private Color scrimColor = DEFAULT_SCRIM;
    private boolean scrimEnabled = true;
    private float preferredWidth = DEFAULT_WIDTH;
    private Runnable cancelAction;
    private UiContext ownerContext;
    private Widget restoreFocus;

    /**
     * Creates an empty closed dialog with no title, message or actions.
     */
    public ModalDialog() {
        setFocusScope(true);
        setCloseOnOutsideClick(false);
        setPadding(DEFAULT_PADDING);
        setViewportMargin(DEFAULT_VIEWPORT_MARGIN);
        titleLabel.setAutoSize(false);
        titleLabel.setWrap(true);
        messageLabel.setAutoSize(false);
        messageLabel.setWrap(true);
        titleLabel.setVisible(false);
        messageLabel.setVisible(false);
        bodySlot.setVisible(false);
        actionRow.setGap(ACTION_GAP);
        actionRow.add(new Spacer());
        contentBox.setGap(DEFAULT_GAP);
        contentBox.add(titleRow).add(messageLabel).add(bodySlot).add(actionRow);
        super.setContent(contentBox);
    }

    /**
     * Returns the dialog title.
     *
     * @return the title, or an empty string when none is assigned
     */
    public final String title() {
        return titleLabel.text();
    }

    /**
     * Replaces the dialog title.
     * An empty title hides the title row.
     *
     * @param title new title
     * @throws NullPointerException if {@code title} is null
     */
    public final void setTitle(String title) {
        titleLabel.setText(Objects.requireNonNull(title, "title"));
        titleLabel.setVisible(!title.isEmpty());
        titleRow.refreshVisibility();
    }

    /**
     * Returns the dialog message.
     *
     * @return the message, or an empty string when none is assigned
     */
    public final String message() {
        return messageLabel.text();
    }

    /**
     * Replaces the dialog message.
     * An empty message hides the message row.
     *
     * @param message new message
     * @throws NullPointerException if {@code message} is null
     */
    public final void setMessage(String message) {
        messageLabel.setText(Objects.requireNonNull(message, "message"));
        messageLabel.setVisible(!message.isEmpty());
    }

    /**
     * Returns the icon shown to the left of the title.
     *
     * @return the icon source, or null when no icon is shown
     */
    public final ImageSource icon() {
        return titleRow.icon();
    }

    /**
     * Assigns an icon drawn to the left of the title.
     * Bundled icons are rasterized at the configured icon size and display density, while a texture
     * source draws the supplied texture. Passing null removes the icon and restores the layout
     * without an icon.
     *
     * @param icon image source, or null to remove the icon
     */
    public final void setIcon(ImageSource icon) {
        titleRow.setIcon(icon);
    }

    /**
     * Assigns a caller-owned texture icon drawn to the left of the title.
     * The dialog never releases the texture. Passing null removes the icon and restores the layout
     * without an icon.
     *
     * @param icon icon texture, or null to remove the icon
     */
    public final void setIcon(Texture icon) {
        titleRow.setIcon(icon);
    }

    /**
     * Removes the title icon and restores the layout without an icon.
     */
    public final void clearIcon() {
        titleRow.clearIcon();
    }

    /**
     * Returns the square title icon size.
     *
     * @return the icon edge length in logical UI units
     */
    public final float iconSize() {
        return titleRow.iconSize();
    }

    /**
     * Sets the square title icon size.
     * The title width shrinks by the icon size and the icon gap so the icon never covers the text.
     *
     * @param iconSize positive edge length in logical UI units
     * @throws IllegalArgumentException if the size is non-positive or non-finite
     */
    public final void setIconSize(float iconSize) {
        titleRow.setIconSize(iconSize);
    }

    /**
     * Returns the explicit title icon tint.
     *
     * @return the icon tint, or null when the dialog text color is used
     */
    public final Color iconTint() {
        return titleRow.iconTint();
    }

    /**
     * Assigns an explicit title icon tint.
     * Passing null restores the dialog text color, so bundled monochrome icons match the title.
     *
     * @param iconTint icon tint, or null to follow the dialog text color
     */
    public final void setIconTint(Color iconTint) {
        titleRow.setIconTint(iconTint);
    }

    /**
     * Returns the custom content shown between the message and the actions.
     *
     * @return the custom content, or null when none is assigned
     */
    public final Widget body() {
        return bodySlot.child();
    }

    /**
     * Replaces the custom content shown between the message and the actions.
     * The widget is stretched across the dialog content area.
     *
     * @param body widget without a parent, or null to remove the custom content
     * @throws IllegalArgumentException if the widget is this dialog or already has a parent
     */
    public final void setBody(Widget body) {
        bodySlot.setChild(body);
        bodySlot.setVisible(body != null);
    }

    /**
     * Returns the preferred dialog width before it is clamped to the viewport.
     *
     * @return the preferred width in logical UI units
     */
    public final float preferredWidth() {
        return preferredWidth;
    }

    /**
     * Sets the preferred dialog width. The dialog shrinks when the viewport is narrower.
     *
     * @param preferredWidth positive width in logical UI units
     * @throws IllegalArgumentException if the width is non-finite or not positive
     */
    public final void setPreferredWidth(float preferredWidth) {
        if (!Float.isFinite(preferredWidth) || preferredWidth <= 0) {
            throw new IllegalArgumentException("Dialog width must be finite and positive.");
        }
        this.preferredWidth = preferredWidth;
    }

    /**
     * Returns the color of the layer drawn behind the dialog.
     *
     * @return the scrim color
     */
    public final Color scrimColor() {
        return scrimColor;
    }

    /**
     * Replaces the color of the layer drawn behind the dialog.
     *
     * @param scrimColor scrim color; the alpha component controls how much of the background shows
     * @throws NullPointerException if {@code scrimColor} is null
     */
    public final void setScrimColor(Color scrimColor) {
        this.scrimColor = Objects.requireNonNull(scrimColor, "scrimColor");
    }

    /**
     * Reports whether the background scrim is painted behind the dialog.
     *
     * @return true when the scrim is painted
     */
    public final boolean isScrimEnabled() {
        return scrimEnabled;
    }

    /**
     * Enables or disables the background scrim.
     * The dialog still fills the viewport and blocks background input while the scrim is disabled,
     * and the configured scrim color is kept for when the scrim is enabled again. This setting is
     * independent of backdrop dismissal.
     *
     * @param scrimEnabled true to paint the configured scrim color
     */
    public final void setScrimEnabled(boolean scrimEnabled) {
        this.scrimEnabled = scrimEnabled;
    }

    /**
     * Returns the rectangle occupied by the dialog panel in viewport coordinates.
     *
     * @return the panel bounds, or an empty rectangle while the dialog is closed
     */
    public final Rect panelBounds() {
        return panelBounds;
    }

    /**
     * Returns the action invoked when the dialog is cancelled.
     *
     * @return the negative action, or null when none is assigned
     */
    public final Runnable cancelAction() {
        return cancelAction;
    }

    /**
     * Assigns the action invoked when the dialog is cancelled by Escape, a background press or
     * {@link #cancel()}.
     *
     * @param cancelAction negative action, or null to clear it
     */
    public final void setOnCancel(Runnable cancelAction) {
        this.cancelAction = cancelAction;
    }

    /**
     * Appends an action button.
     * Activating the button runs the handler and then closes the dialog.
     *
     * @param text button label
     * @param primary true to paint the button with the theme primary style
     * @param handler action to run before closing, or null for a closing button without an action
     * @return the created button
     * @throws NullPointerException if {@code text} is null
     */
    public final Button addAction(String text, boolean primary, Runnable handler) {
        Button button = new Button(Objects.requireNonNull(text, "text"));
        button.setOnAction(() -> {
            try {
                if (handler != null) {
                    handler.run();
                }
            } finally {
                close();
            }
        });
        actionRow.add(button);
        if (primary) {
            primaryButtons.add(button);
            applyPrimaryStyle(button);
        }
        return button;
    }

    /**
     * Appends a non-primary action button.
     *
     * @param text button label
     * @param handler action to run before closing, or null for a closing button without an action
     * @return the created button
     * @throws NullPointerException if {@code text} is null
     */
    public final Button addAction(String text, Runnable handler) {
        return addAction(text, false, handler);
    }

    /**
     * Shows the dialog centered in the viewport.
     * The widget that owned keyboard focus before the first open is focused again after the dialog
     * closes and is still available. Focus moved elsewhere, for example to a dialog opened by an
     * action, is preserved.
     *
     * @param context context that owns the widget manager
     * @throws NullPointerException if {@code context} is null
     * @throws IllegalStateException if the context has no manager
     */
    public final void show(UiContext context) {
        Objects.requireNonNull(context, "context");
        Widget previousFocus = context.focused();
        boolean wasOpen = isOpen();
        open(context, Rect.EMPTY, 0, 0);
        if (!wasOpen) {
            ownerContext = context;
            restoreFocus = previousFocus;
        }
        Widget target = initialFocus();
        if (target != null && target.context() == context) {
            context.requestFocus(target);
        }
    }

    /**
     * Cancels the dialog, runs the negative action and closes it.
     * Calling this method while the dialog is closed has no effect.
     */
    public final void cancel() {
        if (!isOpen()) {
            return;
        }
        try {
            if (cancelAction != null) {
                cancelAction.run();
            }
        } finally {
            close();
        }
    }

    /**
     * Returns the widget that receives focus when the dialog opens.
     * The default keeps focus on the overlay itself.
     *
     * @return the initial focus target, or null to keep focus on the overlay
     */
    protected Widget initialFocus() {
        return null;
    }

    /**
     * Centers the panel and lays out the dialog content.
     */
    @Override
    protected void onLayout() {
        if (!isOpen()) {
            panelBounds = Rect.EMPTY;
            return;
        }
        float viewportWidth = localBounds().width();
        float viewportHeight = localBounds().height();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            panelBounds = Rect.EMPTY;
            return;
        }
        float margin = viewportMargin();
        float padding = padding();
        float maximumWidth = Math.max(0, viewportWidth - margin * 2);
        float width = Math.min(preferredWidth, maximumWidth);
        float contentWidth = Math.max(0, width - padding * 2);
        float maximumHeight = Math.max(0, viewportHeight - margin * 2);
        Size natural = contentBox.measure(
                LayoutConstraints.loose(contentWidth, Math.max(0, maximumHeight - padding * 2)));
        float height = Math.min(natural.height() + padding * 2, maximumHeight);
        float x = (viewportWidth - width) / 2;
        float y = (viewportHeight - height) / 2;
        panelBounds = new Rect(x, y, width, height);
        contentBox.layout(new Rect(x + padding, y + padding, contentWidth,
                Math.max(0, height - padding * 2)));
    }

    /**
     * Paints the dialog panel and its border, and the scrim when it is enabled.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (!isOpen()) {
            return;
        }
        if (scrimEnabled) {
            renderer.rect(localBounds(), scrimColor);
        }
        if (panelBounds.isEmpty()) {
            return;
        }
        WidgetStyle style = style();
        if (style == null) {
            return;
        }
        WidgetState state = isEnabled() ? WidgetState.NORMAL : WidgetState.DISABLED;
        renderer.roundedRect(panelBounds, style.cornerRadius(), style.background(state));
        float borderWidth = style.borderWidth();
        if (borderWidth <= 0) {
            return;
        }
        float inset = borderWidth / 2;
        Rect border = new Rect(panelBounds.x() + inset, panelBounds.y() + inset,
                Math.max(0, panelBounds.width() - borderWidth),
                Math.max(0, panelBounds.height() - borderWidth));
        if (border.isEmpty()) {
            return;
        }
        renderer.strokeRoundedRect(border, Math.max(0, style.cornerRadius() - inset),
                borderWidth, Paint.solid(style.border(state)));
    }

    /**
     * Clips the dialog content to the panel content area.
     *
     * @param child direct child being drawn or hit-tested
     * @return the content rectangle, or null for children outside the content box
     */
    @Override
    protected Rect childClip(Widget child) {
        if (child != contentBox) {
            return null;
        }
        float padding = padding();
        return new Rect(panelBounds.x() + padding, panelBounds.y() + padding,
                Math.max(0, panelBounds.width() - padding * 2),
                Math.max(0, panelBounds.height() - padding * 2));
    }

    /**
     * Cancels the dialog on Escape and consumes presses outside the panel.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat whether this is a key repeat
     * @return true when the key was consumed
     */
    @Override
    protected boolean onKeyPressed(Key key, Modifiers modifiers, boolean repeat) {
        if (key == Key.ESCAPE) {
            cancel();
            return true;
        }
        return false;
    }

    /**
     * Consumes presses outside the panel and cancels the dialog when background dismissal is
     * enabled.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press was consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (!isOpen() || panelBounds.contains(x, y)) {
            return false;
        }
        if (button == MouseButton.LEFT && isCloseOnOutsideClick()) {
            cancel();
        }
        return true;
    }

    /**
     * Refreshes primary action styles after the dialog attaches.
     */
    @Override
    protected void onAttached() {
        super.onAttached();
        refreshPrimaryStyles();
    }

    /**
     * Refreshes primary action styles after the theme or context changes.
     */
    @Override
    protected void onContextChanged() {
        super.onContextChanged();
        refreshPrimaryStyles();
    }

    /**
     * Clears the panel bounds when the dialog leaves the manager.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        panelBounds = Rect.EMPTY;
    }

    /**
     * Clears transient state and returns focus to the pre-open owner when it is still available.
     * Focus that moved to another widget while the dialog closed is left untouched.
     */
    @Override
    protected void onClosed() {
        super.onClosed();
        panelBounds = Rect.EMPTY;
        restorePreviousFocus();
    }

    private void restorePreviousFocus() {
        UiContext context = ownerContext;
        Widget target = restoreFocus;
        ownerContext = null;
        restoreFocus = null;
        if (context == null || target == null) {
            return;
        }
        Widget focused = context.focused();
        if (focused != null && !isWithin(focused, this)) {
            return;
        }
        if (target.context() != context || !target.isFocusable()) {
            return;
        }
        try {
            context.requestFocus(target);
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // The previous owner was disabled, hidden or detached while the dialog was open.
        }
    }

    private static boolean isWithin(Widget widget, Widget ancestor) {
        for (Widget current = widget; current != null; current = current.parent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private void refreshPrimaryStyles() {
        for (Button button : primaryButtons) {
            applyPrimaryStyle(button);
        }
    }

    private void applyPrimaryStyle(Button button) {
        if (context() != null) {
            button.setStyle(context().theme().primaryStyle());
        }
    }

    /**
     * A header row that places an optional icon before the title.
     * Without an icon the row measures and lays out exactly like the title label, so dialogs that
     * do not use an icon keep their original layout. With an icon the title width shrinks by the
     * icon size and gap, and both children are vertically centered against each other.
     */
    private static final class TitleRow extends Widget {
        private final Image icon = new Image();
        private final Label title;
        private float iconSize = DEFAULT_ICON_SIZE;
        private Color iconTint;

        private TitleRow(Label title) {
            this.title = title;
            icon.setAutoSize(false);
            icon.setBounds(new Rect(0, 0, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE));
            icon.setVisible(false);
            add(icon).add(title);
            setVisible(false);
        }

        private ImageSource icon() {
            return icon.source();
        }

        private void setIcon(ImageSource source) {
            icon.setSource(source);
            icon.setVisible(source != null);
            refreshVisibility();
        }

        private void setIcon(Texture texture) {
            icon.setTexture(texture);
            icon.setVisible(texture != null);
            refreshVisibility();
        }

        private void clearIcon() {
            icon.clearSource();
            icon.setVisible(false);
            refreshVisibility();
        }

        private void refreshVisibility() {
            setVisible(icon.isVisible() || title.isVisible());
        }

        private float iconSize() {
            return iconSize;
        }

        private void setIconSize(float iconSize) {
            if (!Float.isFinite(iconSize) || iconSize <= 0) {
                throw new IllegalArgumentException("Icon size must be finite and positive.");
            }
            this.iconSize = iconSize;
            icon.setBounds(new Rect(0, 0, iconSize, iconSize));
        }

        private Color iconTint() {
            return iconTint;
        }

        private void setIconTint(Color iconTint) {
            this.iconTint = iconTint;
            applyTint();
        }

        /**
         * Applies the resolved icon tint when the row attaches.
         */
        @Override
        protected void onAttached() {
            applyTint();
        }

        /**
         * Reapplies the resolved icon tint after a theme or display density change.
         */
        @Override
        protected void onContextChanged() {
            applyTint();
        }

        /**
         * Measures the optional icon and the title wrapped in the remaining width.
         * Without an icon the result matches the title label measurement.
         *
         * @param constraints available and required dimensions
         * @return the header size within the constraints
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            boolean hasIcon = icon.isVisible();
            float titleWidth = hasIcon
                    ? Math.max(0, constraints.maxWidth() - iconSize - ICON_GAP)
                    : constraints.maxWidth();
            Size titleSize = title.measure(
                    LayoutConstraints.loose(titleWidth, constraints.maxHeight()));
            if (!hasIcon) {
                return titleSize;
            }
            float height = Math.max(iconSize, titleSize.height());
            return new Size(Math.min(constraints.maxWidth(), iconSize + ICON_GAP + titleSize.width()),
                    height);
        }

        /**
         * Places the icon and the title and vertically centers the shorter one.
         * The icon occupies its own column, so it never covers the wrapped title text.
         */
        @Override
        protected void onLayout() {
            float width = bounds().width();
            if (!icon.isVisible()) {
                title.layout(new Rect(0, 0, width, bounds().height()));
                return;
            }
            float titleWidth = Math.max(0, width - iconSize - ICON_GAP);
            Size titleSize = title.measure(LayoutConstraints.loose(titleWidth, bounds().height()));
            float rowHeight = Math.max(iconSize, titleSize.height());
            float iconY = (rowHeight - iconSize) / 2;
            float titleY = (rowHeight - titleSize.height()) / 2;
            icon.layout(new Rect(0, iconY, iconSize, iconSize));
            title.layout(new Rect(iconSize + ICON_GAP, titleY, titleWidth, titleSize.height()));
        }

        private void applyTint() {
            WidgetStyle style = style();
            Color tint = iconTint != null ? iconTint
                    : style == null ? Color.WHITE : style.text(WidgetState.NORMAL);
            icon.setTint(tint);
        }
    }

    /**
     * A container that presents one optional custom widget stretched over its own bounds.
     */
    private static final class BodySlot extends Widget {
        private Widget child;

        private Widget child() {
            return child;
        }

        private void setChild(Widget child) {
            if (this.child == child) {
                return;
            }
            if (child != null) {
                add(child);
            }
            Widget previous = this.child;
            this.child = child;
            if (previous != null) {
                remove(previous);
            }
        }

        /**
         * Measures the custom content with the supplied space.
         *
         * @param constraints available and required dimensions
         * @return the natural child size, or zero when no child is assigned
         */
        @Override
        protected Size onMeasure(LayoutConstraints constraints) {
            if (child == null) {
                return new Size(0, 0);
            }
            return child.measure(LayoutConstraints.loose(constraints.maxWidth(), constraints.maxHeight()));
        }

        /**
         * Stretches the custom content across the slot bounds.
         */
        @Override
        protected void onLayout() {
            if (child != null) {
                child.layout(new Rect(0, 0, bounds().width(), bounds().height()));
            }
        }
    }
}
