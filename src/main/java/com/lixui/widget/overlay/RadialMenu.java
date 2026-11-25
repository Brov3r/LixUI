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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A viewport-level radial menu that opens at a point and divides a ring into clickable sectors.
 * Every sector shows an optional icon and a label; the inner circle shows a title and acts as a
 * close button. Opening scales the ring up, closing scales it down, Escape dismisses the menu, and
 * a press outside the ring or center closes it. The menu requests keyboard focus while open and
 * restores the previous focus when it closes normally. The center and the whole ring are clamped
 * to the viewport, including the label extents.
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
    }

    private static final float DEFAULT_RADIUS = 140;
    private static final float DEFAULT_INNER_RADIUS_RATIO = 0.4f;
    private static final float DEFAULT_ICON_SIZE = 20;
    private static final float DEFAULT_ANIMATION_SECONDS = 0.22f;
    private static final float LABEL_GAP = 12;
    private static final float MAX_LABEL_WIDTH = 110;
    private static final float VIEWPORT_MARGIN = 6;
    private static final float SECTOR_GAP = 0.02f;
    private static final float HOVER_LIFT = 4;
    private static final float CENTER_CIRCLE_RATIO = 0.82f;
    private static final float FULL_TURN = (float) (Math.PI * 2);
    private static final float START_OFFSET = (float) (-Math.PI / 2);
    private static final float ANIMATION_SNAP = 0.001f;
    private static final int ARC_STEPS = 96;

    private final List<Item> items = new ArrayList<>();

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
    private float labelWidth;
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
        startCloseAnimation();
    }

    /**
     * Positions the menu center and shrinks the radius to keep the ring and labels in the viewport.
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
        Font font = font();
        float widestLabel = 0;
        if (font != null) {
            for (Item item : items) {
                widestLabel = Math.max(widestLabel, font.measure(item.label).width());
            }
        }
        labelWidth = font == null ? 0
                : Math.min(widestLabel, Math.min(MAX_LABEL_WIDTH, viewportWidth * 0.25f));
        float labelHeight = font == null ? 0 : font.lineHeight();
        float maxRadiusX = viewportWidth / 2 - VIEWPORT_MARGIN - LABEL_GAP - labelWidth / 2;
        float maxRadiusY = viewportHeight / 2 - VIEWPORT_MARGIN - LABEL_GAP - labelHeight / 2;
        effectiveRadius = Math.max(0, Math.min(radius, Math.min(maxRadiusX, maxRadiusY)));
        float extentX = effectiveRadius + LABEL_GAP + labelWidth / 2 + VIEWPORT_MARGIN;
        float extentY = effectiveRadius + LABEL_GAP + labelHeight / 2 + VIEWPORT_MARGIN;
        centerX = extentX * 2 >= viewportWidth
                ? viewportWidth / 2 : Math.clamp(requestedX, extentX, viewportWidth - extentX);
        centerY = extentY * 2 >= viewportHeight
                ? viewportHeight / 2 : Math.clamp(requestedY, extentY, viewportHeight - extentY);
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
        return false;
    }

    /**
     * Clears hover feedback when the pointer leaves the overlay.
     */
    @Override
    protected void onPointerExit() {
        hoveredIndex = -1;
        centerHovered = false;
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

    private void drawMenu(RenderManager renderer, Theme theme, Font font, float amount) {
        if (items.isEmpty()) {
            return;
        }
        float outer = effectiveRadius * amount;
        float inner = outer * innerRadiusRatio;
        float sweep = FULL_TURN / items.size();
        float gap = items.size() > 1 ? Math.min(SECTOR_GAP, sweep * 0.2f) : 0;
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
                        sweep, gap);
            }
        }
        float centerRadius = inner * CENTER_CIRCLE_RATIO;
        if (centerRadius > 0) {
            drawCenter(renderer, theme, font, centerRadius);
        }
    }

    private void drawItem(RenderManager renderer, Theme theme, Font font, float amount,
                          int index, boolean hovered, float inner, float outer, float sweep,
                          float gap) {
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
        float midRadius = (inner + itemOuter) / 2;
        float angle = start + itemSweep / 2;
        if (item.icon != null) {
            float edge = Math.min(iconSize * amount, (itemOuter - inner) * 0.6f);
            if (edge > 0.5f) {
                Color tint = item.enabled ? theme.text() : theme.textMuted().withAlpha(0.45f);
                renderer.icon(item.icon, new Rect(centerX + (float) Math.cos(angle) * midRadius
                                - edge / 2, centerY + (float) Math.sin(angle) * midRadius - edge / 2,
                        edge, edge), ImageStyle.ICON, tint);
            }
        }
        drawLabel(renderer, theme, font, amount, item, angle, itemOuter);
    }

    private void drawLabel(RenderManager renderer, Theme theme, Font font, float amount,
                           Item item, float angle, float outer) {
        if (font == null || labelWidth <= 0 || item.label.isEmpty()) {
            return;
        }
        float labelRadius = outer + LABEL_GAP * amount;
        float x = centerX + (float) Math.cos(angle) * labelRadius;
        float y = centerY + (float) Math.sin(angle) * labelRadius;
        Color color = item.enabled ? theme.text() : theme.textMuted().withAlpha(0.45f);
        TextStyle style = new TextStyle(labelWidth, 0, 1, TextOverflow.ELLIPSIS,
                HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        renderer.text(font, item.label, x, y, color, style);
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
        int steps = Math.max(2, (int) Math.ceil(sweep / FULL_TURN * ARC_STEPS));
        List<Point> points = new ArrayList<>((steps + 1) * 2);
        for (int step = 0; step <= steps; step++) {
            float angle = startAngle + sweep * step / steps;
            points.add(pointAt(center, outer, angle));
        }
        for (int step = steps; step >= 0; step--) {
            float angle = startAngle + sweep * step / steps;
            points.add(pointAt(center, inner, angle));
        }
        renderer.fillPolygon(points, paint);
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

    private static Point pointAt(Point center, float radius, float angle) {
        return new Point(center.x() + (float) Math.cos(angle) * radius,
                center.y() + (float) Math.sin(angle) * radius);
    }

    private static float normalize(float angle) {
        float normalized = angle % FULL_TURN;
        return normalized < 0 ? normalized + FULL_TURN : normalized;
    }
}
