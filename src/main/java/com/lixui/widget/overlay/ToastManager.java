package com.lixui.widget.overlay;

import com.lixui.geometry.Rect;
import com.lixui.geometry.Size;
import com.lixui.graphics.Color;
import com.lixui.render.Texture;
import com.lixui.text.TextLayout;
import com.lixui.widget.FrameInfo;
import com.lixui.widget.LayoutConstraints;
import com.lixui.widget.UiContext;
import com.lixui.widget.Widget;
import com.lixui.widget.image.ImageSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A transparent overlay widget that stacks transient {@link com.lixui.widget.overlay.Toast}
 * cards in a corner of its bounds.
 * The no-argument constructor creates a detached overlay. Attach it through the normal widget API,
 * for example {@code parent.add(toasts)} or {@code manager.add(toasts)}; it then uses the bounds
 * assigned by its parent and never needs the owning manager or context. The context constructor is
 * kept for source and binary compatibility and attaches the overlay as a top-level widget.
 * Toasts appear with a short slide and fade, pause their countdown while hovered and show a
 * progress bar with the remaining time. The visible count is limited; extra notifications wait in
 * a queue and appear as earlier cards expire. Empty layer space passes pointer input through, so
 * the overlay never blocks the UI outside an actual toast.
 */
public final class ToastManager extends Widget implements AutoCloseable {
    /**
     * A viewport corner used as the toast stack origin.
     */
    public enum Corner {
        /** Top-left corner. */
        TOP_LEFT,
        /** Top-right corner. */
        TOP_RIGHT,
        /** Bottom-left corner. */
        BOTTOM_LEFT,
        /** Bottom-right corner. */
        BOTTOM_RIGHT
    }

    /**
     * Legacy semantic notification type.
     * This adapter preserves the nested API and maps to the standalone toast type.
     */
    public enum Type {
        /** Neutral informational message. */
        INFO("info.svg"),
        /** Successful operation or confirmation. */
        SUCCESS("tick.svg"),
        /** Warning that needs attention. */
        WARNING("exclamation.svg"),
        /** Error or failed operation. */
        ERROR("stop.svg");

        private final String iconName;

        Type(String iconName) {
            this.iconName = iconName;
        }

        /**
         * Returns the bundled icon name used by this type.
         *
         * @return the icon resource name
         */
        public String iconName() {
            return iconName;
        }

        com.lixui.widget.overlay.Toast.Type toToastType() {
            return com.lixui.widget.overlay.Toast.Type.valueOf(name());
        }

        static Type from(com.lixui.widget.overlay.Toast.Type type) {
            return valueOf(type.name());
        }
    }

    /**
     * Legacy presentation options for a single toast.
     * The adapter delegates storage and validation to
     * {@link com.lixui.widget.overlay.Toast.Options} while preserving the original fluent API.
     */
    public static final class Options {
        private final com.lixui.widget.overlay.Toast.Options delegate;

        /**
         * Creates options for a notification message.
         *
         * @param message notification text
         * @throws NullPointerException if {@code message} is null
         */
        public Options(String message) {
            delegate = new com.lixui.widget.overlay.Toast.Options(message);
        }

        /**
         * Returns the notification text.
         *
         * @return the message
         */
        public String message() {
            return delegate.message();
        }

        /**
         * Returns the legacy semantic type.
         *
         * @return the type
         */
        public Type type() {
            return Type.from(delegate.type());
        }

        /**
         * Assigns the semantic type that selects the default color and icon.
         *
         * @param type semantic type
         * @return this options object
         * @throws NullPointerException if {@code type} is null
         */
        public Options type(Type type) {
            delegate.type(Objects.requireNonNull(type, "type").toToastType());
            return this;
        }

        /**
         * Returns the title text.
         *
         * @return the title, or an empty string when no title is set
         */
        public String title() {
            return delegate.title();
        }

        /**
         * Assigns a title drawn on its own line above the message.
         *
         * @param title title text
         * @return this options object
         * @throws NullPointerException if {@code title} is null
         */
        public Options title(String title) {
            delegate.title(title);
            return this;
        }

        /**
         * Returns the explicit icon source.
         *
         * @return the explicit source, or null when the type icon is used
         */
        public ImageSource icon() {
            return delegate.icon();
        }

        /**
         * Assigns an explicit icon source.
         *
         * @param icon explicit image source, or null to use the type icon
         * @return this options object
         */
        public Options icon(ImageSource icon) {
            delegate.icon(icon);
            return this;
        }

        /**
         * Assigns a caller-owned texture as the icon.
         *
         * @param texture icon texture, or null to use the type icon
         * @return this options object
         */
        public Options icon(Texture texture) {
            delegate.icon(texture);
            return this;
        }

        /**
         * Returns the explicit accent color.
         *
         * @return the accent, or null when the type color is used
         */
        public Color accent() {
            return delegate.accent();
        }

        /**
         * Assigns an explicit accent color.
         *
         * @param accent straight-alpha accent color, or null to use the type color
         * @return this options object
         */
        public Options accent(Color accent) {
            delegate.accent(accent);
            return this;
        }

        /**
         * Returns the maximum number of message lines.
         *
         * @return the line limit, or zero when the message is not line-limited
         */
        public int maxLines() {
            return delegate.maxLines();
        }

        /**
         * Assigns the maximum number of visible message lines.
         *
         * @param maxLines maximum line count, or zero for no limit
         * @return this options object
         * @throws IllegalArgumentException if the count is negative
         */
        public Options maxLines(int maxLines) {
            delegate.maxLines(maxLines);
            return this;
        }

        /**
         * Returns the maximum message text width.
         *
         * @return the wrap width in logical UI units
         */
        public float maxWidth() {
            return delegate.maxWidth();
        }

        /**
         * Assigns the maximum message text width before lines wrap.
         *
         * @param maxWidth positive wrap width in logical UI units
         * @return this options object
         * @throws IllegalArgumentException if the width is not finite or not positive
         */
        public Options maxWidth(float maxWidth) {
            delegate.maxWidth(maxWidth);
            return this;
        }

        /**
         * Returns the minimum card width.
         *
         * @return the lower bound in logical UI units
         */
        public float minCardWidth() {
            return delegate.minCardWidth();
        }

        /**
         * Assigns the minimum card width.
         *
         * @param minCardWidth non-negative width in logical UI units
         * @return this options object
         * @throws IllegalArgumentException if the width is not finite or negative
         */
        public Options minCardWidth(float minCardWidth) {
            delegate.minCardWidth(minCardWidth);
            return this;
        }

        /**
         * Returns the maximum card width.
         *
         * @return the upper bound in logical UI units
         */
        public float maxCardWidth() {
            return delegate.maxCardWidth();
        }

        /**
         * Assigns the maximum card width.
         *
         * @param maxCardWidth positive width in logical UI units
         * @return this options object
         * @throws IllegalArgumentException if the width is not finite or not positive
         */
        public Options maxCardWidth(float maxCardWidth) {
            delegate.maxCardWidth(maxCardWidth);
            return this;
        }

        /**
         * Reports whether the remaining-time bar is shown.
         *
         * @return true when the timer indicator is drawn
         */
        public boolean showTimer() {
            return delegate.showTimer();
        }

        /**
         * Shows or hides the remaining-time bar.
         *
         * @param showTimer true to draw the remaining-time bar
         * @return this options object
         */
        public Options showTimer(boolean showTimer) {
            delegate.showTimer(showTimer);
            return this;
        }

        /**
         * Returns the explicit lifetime.
         *
         * @return the lifetime in seconds, or zero when the manager default is used
         */
        public float duration() {
            return delegate.duration();
        }

        /**
         * Assigns an explicit lifetime.
         *
         * @param duration positive lifetime in seconds
         * @return this options object
         * @throws IllegalArgumentException if the duration is not finite or not positive
         */
        public Options duration(float duration) {
            delegate.duration(duration);
            return this;
        }

        /**
         * Returns the custom content widget.
         *
         * @return the custom content, or null when the default body is used
         */
        public Widget content() {
            return delegate.content();
        }

        /**
         * Replaces the default body with a custom widget.
         *
         * @param content content widget without a parent, or null to restore the default body
         * @return this options object
         */
        public Options content(Widget content) {
            delegate.content(content);
            return this;
        }

        /**
         * Returns the custom card renderer.
         *
         * @return the renderer, or null when the default card is drawn
         */
        public com.lixui.widget.overlay.Toast.Renderer renderer() {
            return delegate.renderer();
        }

        /**
         * Replaces the default card visuals with custom drawing.
         *
         * @param renderer custom card renderer, or null to restore the default card
         * @return this options object
         */
        public Options renderer(com.lixui.widget.overlay.Toast.Renderer renderer) {
            delegate.renderer(renderer);
            return this;
        }

        com.lixui.widget.overlay.Toast.Options toToastOptions() {
            return delegate;
        }
    }

    private static final float MARGIN = 16;
    private static final float GAP = 8;
    private static final float SLIDE_DISTANCE = 24;
    private static final float DEFAULT_DURATION = 4;

    private final List<Toast> visible = new ArrayList<>();
    private final ArrayDeque<Toast> pending = new ArrayDeque<>();
    private Corner corner = Corner.TOP_RIGHT;
    private int maxVisible = 3;
    private float defaultDuration = DEFAULT_DURATION;
    private boolean closed;

    /**
     * Creates a detached toast overlay.
     * Attach the overlay to any widget tree through
     * {@link Widget#add(Widget)} or {@link com.lixui.widget.WidgetManager#add(Widget)}.
     * The parent assigns the overlay bounds.
     */
    public ToastManager() {
        setFillViewport(true);
        setAlwaysOnTop(true);
        setClipToBounds(true);
    }

    /**
     * Creates a toast overlay and attaches it as a top-level widget of the supplied context.
     * This constructor is kept for compatibility; new code can use {@link #ToastManager()} and
     * attach the overlay through the normal widget API.
     *
     * @param context context that owns the overlay
     * @throws NullPointerException if {@code context} is null
     * @throws IllegalStateException if the context is closed or has no manager
     */
    public ToastManager(UiContext context) {
        this();
        Objects.requireNonNull(context, "context").addTopLevel(this);
    }

    /**
     * Returns the stack corner.
     *
     * @return the current corner
     */
    public Corner corner() {
        return corner;
    }

    /**
     * Moves the toast stack to a corner of the overlay bounds.
     *
     * @param corner stack corner
     * @throws NullPointerException if {@code corner} is null
     */
    public void setCorner(Corner corner) {
        this.corner = Objects.requireNonNull(corner, "corner");
    }

    /**
     * Returns the maximum number of simultaneously visible toasts.
     *
     * @return the visible limit
     */
    public int maxVisible() {
        return maxVisible;
    }

    /**
     * Sets the maximum number of simultaneously visible toasts.
     * Lowering the limit immediately removes the newest excess toasts.
     *
     * @param maxVisible positive visible limit
     * @throws IllegalArgumentException if the limit is not positive
     * @throws IllegalStateException if the overlay is closed
     */
    public void setMaxVisible(int maxVisible) {
        ensureOpen();
        if (maxVisible <= 0) {
            throw new IllegalArgumentException("Visible toast limit must be positive.");
        }
        this.maxVisible = maxVisible;
        while (visible.size() > maxVisible) {
            Toast toast = visible.remove(visible.size() - 1);
            remove(toast.widget());
            toast.widget().abort();
        }
        promote();
    }

    /**
     * Returns the duration used when options do not supply a lifetime.
     *
     * @return the default duration in seconds
     */
    public float defaultDuration() {
        return defaultDuration;
    }

    /**
     * Sets the duration used when options do not supply a lifetime.
     *
     * @param defaultDuration positive duration in seconds
     * @throws IllegalArgumentException if the duration is not finite or not positive
     * @throws IllegalStateException if the overlay is closed
     */
    public void setDefaultDuration(float defaultDuration) {
        ensureOpen();
        if (!Float.isFinite(defaultDuration) || defaultDuration <= 0) {
            throw new IllegalArgumentException("Default toast duration must be finite and positive.");
        }
        this.defaultDuration = defaultDuration;
    }

    /**
     * Shows a toast with the default duration.
     *
     * @param message notification text
     * @return the shown or queued toast handle
     * @throws NullPointerException if {@code message} is null
     * @throws IllegalStateException if the overlay is closed
     */
    public Toast show(String message) {
        return show(new Options(message));
    }

    /**
     * Shows a toast with an explicit duration.
     * When the visible limit is reached the toast waits in the queue.
     *
     * @param message notification text
     * @param duration positive lifetime in seconds
     * @return the shown or queued toast handle
     * @throws NullPointerException if {@code message} is null
     * @throws IllegalArgumentException if the duration is not finite or not positive
     * @throws IllegalStateException if the overlay is closed
     */
    public Toast show(String message, float duration) {
        return show(new Options(message).duration(duration));
    }

    /**
     * Shows a toast with explicit presentation options.
     * When the visible limit is reached the toast waits in the queue. A zero option duration uses
     * {@link #defaultDuration()}.
     *
     * @param options toast presentation options
     * @return the shown or queued toast handle
     * @throws NullPointerException if {@code options} is null
     * @throws IllegalArgumentException if the options are inconsistent or custom content already
     *                                  has a parent
     * @throws IllegalStateException if the overlay is closed
     */
    public Toast show(Options options) {
        ensureOpen();
        Objects.requireNonNull(options, "options");
        com.lixui.widget.overlay.Toast.Options toastOptions = options.toToastOptions();
        float duration = toastOptions.duration() > 0 ? toastOptions.duration() : defaultDuration;
        com.lixui.widget.overlay.Toast view = new com.lixui.widget.overlay.Toast(toastOptions, duration);
        Toast toast = new Toast(view);
        view.setDismissAction(() -> dismiss(toast));
        view.setDismissListener(() -> removeVisible(toast));
        if (visible.size() < maxVisible) {
            showToast(toast);
        } else {
            pending.addLast(toast);
        }
        return toast;
    }

    /**
     * Returns the number of visible toasts.
     *
     * @return the visible count
     */
    public int visibleCount() {
        return visible.size();
    }

    /**
     * Returns the number of queued toasts.
     *
     * @return the queued count
     */
    public int queuedCount() {
        return pending.size();
    }

    /**
     * Returns the visible toast handles in display order.
     *
     * @return an immutable snapshot of visible toast handles
     */
    public List<Toast> visibleToasts() {
        return List.copyOf(visible);
    }

    /**
     * Dismisses every queued toast and starts the exit animation for visible ones.
     *
     * @throws IllegalStateException if the overlay is closed
     */
    public void dismissAll() {
        ensureOpen();
        for (Toast toast : pending) {
            toast.widget().abort();
        }
        pending.clear();
        for (Toast toast : List.copyOf(visible)) {
            toast.widget().startExit();
        }
    }

    /**
     * Removes the overlay from its parent or top-level manager and stops every toast.
     * Closing an already closed overlay does nothing.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        abortAll();
        Widget parent = parent();
        if (parent != null) {
            parent.remove(this);
            return;
        }
        UiContext owner = context();
        if (owner != null) {
            try {
                owner.removeTopLevel(this);
            } catch (IllegalStateException ignored) {
                // The owner may already be closing and detach every top-level widget.
            }
        }
    }

    /**
     * Removes finished toasts at the start of a frame.
     *
     * @param frame timing information for the current frame
     */
    @Override
    protected void onUpdate(FrameInfo frame) {
        for (Toast toast : List.copyOf(visible)) {
            if (toast.widget().isFinished()) {
                removeVisible(toast);
            }
        }
    }

    /**
     * Stacks visible toasts in the configured corner.
     */
    @Override
    protected void onLayout() {
        layoutToasts();
    }

    /**
     * Stops every toast when the overlay leaves its widget tree.
     */
    @Override
    protected void onDetached() {
        closed = true;
        abortAll();
    }

    /**
     * Passes input through empty layer space while keeping toast cards hoverable.
     *
     * @param x horizontal coordinate relative to the overlay
     * @param y vertical coordinate relative to the overlay
     * @return the toast under the point, or null when the point is outside every toast
     */
    @Override
    public Widget hitTest(float x, float y) {
        if (!contains(x, y)) {
            return null;
        }
        for (int index = children().size() - 1; index >= 0; index--) {
            Widget child = children().get(index);
            if (!child.isVisible()) {
                continue;
            }
            Widget hit = child.hitTest(x - child.bounds().x(), y - child.bounds().y());
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void showToast(Toast toast) {
        visible.add(toast);
        add(toast.widget());
    }

    private void removeVisible(Toast toast) {
        if (!visible.remove(toast)) {
            return;
        }
        remove(toast.widget());
        promote();
    }

    private void promote() {
        while (visible.size() < maxVisible && !pending.isEmpty()) {
            showToast(pending.pollFirst());
        }
    }

    private void dismiss(Toast toast) {
        if (pending.remove(toast)) {
            toast.widget().abort();
            return;
        }
        if (visible.contains(toast)) {
            toast.widget().startExit();
        }
    }

    private void abortAll() {
        for (Toast toast : pending) {
            toast.widget().abort();
        }
        pending.clear();
        for (Toast toast : visible) {
            toast.widget().abort();
        }
        visible.clear();
    }

    private void layoutToasts() {
        float layerWidth = bounds().width();
        float layerHeight = bounds().height();
        if (layerWidth <= 0 || layerHeight <= 0) {
            return;
        }
        float maximumWidth = Math.max(1, layerWidth - MARGIN * 2);
        float maximumHeight = Math.max(1, layerHeight - MARGIN * 2);
        float top = MARGIN;
        float bottom = layerHeight - MARGIN;
        for (Toast toast : visible) {
            com.lixui.widget.overlay.Toast view = toast.widget();
            Size size = view.measure(LayoutConstraints.loose(maximumWidth, maximumHeight));
            float slide = view.slideProgress() * SLIDE_DISTANCE;
            float x = switch (corner) {
                case TOP_LEFT, BOTTOM_LEFT -> MARGIN - slide;
                case TOP_RIGHT, BOTTOM_RIGHT ->
                        layerWidth - MARGIN - size.width() + slide;
            };
            float y;
            switch (corner) {
                case TOP_LEFT, TOP_RIGHT -> {
                    y = top;
                    top += size.height() + GAP;
                }
                case BOTTOM_LEFT, BOTTOM_RIGHT -> {
                    bottom -= size.height();
                    y = bottom;
                    bottom -= GAP;
                }
                default -> throw new IllegalStateException("Unknown toast corner.");
            }
            float clampedX = Math.clamp(x, 0, Math.max(0, layerWidth - size.width()));
            float clampedY = Math.clamp(y, 0, Math.max(0, layerHeight - size.height()));
            view.layout(new Rect(clampedX, clampedY, size.width(), size.height()));
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Toast manager is closed.");
        }
    }

    /**
     * A legacy handle for a queued or visible toast.
     * The handle delegates to the standalone toast widget while preserving the original nested API.
     */
    public static final class Toast {
        private final com.lixui.widget.overlay.Toast view;

        private Toast(com.lixui.widget.overlay.Toast view) {
            this.view = view;
        }

        /**
         * Returns the notification text.
         *
         * @return the message
         */
        public String message() {
            return view.message();
        }

        /**
         * Returns the legacy semantic type.
         *
         * @return the type
         */
        public Type type() {
            return Type.from(view.type());
        }

        /**
         * Returns the title text.
         *
         * @return the title, or an empty string when the toast has no title
         */
        public String title() {
            return view.title();
        }

        /**
         * Returns the effective icon source.
         *
         * @return the explicit icon source or the default type icon
         */
        public ImageSource icon() {
            return view.icon();
        }

        /**
         * Returns the explicit accent color.
         *
         * @return the accent, or null when the toast uses the type color
         */
        public Color accent() {
            return view.accent();
        }

        /**
         * Returns the maximum number of message lines.
         *
         * @return the line limit, or zero when the message is not line-limited
         */
        public int maxLines() {
            return view.maxLines();
        }

        /**
         * Returns the maximum message text width.
         *
         * @return the wrap width in logical UI units
         */
        public float maxWidth() {
            return view.maxWidth();
        }

        /**
         * Returns the minimum card width.
         *
         * @return the lower bound in logical UI units
         */
        public float minCardWidth() {
            return view.minCardWidth();
        }

        /**
         * Returns the maximum card width.
         *
         * @return the upper bound in logical UI units
         */
        public float maxCardWidth() {
            return view.maxCardWidth();
        }

        /**
         * Reports whether the remaining-time bar is drawn.
         *
         * @return true when the timer indicator is shown
         */
        public boolean showTimer() {
            return view.showTimer();
        }

        /**
         * Returns the remaining auto-hide time.
         *
         * @return seconds until the toast starts hiding
         */
        public float remainingSeconds() {
            return view.remainingSeconds();
        }

        /**
         * Reports whether the toast is currently attached to the screen.
         *
         * @return true when the card is visible
         */
        public boolean isVisible() {
            return view.isVisible();
        }

        /**
         * Returns the toast bounds in viewport coordinates.
         *
         * @return the current bounds translated to the viewport origin
         */
        public Rect bounds() {
            return view.viewportBounds();
        }

        /**
         * Dismisses the toast, removing it from the queue or starting its exit animation.
         */
        public void dismiss() {
            view.dismiss();
        }

        /**
         * Returns the standalone toast widget behind this handle.
         *
         * @return the toast widget
         */
        public com.lixui.widget.overlay.Toast widget() {
            return view;
        }

        TextLayout messageLayout() {
            return view.messageLayout();
        }
    }
}
