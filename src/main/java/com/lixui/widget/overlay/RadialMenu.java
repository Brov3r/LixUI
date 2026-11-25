package com.lixui.widget.overlay;

import com.lixui.animation.Animation;
import com.lixui.animation.Animator;
import com.lixui.animation.Easing;
import com.lixui.geometry.Point;
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
import com.lixui.widget.FrameInfo;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.tooltip.TextTooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A viewport-level radial menu that opens at a point and divides a ring into clickable sectors.
 * Every sector shows an optional icon and a label; the inner circle shows a title and acts as a
 * close button. Opening scales the ring up, closing scales it down, Escape dismisses the menu, and
 * a press outside the ring or center closes it. The menu requests keyboard focus while open and
 * restores the previous focus when it closes normally. Hovering a sector shows a tooltip with the
 * full label or the item tooltip text. The center and the whole ring are clamped to the viewport,
 * including the label extents.
 */
public class RadialMenu extends Widget {
    /**
     * One sector of the radial menu.
     */
    public static final class Item {
        private String label;
        private Texture icon;
        private Runnable action;
        private boolean enabled = true;
        private String tooltip;
        private PlacementKey placementKey;
        private Placement placement;

        /**
         * Creates an enabled item without an icon or action.
         *
         * @param label item label
         * @throws NullPointerException if {@code label} is null
         */
        public Item(String label) {
            this(label, null, null);
        }

        /**
         * Creates an enabled item without an icon.
         *
         * @param label item label
         * @param action action run when the item is clicked, or null for no action
         * @throws NullPointerException if {@code label} is null
         */
        public Item(String label, Runnable action) {
            this(label, null, action);
        }

        /**
         * Creates an enabled item.
         *
         * @param label item label
         * @param icon item icon, or null for no icon
         * @param action action run when the item is clicked, or null for no action
         * @throws NullPointerException if {@code label} is null
         */
        public Item(String label, Texture icon, Runnable action) {
            this.label = Objects.requireNonNull(label, "label");
            this.icon = icon;
            this.action = action;
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
         * Replaces the item label.
         *
         * @param label new label
         * @throws NullPointerException if {@code label} is null
         */
        public void setLabel(String label) {
            this.label = Objects.requireNonNull(label, "label");
        }

        /**
         * Returns the item icon.
         *
         * @return the icon, or null when no icon is shown
         */
        public Texture icon() {
            return icon;
        }

        /**
         * Replaces the item icon.
         *
         * @param icon new icon, or null to clear it
         */
        public void setIcon(Texture icon) {
            this.icon = icon;
        }

        /**
         * Returns the click action.
         *
         * @return the action, or null when the item only closes the menu
         */
        public Runnable action() {
            return action;
        }

        /**
         * Replaces the click action.
         *
         * @param action new action, or null to clear it
         */
        public void setAction(Runnable action) {
            this.action = action;
        }

        /**
         * Reports whether the item accepts clicks.
         *
         * @return true when the item is enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the item.
         *
         * @param enabled true to accept clicks
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the tooltip text shown while the sector is hovered.
         *
         * @return the custom tooltip text, or null when the full label is used
         */
        public String tooltip() {
            return tooltip;
        }

        /**
         * Replaces the tooltip text shown while the sector is hovered.
         * Null or empty text falls back to the full label, which stays available even when the
         * drawn label is truncated.
         *
         * @param tooltip custom tooltip text, or null to use the full label
         */
        public void setTooltip(String tooltip) {
            this.tooltip = tooltip == null || tooltip.isEmpty() ? null : tooltip;
        }

        private String tooltipText() {
            return tooltip == null ? label : tooltip;
        }
    }

    private static final float DEFAULT_RADIUS = 140;
    private static final float DEFAULT_INNER_RADIUS_RATIO = 0.4f;
    private static final float DEFAULT_ICON_SIZE = 20;
    private static final float DEFAULT_ANIMATION_SECONDS = 0.22f;
    private static final float LABEL_OUTER_MARGIN = 4;
    private static final float LABEL_INNER_MARGIN = 2;
    private static final float LABEL_GLYPH_PADDING = 2;
    private static final float LABEL_ANGLE_MARGIN = 0.02f;
    private static final float LABEL_VISIBLE_AMOUNT = 0.995f;
    private static final float ICON_INNER_MARGIN = 2;
    private static final float ICON_OUTER_MARGIN = 2;
    private static final float ICON_LABEL_GAP = 4;
    private static final float ICON_RADIAL_RATIO = 0.3f;
    private static final float MIN_ICON_EDGE = 4;
    private static final int LABEL_RADIUS_CANDIDATES = 5;
    private static final int LABEL_WIDTH_STEPS = 10;
    private static final int ICON_EDGE_STEPS = 8;
    private static final int FULL_TEXT_MEASURE_LIMIT = 256;
    private static final float VIEWPORT_MARGIN = 6;
    private static final float SECTOR_GAP = 0.02f;
    private static final float HOVER_LIFT = 4;
    private static final float CENTER_CIRCLE_RATIO = 0.82f;
    private static final float FULL_TURN = (float) (Math.PI * 2);
    private static final float START_OFFSET = (float) (-Math.PI / 2);
    private static final float ANIMATION_SNAP = 0.001f;

    private final List<Item> items = new ArrayList<>();
    private final TextTooltip sectorTooltip = new TextTooltip();

    private float radius = DEFAULT_RADIUS;
    private float innerRadiusRatio = DEFAULT_INNER_RADIUS_RATIO;
    private float iconSize = DEFAULT_ICON_SIZE;
    private float animationDuration = DEFAULT_ANIMATION_SECONDS;
    private Easing animationEasing = Easing.EASE_OUT;
    private String title = "";
    private boolean closeOnOutsideClick = true;
    private Runnable closeListener;

    private boolean open;
    private boolean closing;
    private boolean closePending;
    private boolean shouldRestoreFocus = true;
    private float requestedX;
    private float requestedY;
    private float centerX;
    private float centerY;
    private float effectiveRadius;
    private float openAmount;
    private int hoveredIndex = -1;
    private boolean centerHovered;
    private Widget previousFocus;
    private Animator animationOwner;
    private Animation animation;

    /**
     * Creates an empty radial menu.
     */
    public RadialMenu() {
        setFillViewport(true);
        setAlwaysOnTop(true);
        setClipToBounds(true);
        setFocusable(true);
        setFocusOutlineEnabled(false);
    }

    /**
     * Returns the sectors in display order.
     *
     * @return an immutable snapshot of the items
     */
    public final List<Item> items() {
        return List.copyOf(items);
    }

    /**
     * Appends a labeled item without an icon.
     *
     * @param label item label
     * @param action action run when the item is clicked, or null for no action
     * @return the added item
     * @throws NullPointerException if {@code label} is null
     */
    public final Item addItem(String label, Runnable action) {
        Item item = new Item(label, action);
        items.add(item);
        return item;
    }

    /**
     * Appends an item.
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
     * Removes an item and closes the menu when no items remain.
     *
     * @param item item to remove
     * @return true when the item belonged to this menu
     * @throws NullPointerException if {@code item} is null
     */
    public final boolean removeItem(Item item) {
        Objects.requireNonNull(item, "item");
        if (!items.remove(item)) {
            return false;
        }
        hoveredIndex = -1;
        clearSectorTooltip();
        if (items.isEmpty()) {
            close();
        }
        return true;
    }

    /**
     * Removes every item and closes the menu.
     */
    public final void clearItems() {
        items.clear();
        hoveredIndex = -1;
        clearSectorTooltip();
        close();
    }

    /**
     * Reports whether the menu is visible, including the closing animation.
     *
     * @return true while the menu is open or closing
     */
    public final boolean isOpen() {
        return open;
    }

    /**
     * Reports whether the menu is playing its closing animation.
     *
     * @return true while the menu is closing
     */
    public final boolean isClosing() {
        return closing;
    }

    /**
     * Returns the animated open scale.
     *
     * @return the scale in {@code [0, 1]}
     */
    public final float openAmount() {
        return openAmount;
    }

    /**
     * Returns the hidden sector index.
     *
     * @return the hovered item index, or -1 when no sector is hovered
     */
    public final int hoveredIndex() {
        return hoveredIndex;
    }

    /**
     * Returns the effective menu circle in viewport coordinates.
     *
     * @return the ring bounds, or an empty rectangle while closed
     */
    public final Rect menuBounds() {
        if (!open || effectiveRadius <= 0) {
            return Rect.EMPTY;
        }
        return new Rect(centerX - effectiveRadius, centerY - effectiveRadius,
                effectiveRadius * 2, effectiveRadius * 2);
    }

    /**
     * Returns the preferred outer radius.
     *
     * @return the radius in logical UI units
     */
    public final float radius() {
        return radius;
    }

    /**
     * Sets the preferred outer radius.
     * The drawn radius shrinks when the viewport cannot fit the ring and its labels.
     *
     * @param radius positive radius in logical UI units
     * @throws IllegalArgumentException if the radius is not positive or is non-finite
     */
    public final void setRadius(float radius) {
        if (!Float.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException("Radial menu radius must be finite and positive.");
        }
        this.radius = radius;
    }

    /**
     * Returns the inner radius as a fraction of the outer radius.
     *
     * @return the ratio in {@code [0, 1)}
     */
    public final float innerRadiusRatio() {
        return innerRadiusRatio;
    }

    /**
     * Sets the inner radius as a fraction of the outer radius.
     *
     * @param innerRadiusRatio ratio in {@code [0, 1)}
     * @throws IllegalArgumentException if the ratio is outside the range or non-finite
     */
    public final void setInnerRadiusRatio(float innerRadiusRatio) {
        if (!Float.isFinite(innerRadiusRatio) || innerRadiusRatio < 0 || innerRadiusRatio >= 1) {
            throw new IllegalArgumentException("Inner radius ratio must be finite and in [0, 1).");
        }
        this.innerRadiusRatio = innerRadiusRatio;
    }

    /**
     * Returns the preferred icon edge length.
     *
     * @return the icon edge in logical UI units
     */
    public final float iconSize() {
        return iconSize;
    }

    /**
     * Sets the preferred icon edge length.
     *
     * @param iconSize positive edge length in logical UI units
     * @throws IllegalArgumentException if the size is not positive or is non-finite
     */
    public final void setIconSize(float iconSize) {
        if (!Float.isFinite(iconSize) || iconSize <= 0) {
            throw new IllegalArgumentException("Icon size must be finite and positive.");
        }
        this.iconSize = iconSize;
    }

    /**
     * Returns the center title.
     *
     * @return the title, never null
     */
    public final String title() {
        return title;
    }

    /**
     * Sets the title shown in the center circle.
     *
     * @param title center title, or an empty string to draw a close cross
     * @throws NullPointerException if {@code title} is null
     */
    public final void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
    }

    /**
     * Returns the open and close animation duration.
     *
     * @return the duration in seconds, or zero for instant changes
     */
    public final float animationDuration() {
        return animationDuration;
    }

    /**
     * Sets the open and close animation duration.
     *
     * @param animationDuration non-negative duration in seconds
     * @throws IllegalArgumentException if the duration is negative or non-finite
     */
    public final void setAnimationDuration(float animationDuration) {
        if (!Float.isFinite(animationDuration) || animationDuration < 0) {
            throw new IllegalArgumentException("Animation duration must be finite and non-negative.");
        }
        this.animationDuration = animationDuration;
    }

    /**
     * Returns the open and close animation timing curve.
     *
     * @return the timing curve
     */
    public final Easing animationEasing() {
        return animationEasing;
    }

    /**
     * Sets the open and close animation timing curve.
     *
     * @param animationEasing timing curve
     * @throws NullPointerException if {@code animationEasing} is null
     */
    public final void setAnimationEasing(Easing animationEasing) {
        this.animationEasing = Objects.requireNonNull(animationEasing, "animationEasing");
    }

    /**
     * Reports whether a press outside the ring closes the menu.
     *
     * @return true when outside presses dismiss the menu
     */
    public final boolean isCloseOnOutsideClick() {
        return closeOnOutsideClick;
    }

    /**
     * Enables or disables closing the menu on an outside press.
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
     * Opens the menu at a viewport point.
     * The center is clamped so the ring and its labels stay inside the viewport.
     *
     * @param context context that owns the widget manager
     * @param x requested center X in viewport coordinates
     * @param y requested center Y in viewport coordinates
     * @throws NullPointerException if {@code context} is null
     * @throws IllegalArgumentException if a coordinate is non-finite
     * @throws IllegalStateException if the context has no manager
     */
    public final void open(UiContext context, float x, float y) {
        Objects.requireNonNull(context, "context");
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Radial menu position must be finite.");
        }
        if (items.isEmpty()) {
            return;
        }
        if (open) {
            if (closing) {
                finishClose();
            } else {
                requestedX = x;
                requestedY = y;
                return;
            }
        }
        requestedX = x;
        requestedY = y;
        open = true;
        closing = false;
        closePending = false;
        shouldRestoreFocus = true;
        openAmount = 0;
        hoveredIndex = -1;
        centerHovered = false;
        clearSectorTooltip();
        previousFocus = context.focused();
        try {
            context.addTopLevel(this);
            context.requestFocus(this);
        } catch (RuntimeException | Error exception) {
            open = false;
            previousFocus = null;
            try {
                context.removeTopLevel(this);
            } catch (RuntimeException ignored) {
                // The failed open may already have detached the menu.
            }
            throw exception;
        }
    }

    /**
     * Starts closing the menu.
     * The menu finishes the scale animation before it is removed from the manager.
     */
    public final void close() {
        if (!open || closing) {
            return;
        }
        closing = true;
        clearSectorTooltip();
        startCloseAnimation();
    }

    /**
     * Positions the menu center and shrinks the radius so the whole ring stays in the viewport.
     * Labels are fitted inside their sectors and never increase the ring radius.
     */
    @Override
    protected void onLayout() {
        if (!open) {
            effectiveRadius = 0;
            return;
        }
        float viewportWidth = localBounds().width();
        float viewportHeight = localBounds().height();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            effectiveRadius = 0;
            return;
        }
        float maxRadius = Math.min(viewportWidth, viewportHeight) / 2 - VIEWPORT_MARGIN;
        effectiveRadius = Math.max(0, Math.min(radius, maxRadius));
        float extent = effectiveRadius + VIEWPORT_MARGIN;
        centerX = extent * 2 >= viewportWidth
                ? viewportWidth / 2 : Math.clamp(requestedX, extent, viewportWidth - extent);
        centerY = extent * 2 >= viewportHeight
                ? viewportHeight / 2 : Math.clamp(requestedY, extent, viewportHeight - extent);
        refreshPlacements();
    }

    /**
     * Starts the open animation when the menu attaches.
     */
    @Override
    protected void onAttached() {
        if (open) {
            openAmount = 0;
            startOpenAnimation();
        }
    }

    /**
     * Cancels animations and restores focus when the menu is removed externally.
     */
    @Override
    protected void onDetached() {
        cancelAnimation();
        clearSectorTooltip();
        if (open) {
            open = false;
            closing = false;
            closePending = false;
            openAmount = 0;
            UiContext owner = context();
            Widget focused = owner == null ? null : owner.focused();
            Widget restore = shouldRestoreFocus
                    && (focused == null || focused == this) ? previousFocus : null;
            previousFocus = null;
            shouldRestoreFocus = true;
            applyFocusRestore(owner, restore);
            notifyClosed();
        } else {
            previousFocus = null;
            openAmount = 0;
        }
    }

    /**
     * Finishes a deferred close after the closing animation reaches zero.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        if (closePending) {
            finishClose();
        }
        syncTooltip();
    }

    /**
     * Paints the ring sectors, icons, labels and the center circle.
     *
     * @param renderer drawing manager for the current frame
     * @param frame timing information for the current frame
     */
    @Override
    protected void onDraw(RenderManager renderer, FrameInfo frame) {
        if (context() == null || !open) {
            return;
        }
        float amount = Math.clamp(openAmount, 0f, 1f);
        if (amount <= ANIMATION_SNAP || effectiveRadius <= 0) {
            return;
        }
        Theme theme = context().theme();
        Font font = font();
        renderer.withOpacity(amount, () -> drawMenu(renderer, theme, font, amount));
    }

    /**
     * Tracks the sector and center under the pointer.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @return false so the event can still bubble
     */
    @Override
    protected boolean onPointerMove(float x, float y) {
        if (!open) {
            return false;
        }
        hoveredIndex = itemAt(x, y);
        centerHovered = centerAt(x, y);
        syncTooltip();
        return false;
    }

    /**
     * Clears hover feedback when the pointer leaves the overlay.
     */
    @Override
    protected void onPointerExit() {
        hoveredIndex = -1;
        centerHovered = false;
        syncTooltip();
    }

    /**
     * Closes the menu on an outside press and otherwise remembers the pressed sector.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button pressed button
     * @param modifiers active modifiers
     * @return true when the press is consumed
     */
    @Override
    protected boolean onPointerPress(float x, float y, MouseButton button, Modifiers modifiers) {
        if (!open || button != MouseButton.LEFT) {
            return false;
        }
        if (itemAt(x, y) >= 0 || centerAt(x, y)) {
            return true;
        }
        if (closeOnOutsideClick) {
            close();
        }
        return true;
    }

    /**
     * Consumes the release that belongs to the radial menu.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button released button
     * @param modifiers active modifiers
     * @return true while the menu is open
     */
    @Override
    protected boolean onPointerRelease(float x, float y, MouseButton button, Modifiers modifiers) {
        return open;
    }

    /**
     * Runs the clicked sector action or closes the menu from its center.
     *
     * @param x horizontal pointer position relative to the widget
     * @param y vertical pointer position relative to the widget
     * @param button clicked button
     * @param modifiers active modifiers
     * @return true when the click belongs to the menu
     */
    @Override
    protected boolean onClick(float x, float y, MouseButton button, Modifiers modifiers) {
        if (!open || button != MouseButton.LEFT) {
            return false;
        }
        if (centerAt(x, y)) {
            close();
            return true;
        }
        int index = itemAt(x, y);
        if (index < 0) {
            return true;
        }
        Item item = items.get(index);
        if (!item.enabled) {
            return true;
        }
        close();
        if (item.action != null) {
            item.action.run();
        }
        return true;
    }

    /**
     * Closes the menu on Escape.
     *
     * @param key pressed key
     * @param modifiers active modifiers
     * @param repeat true for a repeated key press
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
     * Closes the menu when focus moves to another widget and keeps the previous focus otherwise.
     *
     * @param focused true when this menu gained focus
     */
    @Override
    protected void onFocusChanged(boolean focused) {
        if (!focused && open) {
            shouldRestoreFocus = false;
            if (!closing) {
                close();
            }
        }
    }

    /**
     * Requests the pointer cursor over selectable sectors and the center button.
     *
     * @return the pointer cursor over a clickable target, or the default cursor
     */
    @Override
    public Cursor cursor() {
        if (!open) {
            return Cursor.DEFAULT;
        }
        if (centerHovered) {
            return Cursor.POINTER;
        }
        return hoveredIndex >= 0 && hoveredIndex < items.size() && items.get(hoveredIndex).enabled
                ? Cursor.POINTER : Cursor.DEFAULT;
    }

    private void syncTooltip() {
        if (tooltip() != null && tooltip() != sectorTooltip) {
            return;
        }
        String text = hoveredTooltipText();
        if (text == null) {
            clearSectorTooltip();
            return;
        }
        if (tooltip() == null) {
            setTooltip(sectorTooltip);
        }
        sectorTooltip.setText(text);
    }

    private String hoveredTooltipText() {
        if (!open || closing || hoveredIndex < 0 || hoveredIndex >= items.size()) {
            return null;
        }
        String text = items.get(hoveredIndex).tooltipText();
        return text.isEmpty() ? null : text;
    }

    private void clearSectorTooltip() {
        if (tooltip() == sectorTooltip) {
            setTooltip(null);
        }
    }

    private void drawMenu(RenderManager renderer, Theme theme, Font font, float amount) {
        if (items.isEmpty()) {
            return;
        }
        float outer = effectiveRadius * amount;
        float inner = outer * innerRadiusRatio;
        float sweep = FULL_TURN / items.size();
        float gap = items.size() > 1 ? Math.min(SECTOR_GAP, sweep * 0.2f) : 0;
        boolean labelsVisible = !closing && amount >= LABEL_VISIBLE_AMOUNT;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < items.size(); index++) {
                boolean hovered = index == hoveredIndex;
                if (pass == 0 && hovered) {
                    continue;
                }
                if (pass == 1 && !hovered) {
                    continue;
                }
                drawItem(renderer, theme, font, amount, index, hovered, inner, outer,
                        sweep, gap, labelsVisible);
            }
        }
        float centerRadius = inner * CENTER_CIRCLE_RATIO;
        if (centerRadius > 0) {
            drawCenter(renderer, theme, font, centerRadius);
        }
    }

    private void drawItem(RenderManager renderer, Theme theme, Font font, float amount,
                          int index, boolean hovered, float inner, float outer, float sweep,
                          float gap, boolean labelsVisible) {
        Item item = items.get(index);
        float start = sectorStart(index, sweep) + gap / 2;
        float itemSweep = sweep - gap;
        float itemOuter = hovered ? outer + HOVER_LIFT : outer;
        Color fill = item.enabled ? theme.surface() : theme.background().withAlpha(0.6f);
        if (hovered && item.enabled) {
            fill = Color.mix(fill, theme.primary(), 0.32f);
        }
        drawRingSector(renderer, new Point(centerX, centerY), inner, itemOuter, start,
                itemSweep, Paint.solid(fill));
        Placement placement = item.placement;
        if (placement == null) {
            return;
        }
        float angle = sectorStart(index, sweep) + sweep / 2;
        if (item.icon != null && placement.iconVisible) {
            float edge = placement.iconEdge * amount;
            float radius = placement.iconRadius * amount;
            if (edge > 0.5f) {
                Color tint = item.enabled ? theme.text() : theme.textMuted().withAlpha(0.45f);
                renderer.icon(item.icon, new Rect(centerX + (float) Math.cos(angle) * radius
                                - edge / 2, centerY + (float) Math.sin(angle) * radius - edge / 2,
                        edge, edge), ImageStyle.ICON, tint);
            }
        }
        if (labelsVisible && font != null && placement.labelText != null) {
            float x = centerX + (float) Math.cos(angle) * placement.labelRadius;
            float y = centerY + (float) Math.sin(angle) * placement.labelRadius;
            Color color = item.enabled ? theme.text() : theme.textMuted().withAlpha(0.45f);
            renderer.text(font, placement.labelText, x, y, color,
                    HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        }
    }

    private void drawCenter(RenderManager renderer, Theme theme, Font font, float centerRadius) {
        Point center = new Point(centerX, centerY);
        Color fill = centerHovered ? Color.mix(theme.surface(), theme.primary(), 0.25f)
                : theme.surface();
        renderer.fillCircle(center, centerRadius, Paint.solid(fill));
        renderer.strokeCircle(center, centerRadius, 1.5f,
                Paint.solid(centerHovered ? theme.primary() : theme.border()));
        if (!title.isEmpty() && font != null) {
            float maxWidth = Math.max(0, centerRadius * 1.6f);
            if (maxWidth > 0) {
                TextStyle style = new TextStyle(maxWidth, 0, 2, TextOverflow.ELLIPSIS,
                        HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                renderer.text(font, title, centerX, centerY, theme.text(), style);
            }
            return;
        }
        float arm = centerRadius * 0.32f;
        Color color = theme.textMuted();
        renderer.strokeLine(new Point(centerX - arm, centerY - arm),
                new Point(centerX + arm, centerY + arm), 2, Paint.solid(color));
        renderer.strokeLine(new Point(centerX - arm, centerY + arm),
                new Point(centerX + arm, centerY - arm), 2, Paint.solid(color));
    }

    private void refreshPlacements() {
        if (items.isEmpty() || effectiveRadius <= 0) {
            return;
        }
        Font font = font();
        float inner = effectiveRadius * innerRadiusRatio;
        float outer = effectiveRadius;
        float sweep = FULL_TURN / items.size();
        float gap = items.size() > 1 ? Math.min(SECTOR_GAP, sweep * 0.2f) : 0;
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            float angle = START_OFFSET + index * sweep;
            PlacementKey key = new PlacementKey(font, item.label, inner, outer, sweep, angle,
                    iconSize, item.icon != null);
            if (item.placement == null || !key.equals(item.placementKey)) {
                item.placement = computePlacement(item, font, inner, outer, sweep, gap, angle);
                item.placementKey = key;
            }
        }
    }

    private Placement computePlacement(Item item, Font font, float inner, float outer,
                                       float sweep, float gap, float angle) {
        float alpha = (sweep - gap) / 2 - LABEL_ANGLE_MARGIN;
        if (alpha <= 0) {
            return Placement.hidden();
        }
        float iconRadius = 0;
        float iconEdge = 0;
        boolean iconVisible = false;
        if (item.icon != null) {
            iconRadius = inner + (outer - inner) * ICON_RADIAL_RATIO;
            float maxEdge = Math.min(iconSize, (outer - inner) * 0.55f);
            iconEdge = fitSquare(iconRadius, maxEdge, angle, alpha,
                    inner + ICON_INNER_MARGIN, outer - ICON_OUTER_MARGIN);
            iconVisible = iconEdge >= MIN_ICON_EDGE;
        }
        if (font == null || item.label.isEmpty()) {
            return new Placement(iconRadius, iconEdge, iconVisible, 0, null);
        }
        float height = font.lineHeight();
        if (height <= 0) {
            return new Placement(iconRadius, iconEdge, iconVisible, 0, null);
        }
        float textHeight = height + LABEL_GLYPH_PADDING * 2;
        Label label = fitLabel(font, item.label, inner, outer, angle, alpha, textHeight,
                iconVisible ? iconRadius : 0, iconVisible ? iconEdge : 0);
        if (label == null && iconVisible) {
            label = fitLabel(font, item.label, inner, outer, angle, alpha, textHeight, 0, 0);
            if (label != null) {
                // A readable label carries more information than the decoration next to it.
                iconVisible = false;
            }
        }
        if (label == null) {
            return new Placement(iconRadius, iconEdge, iconVisible, 0, null);
        }
        return new Placement(iconRadius, iconEdge, iconVisible, label.radius(), label.text());
    }

    private static Label fitLabel(Font font, String text, float inner, float outer, float angle,
                                  float alpha, float textHeight, float iconRadius, float iconEdge) {
        float labelMin = inner + LABEL_INNER_MARGIN + textHeight / 2;
        if (iconEdge > 0) {
            float iconExtent = iconRadius + (float) Math.hypot(iconEdge / 2, iconEdge / 2);
            labelMin = Math.max(labelMin, iconExtent + ICON_LABEL_GAP + textHeight / 2);
        }
        float labelMax = outer - LABEL_OUTER_MARGIN - textHeight / 2;
        if (labelMax < labelMin) {
            return null;
        }
        float bestWidth = 0;
        float bestRadius = 0;
        for (int step = 0; step < LABEL_RADIUS_CANDIDATES; step++) {
            float fraction = (float) step / (LABEL_RADIUS_CANDIDATES - 1);
            float candidate = labelMin + (labelMax - labelMin) * fraction;
            float width = fitRectWidth(candidate, angle, textHeight, alpha,
                    inner + LABEL_INNER_MARGIN, outer - LABEL_OUTER_MARGIN);
            if (width > bestWidth) {
                bestWidth = width;
                bestRadius = candidate;
            }
        }
        if (bestWidth <= 0) {
            return null;
        }
        float textWidth = bestWidth - LABEL_GLYPH_PADDING * 2;
        String fitted = textWidth <= 0 ? null : fitLabelText(font, text, textWidth);
        if (fitted == null) {
            return null;
        }
        return new Label(bestRadius, fitted);
    }

    private static float fitSquare(float radius, float maxEdge, float angle, float alpha,
                                   float inner, float outer) {
        if (maxEdge <= 0) {
            return 0;
        }
        float centerX = (float) Math.cos(angle) * radius;
        float centerY = (float) Math.sin(angle) * radius;
        if (rectFits(centerX, centerY, maxEdge, maxEdge, angle, alpha, inner, outer)) {
            return maxEdge;
        }
        float low = 0;
        float high = maxEdge;
        for (int step = 0; step < ICON_EDGE_STEPS; step++) {
            float middle = (low + high) / 2;
            if (rectFits(centerX, centerY, middle, middle, angle, alpha, inner, outer)) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static float fitRectWidth(float radius, float angle, float height, float alpha,
                                      float inner, float outer) {
        float centerX = (float) Math.cos(angle) * radius;
        float centerY = (float) Math.sin(angle) * radius;
        if (!rectFits(centerX, centerY, 0, height, angle, alpha, inner, outer)) {
            return 0;
        }
        float low = 0;
        float high = outer * 2;
        for (int step = 0; step < LABEL_WIDTH_STEPS; step++) {
            float middle = (low + high) / 2;
            if (rectFits(centerX, centerY, middle, height, angle, alpha, inner, outer)) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static boolean rectFits(float centerX, float centerY, float width, float height,
                                    float angle, float alpha, float inner, float outer) {
        float left = centerX - width / 2;
        float right = centerX + width / 2;
        float top = centerY - height / 2;
        float bottom = centerY + height / 2;
        if (!cornerFits(left, top, angle, alpha, inner, outer)
                || !cornerFits(right, top, angle, alpha, inner, outer)
                || !cornerFits(left, bottom, angle, alpha, inner, outer)
                || !cornerFits(right, bottom, angle, alpha, inner, outer)) {
            return false;
        }
        return inner <= 0 || minDistanceToRect(left, top, right, bottom) >= inner;
    }

    private static boolean cornerFits(float x, float y, float angle, float alpha,
                                      float inner, float outer) {
        double radius = Math.hypot(x, y);
        if (radius < inner || radius > outer) {
            return false;
        }
        double difference = Math.abs(normalizeSigned((float) Math.atan2(y, x) - angle));
        return difference <= alpha;
    }

    private static double minDistanceToRect(float left, float top, float right, float bottom) {
        if (left <= 0 && right >= 0 && top <= 0 && bottom >= 0) {
            return 0;
        }
        double distance = distanceToSegment(0, 0, left, top, right, top);
        distance = Math.min(distance, distanceToSegment(0, 0, right, top, right, bottom));
        distance = Math.min(distance, distanceToSegment(0, 0, right, bottom, left, bottom));
        distance = Math.min(distance, distanceToSegment(0, 0, left, bottom, left, top));
        return distance;
    }

    private static double distanceToSegment(double x, double y, double x1, double y1,
                                            double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0) {
            return Math.hypot(x - x1, y - y1);
        }
        double projection = ((x - x1) * dx + (y - y1) * dy) / lengthSquared;
        double clamped = Math.clamp(projection, 0.0, 1.0);
        return Math.hypot(x - (x1 + clamped * dx), y - (y1 + clamped * dy));
    }

    private static boolean fitsFullText(Font font, String text, float maxWidth) {
        if (text.length() <= FULL_TEXT_MEASURE_LIMIT) {
            return font.measure(text).width() <= maxWidth;
        }
        float width = 0;
        int index = 0;
        int previous = -1;
        while (index < text.length()) {
            int codepoint = text.codePointAt(index);
            width += font.advance(codepoint);
            if (previous >= 0) {
                width += font.kerning(previous, codepoint);
            }
            if (width > maxWidth) {
                return false;
            }
            previous = codepoint;
            index += Character.charCount(codepoint);
        }
        return font.measure(text).width() <= maxWidth;
    }

    static String fitLabelText(Font font, String text, float maxWidth) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(text, "text");
        if (text.isEmpty() || maxWidth <= 0) {
            return null;
        }
        if (fitsFullText(font, text, maxWidth)) {
            return text;
        }
        String ellipsis = font.glyph(0x2026) != null ? "\u2026" : "...";
        float ellipsisWidth = font.measure(ellipsis).width();
        if (ellipsisWidth > maxWidth) {
            return null;
        }
        float available = maxWidth - ellipsisWidth;
        int index = 0;
        int previous = -1;
        int codePoints = 0;
        float width = 0;
        while (index < text.length()) {
            int codepoint = text.codePointAt(index);
            float advance = font.advance(codepoint);
            if (previous >= 0) {
                advance += font.kerning(previous, codepoint);
            }
            if (width + advance > available) {
                break;
            }
            width += advance;
            previous = codepoint;
            codePoints++;
            index += Character.charCount(codepoint);
        }
        if (codePoints == 0) {
            return null;
        }
        int low = 0;
        int high = codePoints;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int end = text.offsetByCodePoints(0, middle);
            if (font.measure(text.substring(0, end) + ellipsis).width() <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        if (low == 0) {
            return null;
        }
        int end = text.offsetByCodePoints(0, low);
        return text.substring(0, end) + ellipsis;
    }


    private void drawRingSector(RenderManager renderer, Point center, float inner, float outer,
                                float startAngle, float sweep, Paint paint) {
        if (sweep <= ANIMATION_SNAP || outer <= inner || outer <= 0) {
            return;
        }
        if (sweep >= FULL_TURN - 0.0001f) {
            if (inner <= ANIMATION_SNAP) {
                renderer.fillCircle(center, outer, paint);
            } else {
                renderer.strokeCircle(center, (inner + outer) / 2, outer - inner, paint);
            }
            return;
        }
        if (inner <= ANIMATION_SNAP) {
            renderer.fillArc(center, outer, outer, startAngle, sweep, paint);
            return;
        }
        renderer.fillRingSector(center, inner, outer, startAngle, sweep, paint);
    }

    private int itemAt(float x, float y) {
        if (!open || items.isEmpty()) {
            return -1;
        }
        float scale = Math.clamp(openAmount, 0f, 1f);
        if (scale <= ANIMATION_SNAP) {
            return -1;
        }
        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.hypot(dx, dy);
        float outer = effectiveRadius * scale;
        float inner = outer * innerRadiusRatio;
        if (distance < inner) {
            return -1;
        }
        float sweep = FULL_TURN / items.size();
        float relative = normalize((float) Math.atan2(dy, dx)
                - (START_OFFSET - sweep / 2));
        int index = Math.min((int) (relative / sweep), items.size() - 1);
        float gap = items.size() > 1 ? Math.min(SECTOR_GAP, sweep * 0.2f) : 0;
        float within = relative - index * sweep;
        if (within < gap / 2 || within > sweep - gap / 2) {
            return -1;
        }
        float itemOuter = index == hoveredIndex ? outer + HOVER_LIFT : outer;
        return distance <= itemOuter ? index : -1;
    }

    private boolean centerAt(float x, float y) {
        if (!open) {
            return false;
        }
        float scale = Math.clamp(openAmount, 0f, 1f);
        if (scale <= ANIMATION_SNAP) {
            return false;
        }
        float centerRadius = effectiveRadius * scale * innerRadiusRatio * CENTER_CIRCLE_RATIO;
        if (centerRadius <= 0) {
            return false;
        }
        return Math.hypot(x - centerX, y - centerY) <= centerRadius;
    }

    private float sectorStart(int index, float sweep) {
        return START_OFFSET - sweep / 2 + index * sweep;
    }

    private void startOpenAnimation() {
        cancelAnimation();
        UiContext owner = context();
        if (owner == null || animationDuration <= 0) {
            openAmount = 1;
            return;
        }
        openAmount = 0;
        animationOwner = owner.animator();
        animation = animationOwner.animate(0, 1, animationDuration, animationEasing,
                value -> openAmount = Math.clamp(value, 0f, 1f));
        animation.setOnComplete(this::clearAnimation);
    }

    private void startCloseAnimation() {
        cancelAnimation();
        if (animationDuration <= 0 || openAmount <= ANIMATION_SNAP) {
            finishClose();
            return;
        }
        UiContext owner = context();
        if (owner == null) {
            finishClose();
            return;
        }
        animationOwner = owner.animator();
        animation = animationOwner.animate(openAmount, 0, animationDuration, animationEasing,
                value -> openAmount = Math.clamp(value, 0f, 1f));
        animation.setOnComplete(() -> closePending = true);
    }

    private void finishClose() {
        if (!open) {
            return;
        }
        open = false;
        closing = false;
        closePending = false;
        openAmount = 0;
        hoveredIndex = -1;
        centerHovered = false;
        clearSectorTooltip();
        cancelAnimation();
        UiContext owner = context();
        Widget focused = owner == null ? null : owner.focused();
        Widget restore = shouldRestoreFocus
                && (focused == null || focused == this) ? previousFocus : null;
        previousFocus = null;
        shouldRestoreFocus = true;
        if (owner != null) {
            try {
                owner.removeTopLevel(this);
            } catch (IllegalStateException ignored) {
                // The manager may already be closing and detach every top-level widget.
            }
        }
        applyFocusRestore(owner, restore);
        notifyClosed();
    }

    private void applyFocusRestore(UiContext owner, Widget restore) {
        if (owner == null || restore == null || restore.context() != owner || !restore.isFocusable()) {
            return;
        }
        try {
            owner.requestFocus(restore);
        } catch (RuntimeException ignored) {
            // The previous owner may have left the tree while the menu was open.
        }
    }

    private void cancelAnimation() {
        if (animation != null) {
            animationOwner.cancel(animation);
        }
        clearAnimation();
    }

    private void clearAnimation() {
        animation = null;
        animationOwner = null;
    }

    private void notifyClosed() {
        if (closeListener != null) {
            closeListener.run();
        }
    }

    private static float normalize(float angle) {
        float normalized = angle % FULL_TURN;
        return normalized < 0 ? normalized + FULL_TURN : normalized;
    }

    private static float normalizeSigned(float angle) {
        float normalized = angle % FULL_TURN;
        if (normalized > Math.PI) {
            normalized -= FULL_TURN;
        } else if (normalized < -Math.PI) {
            normalized += FULL_TURN;
        }
        return normalized;
    }

    private record PlacementKey(Font font, String text, float inner, float outer,
                                float sweep, float angle, float iconSize, boolean icon) {
    }

    private record Label(float radius, String text) {
    }

    private record Placement(float iconRadius, float iconEdge, boolean iconVisible,
                             float labelRadius, String labelText) {
        private static Placement hidden() {
            return new Placement(0, 0, false, 0, null);
        }
    }
}
