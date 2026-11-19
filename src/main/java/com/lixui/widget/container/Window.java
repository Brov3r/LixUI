package com.lixui.widget.container;

import com.lixui.widget.Widget;
import com.lixui.widget.IconSet;
import com.lixui.widget.UiContext;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.control.Button;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
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

import java.util.List;
import java.util.Objects;

/**
 * A movable, resizable panel with a title bar, window actions, and a scrollable body.
 * The title bar stays visible while the body is collapsed or pinned. The window body uses the
 * inherited {@link ScrollPanel} content and scrolling behavior.
 */
public class Window extends ScrollPanel {
    private static final float DEFAULT_HEADER_HEIGHT = 34;
    private static final float HEADER_PADDING = 10;
    private static final float BUTTON_SIZE = 26;
    private static final float BUTTON_GAP = 2;
    private static final float ICON_SIZE = 14;
    private static final float HEADER_ICON_SIZE = 16;
    private static final float HEADER_ICON_GAP = 7;
    private static final float TITLE_RIGHT_GAP = 10;
    private static final float RESIZE_SIZE = 18;
    private static final float COLLAPSE_SECONDS = 0.2f;
    private static final float DEFAULT_MINIMUM_WIDTH = 160;
    private static final float DEFAULT_MINIMUM_HEIGHT = 80;

    private final Header header;
    private final ResizeHandle resizeHandle;
    private final Button pinButton = new Button();
    private final Button collapseButton = new Button();
    private final Button closeButton = new Button();
    private final IconSet icons = new IconSet("pushpin.svg", "unlock.svg", "minus.svg",
            "plus.svg", "cross.svg", "cursor-diagonal-resize.svg");

    private String title = "";
    private Texture headerIcon;
    private float headerIconSize = HEADER_ICON_SIZE;
    private ImageStyle headerIconStyle = ImageStyle.ICON;
    private float headerHeight = DEFAULT_HEADER_HEIGHT;
    private float contentPaddingTop;
    private float contentPaddingRight;
    private float contentPaddingBottom;
    private float contentPaddingLeft;
    private float expandedHeight;
    private boolean movable = true;
    private boolean horizontalResizable = true;
    private boolean verticalResizable = true;
    private boolean collapsible = true;
    private boolean pinnable = true;
    private boolean closable = true;
    private boolean pinned;
    private boolean collapsed;
    private final AnimationSlot heightAnimation = new AnimationSlot();
    private Runnable onClose;

    /**
     * Creates an empty window.
     */
    public Window() {
        setMinimumSize(DEFAULT_MINIMUM_WIDTH, DEFAULT_MINIMUM_HEIGHT);
        setRaiseOnFocus(true);
        header = new Header();
        resizeHandle = new ResizeHandle();
        header.add(pinButton).add(collapseButton).add(closeButton);
        add(header).add(resizeHandle);
        pinButton.setOnAction(this::togglePinned);
        collapseButton.setOnAction(this::toggleCollapsed);
        closeButton.setOnAction(this::close);
        updateButtonVisibility();
    }

    /**
     * Returns the window title.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    /**
     * Replaces the window title.
     *
     * @param title title text
     * @throws NullPointerException if {@code title} is null
     */
    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    /**
     * Returns the header icon texture.
     *
     * @return the header icon, or null when none is assigned
     */
    public Texture headerIcon() {
        return headerIcon;
    }

    /**
     * Assigns an optional header icon. The texture is owned by the caller.
     *
     * @param headerIcon icon texture, or null to clear it
     */
    public void setHeaderIcon(Texture headerIcon) {
        this.headerIcon = headerIcon;
    }

    /**
     * Returns how the header icon is fitted into its square slot.
     *
     * @return the header icon placement style
     */
    public ImageStyle headerIconStyle() {
        return headerIconStyle;
    }

    /**
     * Assigns how the header icon is fitted into its square slot.
     * The default preserves the icon aspect ratio and centers it.
     *
     * @param headerIconStyle header icon placement style
     * @throws NullPointerException if {@code headerIconStyle} is null
     */
    public void setHeaderIconStyle(ImageStyle headerIconStyle) {
        this.headerIconStyle = Objects.requireNonNull(headerIconStyle, "headerIconStyle");
    }

    /**
     * Returns the header icon edge length.
     *
     * @return the header icon size in logical UI units
     */
    public float headerIconSize() {
        return headerIconSize;
    }

    /**
     * Sets the header icon edge length.
     *
     * @param headerIconSize positive icon size in logical UI units
     * @throws IllegalArgumentException if the size is not positive or non-finite
     */
    public void setHeaderIconSize(float headerIconSize) {
        if (!Float.isFinite(headerIconSize) || headerIconSize <= 0) {
            throw new IllegalArgumentException("Header icon size must be finite and positive.");
        }
        this.headerIconSize = headerIconSize;
    }

    /**
     * Returns the header height.
     *
     * @return the header height in logical UI units
     */
    public float headerHeight() {
        return headerHeight;
    }

    /**
     * Sets the header height and updates the content inset.
     *
     * @param headerHeight positive header height in logical UI units
     * @throws IllegalArgumentException if the height is not positive or non-finite
     */
    public void setHeaderHeight(float headerHeight) {
        if (!Float.isFinite(headerHeight) || headerHeight <= 0) {
            throw new IllegalArgumentException("Header height must be finite and positive.");
        }
        this.headerHeight = headerHeight;
        if (collapsed) {
            heightAnimation.cancel();
            setSize(bounds().width(), headerHeight);
        }
    }

    /**
     * Sets symmetric content padding inside the window body.
     *
     * @param padding non-negative padding in logical UI units
     * @throws IllegalArgumentException if the padding is negative or non-finite
     */
    public void setContentPadding(float padding) {
        setContentPadding(padding, padding, padding, padding);
    }

    /**
     * Sets content padding inside the window body.
     *
     * @param top top padding
     * @param right right padding
     * @param bottom bottom padding
     * @param left left padding
     * @throws IllegalArgumentException if a padding is negative or non-finite
     */
    public void setContentPadding(float top, float right, float bottom, float left) {
        if (!Float.isFinite(top) || !Float.isFinite(right)
                || !Float.isFinite(bottom) || !Float.isFinite(left)
                || top < 0 || right < 0 || bottom < 0 || left < 0) {
            throw new IllegalArgumentException("Content padding must be finite and non-negative.");
        }
        contentPaddingTop = top;
        contentPaddingRight = right;
        contentPaddingBottom = bottom;
        contentPaddingLeft = left;
    }

    /**
     * Returns whether the window can be moved by dragging its header.
     *
     * @return true when moving is enabled
     */
    public boolean isMovable() {
        return movable;
    }

    /**
     * Enables or disables dragging the window by its header.
     *
     * @param movable true to allow moving
     */
    public void setMovable(boolean movable) {
        this.movable = movable;
    }

    /**
     * Returns whether the window can be resized.
     *
     * @return true when resizing is enabled
     */
    public boolean isResizable() {
        return horizontalResizable || verticalResizable;
    }

    /**
     * Enables or disables resizing on both axes.
     *
     * @param resizable true to allow resizing
     */
    public void setResizable(boolean resizable) {
        horizontalResizable = resizable;
        verticalResizable = resizable;
        updateResizeHandleVisibility();
    }

    /**
     * Returns whether horizontal resizing is enabled.
     *
     * @return true when the width can be changed
     */
    public boolean isHorizontalResizable() {
        return horizontalResizable;
    }

    /**
     * Enables or disables horizontal resizing.
     *
     * @param resizable true to allow width changes
     */
    public void setHorizontalResizable(boolean resizable) {
        horizontalResizable = resizable;
        updateResizeHandleVisibility();
    }

    /**
     * Returns whether vertical resizing is enabled.
     *
     * @return true when the height can be changed
     */
    public boolean isVerticalResizable() {
        return verticalResizable;
    }

    /**
     * Enables or disables vertical resizing.
     *
     * @param resizable true to allow height changes
     */
    public void setVerticalResizable(boolean resizable) {
        verticalResizable = resizable;
        updateResizeHandleVisibility();
    }

    /**
     * Returns whether the window can be collapsed.
     *
     * @return true when collapsing is enabled
     */
    public boolean isCollapsible() {
        return collapsible;
    }

    /**
     * Enables or disables collapsing and hides the collapse button when disabled.
     *
     * @param collapsible true to allow collapsing
     */
    public void setCollapsible(boolean collapsible) {
        this.collapsible = collapsible;
        updateButtonVisibility();
    }

    /**
     * Returns whether the window can be pinned.
     *
     * @return true when pinning is enabled
     */
    public boolean isPinnable() {
        return pinnable;
    }

    /**
     * Enables or disables pinning and hides the pin button when disabled.
     *
     * @param pinnable true to allow pinning
     */
    public void setPinnable(boolean pinnable) {
        this.pinnable = pinnable;
        if (!pinnable && pinned) {
            setPinned(false);
        }
        updateButtonVisibility();
    }

    /**
     * Returns whether the window can be closed.
     *
     * @return true when closing is enabled
     */
    public boolean isClosable() {
        return closable;
    }

    /**
     * Enables or disables closing and hides the close button when disabled.
     *
     * @param closable true to allow closing
     */
    public void setClosable(boolean closable) {
        this.closable = closable;
        updateButtonVisibility();
    }

    /**
     * Returns whether the window is currently pinned.
     *
     * @return true when pinned
     */
    public boolean isPinned() {
        return pinned;
    }

    /**
     * Pins or unpins the window. Pinning collapses the body to the header.
     *
     * @param pinned true to pin and collapse
     */
    public void setPinned(boolean pinned) {
        if (this.pinned == pinned) {
            return;
        }
        this.pinned = pinned;
        updateButtonIcons();
        updateButtonVisibility();
        if (pinned) {
            updatePinnedHover();
        } else {
            collapseTo(false);
        }
    }

    /**
     * Returns whether the window body is collapsed.
     *
     * @return true when collapsed
     */
    public boolean isCollapsed() {
        return collapsed;
    }

    /**
     * Collapses or expands the window body with a smooth animation.
     *
     * @param collapsed true to collapse the body
     */
    public void setCollapsed(boolean collapsed) {
        if (pinned && !collapsed) {
            return;
        }
        collapseTo(collapsed);
    }

    /**
     * Returns the close callback.
     *
     * @return the callback, or null when none is assigned
     */
    public Runnable onClose() {
        return onClose;
    }

    /**
     * Assigns a callback invoked after the window is hidden.
     *
     * @param onClose close callback, or null to clear it
     */
    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    /**
     * Hides the window and invokes the close callback.
     */
    public void close() {
        setVisible(false);
        if (onClose != null) {
            onClose.run();
        }
    }

    /**
     * Replaces the scrollable body content and keeps the fixed header above it.
     *
     * @param content body content
     * @throws NullPointerException if {@code content} is null
     */
    @Override
    public void setContent(Widget content) {
        super.setContent(content);
        remove(header);
        remove(resizeHandle);
        add(header);
        add(resizeHandle);
    }

    /**
     * Loads built-in action icons when the window is attached.
     */
    @Override
    protected void onAttached() {
        loadIcons();
        updateButtonStyles();
        updateButtonVisibility();
    }

    /**
     * Releases built-in icons when the window leaves its manager.
     */
    @Override
    protected void onDetached() {
        super.onDetached();
        boolean settlingHeight = heightAnimation.isActive();
        heightAnimation.cancel();
        if (settlingHeight) {
            setSize(bounds().width(), collapsed ? headerHeight : Math.max(expandedHeight, headerHeight));
        }
        try {
            icons.release(context());
        } finally {
            updateButtonIcons();
        }
    }

    /**
     * Refreshes icon button styles after a theme change.
     */
    @Override
    protected void onContextChanged() {
        loadIcons();
        updateButtonStyles();
    }

    /**
     * Re-evaluates the pinned hover state every frame.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        if (pinned) {
            updatePinnedHover();
        }
    }

    /**
     * Measures the content with the header and padding already applied, so an auto-sized window
     * wraps its body and reports the header height while collapsed.
     *
     * @param constraints available and required dimensions
     * @return the natural window size
     */
    @Override
    protected Size onMeasure(LayoutConstraints constraints) {
        updateContentInsets();
        Size measured = super.onMeasure(constraints);
        return collapsed ? new Size(measured.width(), headerHeight) : measured;
    }

    /**
     * Lays out the header, body and resize handle.
     */
    @Override
    protected void onLayout() {
        if (!heightAnimation.isActive() && !collapsed && bounds().height() > headerHeight) {
            expandedHeight = bounds().height();
        }
        updateContentInsets();
        setScrollBarsSuppressed(collapsed);
        super.onLayout();
        header.setBounds(new Rect(0, 0, bounds().width(), headerHeight));
        header.layout(header.bounds());
        resizeHandle.setBounds(new Rect(bounds().width() - RESIZE_SIZE, bounds().height() - RESIZE_SIZE,
                RESIZE_SIZE, RESIZE_SIZE));
        resizeHandle.layout(resizeHandle.bounds());
        setVisibleIfChanged(resizeHandle, isResizable() && !collapsed);
        updateButtonVisibility();
    }

    /**
     * Prevents wheel scrolling while the body is collapsed.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param deltaX horizontal scroll amount
     * @param deltaY vertical scroll amount
     * @return true when the wheel was consumed
     */
    @Override
    protected boolean onPointerScroll(float x, float y, float deltaX, float deltaY) {
        return !collapsed && super.onPointerScroll(x, y, deltaX, deltaY);
    }

    private void updateContentInsets() {
        setContentInsets(headerHeight + contentPaddingTop, contentPaddingRight,
                contentPaddingBottom, contentPaddingLeft);
        setScrollBarInsets(0, RESIZE_SIZE, RESIZE_SIZE, 0);
    }

    private void updatePinnedHover() {
        boolean inside = isPointerInside();
        if (inside && collapsed) {
            collapseTo(false);
        } else if (!inside && !collapsed) {
            collapseTo(true);
        }
    }

    private boolean isPointerInside() {
        if (context() == null) {
            return false;
        }
        for (Widget widget = context().hovered(); widget != null; widget = widget.parent()) {
            if (widget == this) {
                return true;
            }
        }
        return false;
    }

    private void togglePinned() {
        if (pinnable) {
            setPinned(!pinned);
        }
    }

    private void toggleCollapsed() {
        if (!pinned && collapsible) {
            setCollapsed(!collapsed);
        }
    }

    private void collapseTo(boolean value) {
        if (collapsed == value) {
            return;
        }
        if (value && !collapsed && !heightAnimation.isActive()) {
            expandedHeight = Math.max(headerHeight, bounds().height());
        }
        heightAnimation.cancel();
        collapsed = value;
        setScrollBarsSuppressed(value);
        setVisibleIfChanged(resizeHandle, isResizable() && !value);
        updateButtonIcons();
        updateButtonVisibility();
        float target = value ? headerHeight : Math.max(expandedHeight, headerHeight);
        float start = bounds().height();
        if (!Float.isFinite(start) || Math.abs(target - start) < 0.001f || context() == null) {
            setSize(bounds().width(), target);
            return;
        }
        Animator animator = context().animator();
        heightAnimation.start(animator, start, target, COLLAPSE_SECONDS, Easing.EASE_IN_OUT,
                animationHeight -> setSize(bounds().width(), animationHeight), null);
    }

    private void loadIcons() {
        UiContext context = context();
        if (context == null) {
            return;
        }
        int size = Math.max(1, Math.round(ICON_SIZE * Math.max(context.scaleX(), context.scaleY())));
        if (icons.matches(context, size)) {
            return;
        }
        try {
            icons.reload(context, size);
        } finally {
            updateButtonIcons();
        }
    }

    private void updateButtonIcons() {
        pinButton.setIcon(pinned ? icons.get(0) : icons.get(1));
        collapseButton.setIcon(collapsed ? icons.get(3) : icons.get(2));
        closeButton.setIcon(icons.get(4));
    }

    private void updateButtonVisibility() {
        setVisibleIfChanged(pinButton, pinnable);
        setVisibleIfChanged(collapseButton, collapsible);
        setVisibleIfChanged(closeButton, closable);
        collapseButton.setEnabled(!pinned);
    }

    private void updateResizeHandleVisibility() {
        setVisibleIfChanged(resizeHandle, isResizable() && !collapsed);
    }

    private void updateButtonStyles() {
        if (context() == null) {
            return;
        }
        Theme theme = context().theme();
        WidgetStyle style = new WidgetStyle(Color.TRANSPARENT, Color.TRANSPARENT,
                Color.TRANSPARENT, Color.TRANSPARENT, theme.textMuted(),
                theme.textMuted().withAlpha(0.4f), Color.TRANSPARENT, Color.TRANSPARENT,
                6, 3, 0);
        for (Button button : List.of(pinButton, collapseButton, closeButton)) {
            button.setStyle(style);
            button.setIconSize(ICON_SIZE);
            button.setFocusOutlineEnabled(false);
        }
        tintButton(pinButton, theme.primary());
        tintButton(collapseButton, theme.focusRing());
        tintButton(closeButton, Color.hex("#E9B4B4"));
        updateButtonIcons();
    }

    private static void tintButton(Button button, Color color) {
        button.setIconTint(color.withAlpha(0.75f), color, color, color.withAlpha(0.3f));
    }

    private final class Header extends Widget {
        private float pressX;
        private float pressY;

        private Header() {
            setClipToBounds(true);
        }

        /**
         * Places visible action buttons together against the right edge.
         */
        @Override
        protected void onLayout() {
            float x = bounds().width() - HEADER_PADDING - BUTTON_SIZE;
            float y = (bounds().height() - BUTTON_SIZE) / 2;
            for (Button button : List.of(closeButton, collapseButton, pinButton)) {
                if (button.isVisible()) {
                    layoutButton(button, x, y);
                    x -= BUTTON_SIZE + BUTTON_GAP;
                }
            }
        }

        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Theme theme = context().theme();
            WidgetStyle style = style();
            Color background = style == null ? theme.surface() : style.background(WidgetState.NORMAL);
            Color foreground = style == null ? theme.text() : style.text(WidgetState.NORMAL);
            renderer.rect(localBounds(), background);
            renderer.rect(new Rect(0, bounds().height() - 1, bounds().width(), 1), theme.border());
            float x = HEADER_PADDING;
            if (headerIcon != null) {
                float y = (bounds().height() - headerIconSize) / 2;
                renderer.icon(headerIcon, new Rect(x, y, headerIconSize, headerIconSize),
                        headerIconStyle, theme.text());
                x += headerIconSize + HEADER_ICON_GAP;
            }
            Font font = font();
            float titleRight = buttonsLeft() - (hasActionButtons() ? TITLE_RIGHT_GAP : 0);
            float titleWidth = (float) Math.floor(titleRight - x);
            if (font != null && !title.isEmpty() && titleWidth > 0) {
                TextStyle textStyle = new TextStyle(titleWidth, 0, 1, TextOverflow.ELLIPSIS,
                        HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
                renderer.text(font, title, x, bounds().height() / 2, foreground, textStyle);
            }
        }

        private boolean hasActionButtons() {
            return pinButton.isVisible() || collapseButton.isVisible() || closeButton.isVisible();
        }

        private float buttonsLeft() {
            float x = bounds().width() - HEADER_PADDING;
            for (Button button : List.of(closeButton, collapseButton, pinButton)) {
                if (button.isVisible()) {
                    x = Math.min(x, button.bounds().x());
                }
            }
            return x;
        }

        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT || !movable || hitTest(x, y) != this) {
                return false;
            }
            pressX = x;
            pressY = y;
            capturePointer(button);
            return true;
        }

        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT || !movable) {
                return false;
            }
            Window.this.setPosition(Window.this.bounds().x() + x - pressX,
                    Window.this.bounds().y() + y - pressY);
            return true;
        }

        private void layoutButton(Button button, float x, float y) {
            button.setBounds(new Rect(x, y, BUTTON_SIZE, BUTTON_SIZE));
            button.layout(button.bounds());
        }
    }

    private final class ResizeHandle extends Widget {
        private float pressX;
        private float pressY;

        @Override
        public Cursor cursor() {
            if (horizontalResizable && verticalResizable) {
                return Cursor.RESIZE_DIAGONAL;
            }
            if (horizontalResizable) {
                return Cursor.RESIZE_HORIZONTAL;
            }
            if (verticalResizable) {
                return Cursor.RESIZE_VERTICAL;
            }
            return Cursor.DEFAULT;
        }

        @Override
        protected void onDraw(RenderManager renderer, FrameInfo frame) {
            Texture resizeIcon = icons.get(5);
            if (resizeIcon == null) {
                return;
            }
            Theme theme = context().theme();
            Color tint = !isEnabled() ? theme.textMuted().withAlpha(0.3f)
                    : isHovered() || isPressed() ? theme.text() : theme.textMuted().withAlpha(0.6f);
            float inset = 2;
            renderer.icon(resizeIcon, new Rect(inset, inset,
                    bounds().width() - inset * 2, bounds().height() - inset * 2),
                    ImageStyle.FILL, tint);
        }

        @Override
        protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT || !isResizable() || collapsed) {
                return false;
            }
            pressX = x;
            pressY = y;
            capturePointer(button);
            return true;
        }

        @Override
        protected boolean onDrag(float x, float y, MouseButton button, Modifiers modifiers) {
            if (button != MouseButton.LEFT || !isResizable() || collapsed) {
                return false;
            }
            float width = Window.this.bounds().width();
            float height = Window.this.bounds().height();
            if (horizontalResizable) {
                width = Math.clamp(width + x - pressX, Window.this.minimumWidth(),
                        Window.this.maximumWidth());
            }
            if (verticalResizable) {
                height = Math.clamp(height + y - pressY, Window.this.minimumHeight(),
                        Window.this.maximumHeight());
            }
            Window.this.setSize(width, height);
            expandedHeight = height;
            return true;
        }
    }
}
